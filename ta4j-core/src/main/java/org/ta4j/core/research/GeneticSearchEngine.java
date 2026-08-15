/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Genetic algorithm search over the declared discrete domain space.
 *
 * <p>
 * Genomes are one canonical-value index per dimension. The initial population
 * is sampled distinctly — falling back to deterministic enumeration of the
 * remaining space when random draws collide — and every breeding batch prefers
 * unseen genomes:
 * 
 * children are re-sampled until they are canonical candidates not yet proposed
 * in this run, so each batch yields at least one new evaluation until the
 * search space is covered. When no unseen genome can be produced, the engine
 * pads the population with elites (already evaluated, served from the cache at
 * no budget charge) and the pipeline terminates with
 * {@code SEARCH_SPACE_EXHAUSTED} once the whole space has been proposed.
 * </p>
 *
 * <p>
 * Each generation is finalized at the next proposal request: the best valid
 * evaluation of the generation is compared against the best-ever evaluation by
 * primary score only — normalizer tie-breakers never reset the streak — to
 * derive the no-improvement streak, the iteration counter advances, and the
 * next generation is bred from the current generation's valid evaluations via
 * elitism, tournament selection, uniform per-dimension crossover, and
 * per-dimension mutation. All randomness comes from the run-local seeded
 * {@link Random}, so identical configuration reproduces identical proposals.
 * </p>
 */
final class GeneticSearchEngine extends SearchEngine {

    private static final int UNSEEN_CHILD_ATTEMPTS = 1000;
    private final ParameterResearch.Direction direction;

    private final ParameterResearch.GeneticSettings settings;
    private final Random random;
    private final Comparator<ParameterResearch.EvaluatedCandidate> ranking;
    private final int maxIterations;
    private final int noImprovementIterations;
    private final Map<String, int[]> genomesById = new HashMap<>();

    private boolean initialized;
    private long sweepCursor;

    private List<int[]> population;
    private List<GenomeEvaluation> generationEvaluations = new ArrayList<>();
    private ParameterResearch.EvaluatedCandidate bestEver;
    private int noImprovementStreak;
    private boolean generationPending;

    GeneticSearchEngine(List<DomainSpec> specs, ParameterResearch.GeneticSettings settings, Random random,
            Comparator<ParameterResearch.EvaluatedCandidate> ranking, ParameterResearch.Direction direction,
            int maxIterations, int noImprovementIterations) {
        super(specs);
        this.direction = direction;
        this.settings = settings;

        this.random = random;
        this.ranking = ranking;
        this.maxIterations = maxIterations;
        this.noImprovementIterations = noImprovementIterations;
    }

    List<ParameterResearch.ParameterSet> propose(int maxNew) {
        if (!initialized) {
            initialized = true;
            int size = Math.min(settings.populationSize(), maxNew);
            population = sampleDistinct(size);
            return proposalBatch(population);
        }
        // Finalize the pending generation before any limit check so a
        // rejection-only generation is counted exactly once even when a limit
        // fires on this request; the limit guards breeding new generations.
        List<GenomeEvaluation> valid = finalizeGeneration();
        if (terminationReason() != null) {
            return List.of();
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        if (maxNew <= 0) {
            // No capacity was granted: finalization above still runs so
            // rejection-only generations are counted exactly once, but a
            // zero-capacity request must not breed a zero-sized population
            // and thereby shrink the search to nothing.
            return List.of();
        }
        int count = Math.min(settings.populationSize(), maxNew);
        population = breed(count, valid);
        return proposalBatch(population);
    }

    @Override
    void observe(String rawId, ParameterResearch.EvaluatedCandidate evaluated) {
        int[] genome = genomesById.get(rawId);
        if (genome != null) {
            generationEvaluations.add(new GenomeEvaluation(genome, evaluated));
        }
    }

    @Override
    void finalizeObserved() {
        if (generationPending) {
            finalizeGeneration();
        }
    }

    private List<GenomeEvaluation> finalizeGeneration() {
        if (!generationPending) {
            return List.of();
        }
        completeIteration();
        List<GenomeEvaluation> valid = generationEvaluations.stream()
                .filter(g -> g.evaluated().valid())
                .sorted((a, b) -> ranking.compare(a.evaluated(), b.evaluated()))
                .toList();
        // An all-invalid generation carries no ranking evidence: the best
        // valid score neither improved nor declined, so the stagnation streak
        // stays put and NO_IMPROVEMENT cannot fire while unseen genomes
        // remain. The streak only ever mutates for generations that produced
        // a valid evaluation.
        if (!valid.isEmpty()) {
            ParameterResearch.EvaluatedCandidate generationBest = valid.get(0).evaluated();
            if (bestEver == null
                    || ParameterResearch.scoreIsBetter(direction, generationBest.score(), bestEver.score())) {
                bestEver = generationBest;
                noImprovementStreak = 0;
            } else {
                noImprovementStreak++;
            }
            if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
            }
        }
        generationEvaluations = new ArrayList<>();
        generationPending = false;
        return valid;
    }

    private List<int[]> breed(int count, List<GenomeEvaluation> valid) {
        List<int[]> next = new ArrayList<>(count);
        Set<String> batchIds = new HashSet<>();
        // Reserve the configured elite slots first: elites are already evaluated,
        // so the pipeline serves them from the cache without spending budget.
        // Always leave at least one slot for a child so the final budget point is
        // never wasted on an elite.
        int eliteCount = Math.min(settings.elitismCount(), Math.min(Math.max(0, count - 1), valid.size()));
        for (int i = 0; i < eliteCount; i++) {
            int[] elite = valid.get(i).genome().clone();
            next.add(elite);
            batchIds.add(canonicalId(elite));
        }
        int childrenFound = 0;
        for (int i = 0; i < count - eliteCount; i++) {
            int[] child = unseenChild(valid, batchIds);
            if (child == null) {
                // Breeding cannot reach an unseen genome with these settings
                // (for example mutationRate 0 with a converged population):
                // fall back to random exploration so the search keeps consuming
                // its budget until the declared space is covered or a configured
                // limit fires, instead of stopping with an NO_IMPROVEMENT streak
                // that was never configured.
                child = unseenGenome(batchIds);
                if (child == null) {
                    // The deterministic sweep just visited every declared cell
                    // without finding an unproposed one: no later slot in this
                    // batch can succeed, so stop instead of re-running the
                    // whole sweep for every remaining slot.
                    break;
                }
            }
            next.add(child);
            childrenFound++;
        }
        if (childrenFound == 0 && count > 0) {
            // No unseen genome was bred and no unseen cell remained for random
            // exploration: the declared space is effectively covered.
            if (exhausted()) {
                terminate(ParameterResearch.TerminationReason.SEARCH_SPACE_EXHAUSTED);
            } else {
                // Unreachable in practice: the deterministic sweep in
                // unseenGenome only gives up once every cell is proposed.
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
            }
            return List.of();
        }
        return next;
    }

    private int[] unseenGenome(Set<String> batchIds) {
        for (int attempt = 0; attempt < UNSEEN_CHILD_ATTEMPTS; attempt++) {
            int[] candidate = randomGenome();
            String id = canonicalId(candidate);
            if (!proposed(id) && batchIds.add(id)) {
                return candidate;
            }
        }
        // When the space is nearly covered, random draws can collide
        // repeatedly: sweep the remaining space deterministically so
        // exploration stops only when every declared cell has been proposed.
        // The cursor is retained across calls so a breeding batch does not
        // rescan already-visited cells from index 0 for every child.
        long space = totalSpace();
        if (space <= Integer.MAX_VALUE) {
            for (long step = 0; step < space; step++) {
                int[] candidate = genomeAt(sweepCursor);
                sweepCursor = (sweepCursor + 1L) % space;
                String id = canonicalId(candidate);
                if (!proposed(id) && batchIds.add(id)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private int[] unseenChild(List<GenomeEvaluation> valid, Set<String> batchIds) {
        for (int attempt = 0; attempt < UNSEEN_CHILD_ATTEMPTS; attempt++) {
            int[] child;
            if (valid.isEmpty()) {
                child = randomGenome();
            } else {
                int[] parentA = tournament(valid).genome();
                int[] parentB = tournament(valid).genome();
                child = new int[parentA.length];
                for (int d = 0; d < child.length; d++) {
                    // crossoverRate is the probability that a dimension is
                    // recombined: crossed dimensions draw their allele from
                    // either parent with equal probability, while uncrossed
                    // dimensions inherit the first parent's allele.
                    child[d] = random.nextDouble() < settings.crossoverRate()
                            ? (random.nextBoolean() ? parentA[d] : parentB[d])
                            : parentA[d];
                }
                for (int d = 0; d < child.length; d++) {
                    if (random.nextDouble() < settings.mutationRate()) {
                        child[d] = random.nextInt(specs().get(d).cardinality());
                    }
                }
            }
            String id = canonicalId(child);
            if (!proposed(id) && batchIds.add(id)) {
                return child;
            }
        }
        return null;
    }

    private GenomeEvaluation tournament(List<GenomeEvaluation> valid) {
        GenomeEvaluation best = null;
        for (int i = 0; i < settings.tournamentSize(); i++) {
            GenomeEvaluation pick = valid.get(random.nextInt(valid.size()));
            if (best == null || ranking.compare(pick.evaluated(), best.evaluated()) < 0) {
                best = pick;
            }
        }
        return best;
    }

    private List<int[]> sampleDistinct(int target) {
        List<int[]> genomes = new ArrayList<>(target);
        if (target <= 0) {
            return genomes;
        }
        Set<String> seen = new HashSet<>();
        long attempts = Math.min((long) target * 100L, totalSpace());
        for (long attempt = 0; attempt < attempts && genomes.size() < target; attempt++) {
            int[] genome = randomGenome();
            if (seen.add(Arrays.toString(genome))) {
                genomes.add(genome);
            }
        }
        // When the declared space is close to the requested population size,
        // random draws can collide repeatedly. Enumerate the remaining space
        // deterministically so the population reaches its documented size while
        // unseen genomes still exist.
        if (genomes.size() < target && totalSpace() <= Integer.MAX_VALUE) {
            for (long index = 0; index < totalSpace() && genomes.size() < target; index++) {
                int[] genome = genomeAt(index);
                if (seen.add(Arrays.toString(genome))) {
                    genomes.add(genome);
                }
            }
        }
        return genomes;
    }

    private int[] randomGenome() {
        int[] genome = new int[specs().size()];
        for (int d = 0; d < genome.length; d++) {
            genome[d] = random.nextInt(specs().get(d).cardinality());
        }
        return genome;
    }

    private int[] genomeAt(long index) {
        int[] genome = new int[specs().size()];
        long remaining = index;
        for (int dimension = genome.length - 1; dimension >= 0; dimension--) {
            int cardinality = specs().get(dimension).cardinality();
            genome[dimension] = (int) (remaining % cardinality);
            remaining /= cardinality;
        }
        return genome;
    }

    private List<ParameterResearch.ParameterSet> proposalBatch(List<int[]> genomes) {
        // The pipeline evaluates and observes one batch before requesting the
        // next: raw-id bindings from older batches can never be looked up
        // again, so retain only the in-flight batch.
        genomesById.clear();
        List<ParameterResearch.ParameterSet> batch = new ArrayList<>(genomes.size());
        for (int[] genome : genomes) {
            ParameterResearch.ParameterSet set = parameterSet(genome);
            genomesById.put(set.stableId(), genome);
            batch.add(set);
        }
        if (!batch.isEmpty()) {
            generationPending = true;
        }
        return batch;
    }

    /**
     * One observed evaluation bound to its proposed genome.
     */
    private record GenomeEvaluation(int[] genome, ParameterResearch.EvaluatedCandidate evaluated) {
    }
}
