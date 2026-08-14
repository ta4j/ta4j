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
 * is sampled distinctly, and every breeding batch prefers unseen genomes:
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
 * evaluation of the generation is compared against the best-ever evaluation to
 * derive the no-improvement streak, the iteration counter advances, and the
 * next generation is bred from the current generation's valid evaluations via
 * elitism, tournament selection, uniform per-dimension crossover, and
 * per-dimension mutation. All randomness comes from the run-local seeded
 * {@link Random}, so identical configuration reproduces identical proposals.
 * </p>
 */
final class GeneticSearchEngine extends SearchEngine {

    private static final int UNSEEN_CHILD_ATTEMPTS = 1000;

    private final ParameterResearch.GeneticSettings settings;
    private final Random random;
    private final Comparator<ParameterResearch.EvaluatedCandidate> ranking;
    private final int maxIterations;
    private final int noImprovementIterations;
    private final Map<String, int[]> genomesById = new HashMap<>();

    private boolean initialized;
    private List<int[]> population;
    private List<GenomeEvaluation> generationEvaluations = new ArrayList<>();
    private ParameterResearch.EvaluatedCandidate bestEver;
    private int noImprovementStreak;

    GeneticSearchEngine(List<DomainSpec> specs, ParameterResearch.GeneticSettings settings, Random random,
            Comparator<ParameterResearch.EvaluatedCandidate> ranking, int maxIterations, int noImprovementIterations) {
        super(specs);
        this.settings = settings;
        this.random = random;
        this.ranking = ranking;
        this.maxIterations = maxIterations;
        this.noImprovementIterations = noImprovementIterations;
    }

    @Override
    List<ParameterResearch.ParameterSet> propose(int maxNew) {
        if (!initialized) {
            initialized = true;
            int size = Math.min(settings.populationSize(), maxNew);
            population = sampleDistinct(size);
            return proposalBatch(population);
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        List<GenomeEvaluation> valid = finalizeGeneration();
        if (terminationReason() != null) {
            return List.of();
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        int count = Math.min(population.size(), maxNew);
        population = breed(count, valid);
        return proposalBatch(population);
    }

    @Override
    void observe(ParameterResearch.EvaluatedCandidate evaluated) {
        int[] genome = genomesById.get(evaluated.candidateId());
        if (genome != null) {
            generationEvaluations.add(new GenomeEvaluation(genome, evaluated));
        }
    }

    private List<GenomeEvaluation> finalizeGeneration() {
        completeIteration();
        List<GenomeEvaluation> valid = generationEvaluations.stream()
                .filter(g -> g.evaluated().valid())
                .sorted((a, b) -> ranking.compare(a.evaluated(), b.evaluated()))
                .toList();
        if (valid.isEmpty()) {
            noImprovementStreak++;
        } else {
            ParameterResearch.EvaluatedCandidate generationBest = valid.get(0).evaluated();
            if (bestEver == null || ranking.compare(generationBest, bestEver) < 0) {
                bestEver = generationBest;
                noImprovementStreak = 0;
            } else {
                noImprovementStreak++;
            }
        }
        if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
            terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
        }
        generationEvaluations = new ArrayList<>();
        return valid;
    }

    private List<int[]> breed(int count, List<GenomeEvaluation> valid) {
        List<int[]> next = new ArrayList<>(count);
        Set<String> batchIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int[] child = unseenChild(valid, batchIds);
            if (child != null) {
                next.add(child);
            }
        }
        // Pad with elites only when no unseen child could be bred; elites are
        // already evaluated, so the pipeline serves them from the cache.
        if (next.size() < count) {
            int eliteCount = Math.min(settings.elitismCount(), Math.min(count - next.size(), valid.size()));
            for (int i = 0; i < eliteCount; i++) {
                next.add(valid.get(i).genome().clone());
            }
        }
        return next;
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
                    child[d] = random.nextDouble() < settings.crossoverRate() ? parentA[d] : parentB[d];
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
        return genomes;
    }

    private int[] randomGenome() {
        int[] genome = new int[specs().size()];
        for (int d = 0; d < genome.length; d++) {
            genome[d] = random.nextInt(specs().get(d).cardinality());
        }
        return genome;
    }

    private List<ParameterResearch.ParameterSet> proposalBatch(List<int[]> genomes) {
        List<ParameterResearch.ParameterSet> batch = new ArrayList<>(genomes.size());
        for (int[] genome : genomes) {
            ParameterResearch.ParameterSet set = parameterSet(genome);
            genomesById.put(set.stableId(), genome);
            batch.add(set);
        }
        return batch;
    }

    /**
     * One observed evaluation bound to its proposed genome.
     */
    private record GenomeEvaluation(int[] genome, ParameterResearch.EvaluatedCandidate evaluated) {
    }
}
