/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.genetics.Chromosome;
import org.apache.commons.math3.genetics.ElitisticListPopulation;
import org.apache.commons.math3.genetics.GeneticAlgorithm;
import org.apache.commons.math3.genetics.MutationPolicy;
import org.apache.commons.math3.genetics.OnePointCrossover;
import org.apache.commons.math3.genetics.Population;
import org.apache.commons.math3.genetics.TournamentSelection;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.ta4j.core.num.Num;

/**
 * Seeded genetic search for parameterized ta4j candidates.
 *
 * @param <C> decoded candidate context type
 * @since 0.22.7
 */
public final class GeneticCandidateSearch<C> {

    private final CandidateCodec<C> codec;
    private final CandidateFitnessEvaluator<C> evaluator;
    private final Settings settings;

    /**
     * Creates a configured search.
     *
     * @param codec     chromosome decoder
     * @param evaluator candidate fitness evaluator
     * @param settings  GA configuration
     * @since 0.22.7
     */
    public GeneticCandidateSearch(CandidateCodec<C> codec, CandidateFitnessEvaluator<C> evaluator, Settings settings) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Runs the configured GA search.
     *
     * @return immutable result bundle containing the top retained candidates
     * @since 0.22.7
     */
    public SearchResult<C> search() {
        RandomGenerator previousRandom = GeneticAlgorithm.getRandomGenerator();
        JDKRandomGenerator seededRandom = new JDKRandomGenerator();
        seededRandom.setSeed(settings.randomSeed());
        GeneticAlgorithm.setRandomGenerator(seededRandom);
        try {
            CandidateChromosome.EvaluationContext<C> evaluationContext = new CandidateChromosome.EvaluationContext<>(
                    codec, evaluator);
            Population population = buildInitialPopulation(evaluationContext, seededRandom);
            evaluatePopulation(population);

            double crossoverRate = codec.domains().size() > 1 ? settings.crossoverRate() : 0.0;
            GeneticAlgorithm algorithm = new GeneticAlgorithm(new OnePointCrossover<>(), crossoverRate,
                    new DomainMutationPolicy<>(codec, evaluationContext), settings.mutationRate(),
                    new TournamentSelection(settings.tournamentArity()));

            for (int generation = 0; generation < settings.generations(); generation++) {
                population = algorithm.nextGeneration(population);
                evaluatePopulation(population);
            }

            List<CandidateResult<C>> topCandidates = evaluationContext.results()
                    .stream()
                    .sorted(Comparator
                            .comparing(CandidateChromosome.EvaluationRecord<C>::fitnessScore,
                                    CandidateChromosome::compareScores)
                            .reversed()
                            .thenComparing(record -> record.decodedCandidate().id()))
                    .limit(settings.keepTopK())
                    .map(record -> new CandidateResult<>(record.decodedCandidate().id(),
                            record.decodedCandidate().context(), record.decodedCandidate().parameters(),
                            record.fitnessScore()))
                    .toList();

            return new SearchResult<>(topCandidates, evaluationContext.results().size(), settings.generations());
        } finally {
            GeneticAlgorithm.setRandomGenerator(previousRandom);
        }
    }

    private Population buildInitialPopulation(CandidateChromosome.EvaluationContext<C> evaluationContext,
            RandomGenerator random) {
        List<Chromosome> chromosomes = new ArrayList<>(settings.populationSize());
        for (int index = 0; index < settings.populationSize(); index++) {
            chromosomes.add(new CandidateChromosome<>(codec.randomRepresentation(random), codec, evaluationContext));
        }
        double elitismRate = (double) settings.eliteCount() / settings.populationSize();
        return new ElitisticListPopulation(chromosomes, settings.populationSize(), elitismRate);
    }

    private void evaluatePopulation(Population population) {
        for (Chromosome chromosome : population) {
            chromosome.getFitness();
        }
    }

    /**
     * Immutable GA configuration.
     *
     * @param populationSize  population size per generation
     * @param generations     number of generations to evolve
     * @param keepTopK        number of best unique candidates to retain
     * @param eliteCount      number of elite chromosomes preserved each generation
     * @param crossoverRate   crossover probability in {@code [0,1]}
     * @param mutationRate    mutation probability in {@code [0,1]}
     * @param tournamentArity selection tournament size
     * @param randomSeed      deterministic RNG seed
     * @since 0.22.7
     */
    public record Settings(int populationSize, int generations, int keepTopK, int eliteCount, double crossoverRate,
            double mutationRate, int tournamentArity, long randomSeed) {

        /**
         * Creates a validated settings record.
         *
         * @since 0.22.7
         */
        public Settings {
            if (populationSize <= 0) {
                throw new IllegalArgumentException("populationSize must be > 0");
            }
            if (generations < 0) {
                throw new IllegalArgumentException("generations must be >= 0");
            }
            if (keepTopK <= 0) {
                throw new IllegalArgumentException("keepTopK must be > 0");
            }
            if (eliteCount < 0 || eliteCount > populationSize) {
                throw new IllegalArgumentException("eliteCount must be in [0, populationSize]");
            }
            if (crossoverRate < 0.0 || crossoverRate > 1.0) {
                throw new IllegalArgumentException("crossoverRate must be in [0,1]");
            }
            if (mutationRate < 0.0 || mutationRate > 1.0) {
                throw new IllegalArgumentException("mutationRate must be in [0,1]");
            }
            if (tournamentArity <= 0 || tournamentArity > populationSize) {
                throw new IllegalArgumentException("tournamentArity must be in [1, populationSize]");
            }
        }
    }

    /**
     * One retained candidate result.
     *
     * @param id           stable candidate identifier
     * @param context      decoded candidate context
     * @param parameters   decoded parameter values
     * @param fitnessScore retained fitness score
     * @param <C>          candidate context type
     * @since 0.22.7
     */
    public record CandidateResult<C>(String id, C context, CandidateCodec.ParameterValues parameters,
            Num fitnessScore) {

        /**
         * Creates a validated candidate result.
         *
         * @since 0.22.7
         */
        public CandidateResult {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(fitnessScore, "fitnessScore");
        }
    }

    /**
     * Immutable search result summary.
     *
     * @param topCandidates        top retained candidates in descending fitness
     * @param uniqueCandidateCount unique decoded candidate count evaluated across
     *                             all generations
     * @param generationsExecuted  number of generations executed
     * @param <C>                  candidate context type
     * @since 0.22.7
     */
    public record SearchResult<C>(List<CandidateResult<C>> topCandidates, int uniqueCandidateCount,
            int generationsExecuted) {

        /**
         * Creates a validated search result.
         *
         * @since 0.22.7
         */
        public SearchResult {
            topCandidates = List.copyOf(Objects.requireNonNull(topCandidates, "topCandidates"));
            if (uniqueCandidateCount < 0) {
                throw new IllegalArgumentException("uniqueCandidateCount must be >= 0");
            }
            if (generationsExecuted < 0) {
                throw new IllegalArgumentException("generationsExecuted must be >= 0");
            }
        }
    }

    private static final class DomainMutationPolicy<C> implements MutationPolicy {

        private final CandidateCodec<C> codec;
        private final CandidateChromosome.EvaluationContext<C> evaluationContext;

        private DomainMutationPolicy(CandidateCodec<C> codec,
                CandidateChromosome.EvaluationContext<C> evaluationContext) {
            this.codec = Objects.requireNonNull(codec, "codec");
            this.evaluationContext = Objects.requireNonNull(evaluationContext, "evaluationContext");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Chromosome mutate(Chromosome original) {
            if (!(original instanceof CandidateChromosome<?> rawChromosome)) {
                throw new IllegalArgumentException("mutation requires CandidateChromosome");
            }

            CandidateChromosome<C> chromosome = (CandidateChromosome<C>) rawChromosome;
            List<Integer> representation = chromosome.representationCopy();
            List<Integer> mutableGeneIndexes = new ArrayList<>();
            for (int index = 0; index < codec.domains().size(); index++) {
                if (codec.domains().get(index).size() > 1) {
                    mutableGeneIndexes.add(index);
                }
            }
            if (mutableGeneIndexes.isEmpty()) {
                return chromosome;
            }

            RandomGenerator random = GeneticAlgorithm.getRandomGenerator();
            int geneIndex = mutableGeneIndexes.get(random.nextInt(mutableGeneIndexes.size()));
            ParameterDomain<?> domain = codec.domains().get(geneIndex);
            int currentAllele = representation.get(geneIndex);
            representation.set(geneIndex, domain.mutateIndex(currentAllele, random));
            return new CandidateChromosome<>(representation, codec, evaluationContext);
        }
    }
}
