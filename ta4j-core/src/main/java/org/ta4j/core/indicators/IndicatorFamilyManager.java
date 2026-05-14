/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Groups indicators by similarity over a single {@link BarSeries}.
 * <p>
 * Callers provide already-instantiated indicators and stable display names. The
 * manager compares each pair with an absolute population correlation score and
 * places indicators whose average similarity is at or above the requested
 * threshold into the same family. The default constructor uses a 120-bar
 * rolling correlation window; use
 * {@link #IndicatorFamilyManager(BarSeries, int)} when shorter or longer
 * windows better match the analysis horizon.
 * <p>
 * Custom metric factories may be supplied when a different signed similarity
 * stream is more appropriate. The returned metric indicator must use the same
 * {@link BarSeries} and must return values in {@code [-1, 1]}; invalid values
 * are skipped.
 *
 * @since 0.22.7
 */
public final class IndicatorFamilyManager {

    private static final int DEFAULT_CORRELATION_WINDOW = 120;
    private static final Number DEFAULT_SIMILARITY_THRESHOLD = 0.93;
    private static final String FAMILY_ID_PREFIX = "family-";

    private final BarSeries barSeries;
    private final BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> similarityMetricFactory;
    private final int maxParallelism;

    /**
     * Constructor using the default rolling correlation window.
     *
     * @param barSeries bar series shared by every indicator passed to
     *                  {@link #analyze(Map)}
     * @throws IllegalArgumentException if {@code barSeries} is empty
     * @throws NullPointerException     if {@code barSeries} is {@code null}
     * @since 0.22.7
     */
    public IndicatorFamilyManager(BarSeries barSeries) {
        this(barSeries, DEFAULT_CORRELATION_WINDOW);
    }

    /**
     * Constructor.
     *
     * @param barSeries         bar series shared by every indicator passed to
     *                          {@link #analyze(Map)}
     * @param correlationWindow number of bars used for each rolling pairwise
     *                          correlation; must be at least {@code 2}
     * @throws IllegalArgumentException if {@code barSeries} is empty or
     *                                  {@code correlationWindow} is less than
     *                                  {@code 2}
     * @throws NullPointerException     if {@code barSeries} is {@code null}
     * @since 0.22.7
     */
    public IndicatorFamilyManager(BarSeries barSeries, int correlationWindow) {
        this(barSeries, defaultSimilarityMetric(correlationWindow), 1);
    }

    /**
     * Constructor using the default rolling correlation window and a
     * caller-provided pairwise similarity metric.
     *
     * @param barSeries               bar series shared by every indicator passed to
     *                                {@link #analyze(Map)}
     * @param similarityMetricFactory factory that creates a signed similarity
     *                                indicator for each indicator pair
     * @throws IllegalArgumentException if {@code barSeries} is empty
     * @throws NullPointerException     if {@code barSeries} or
     *                                  {@code similarityMetricFactory} is
     *                                  {@code null}
     * @since 0.22.7
     */
    public IndicatorFamilyManager(BarSeries barSeries,
            BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> similarityMetricFactory) {
        this(barSeries, similarityMetricFactory, 1);
    }

    /**
     * Constructor using the default population-correlation metric with opt-in
     * bounded pair-level parallelism.
     * <p>
     * Use this only when the supplied indicators and their {@link BarSeries} are
     * safe for concurrent reads. A value of {@code 1} forces sequential execution.
     *
     * @param barSeries         bar series shared by every indicator passed to
     *                          {@link #analyze(Map)}
     * @param correlationWindow number of bars used for each rolling pairwise
     *                          correlation; must be at least {@code 2}
     * @param maxParallelism    maximum number of pair analyses to run concurrently;
     *                          must be at least {@code 1}
     * @throws IllegalArgumentException if {@code barSeries} is empty,
     *                                  {@code correlationWindow} is less than
     *                                  {@code 2}, or {@code maxParallelism} is less
     *                                  than {@code 1}
     * @throws NullPointerException     if {@code barSeries} is {@code null}
     * @since 0.22.7
     */
    public IndicatorFamilyManager(BarSeries barSeries, int correlationWindow, int maxParallelism) {
        this(barSeries, defaultSimilarityMetric(correlationWindow), maxParallelism);
    }

    /**
     * Constructor using a caller-provided pairwise similarity metric with opt-in
     * bounded pair-level parallelism.
     * <p>
     * Use parallelism only when the supplied indicators, metric factory, and
     * {@link BarSeries} are safe for concurrent reads. A value of {@code 1} forces
     * sequential execution.
     *
     * @param barSeries               bar series shared by every indicator passed to
     *                                {@link #analyze(Map)}
     * @param similarityMetricFactory factory that creates a signed similarity
     *                                indicator for each indicator pair
     * @param maxParallelism          maximum number of pair analyses to run
     *                                concurrently; must be at least {@code 1}
     * @throws IllegalArgumentException if {@code barSeries} is empty or
     *                                  {@code maxParallelism} is less than
     *                                  {@code 1}
     * @throws NullPointerException     if {@code barSeries} or
     *                                  {@code similarityMetricFactory} is
     *                                  {@code null}
     * @since 0.22.7
     */
    public IndicatorFamilyManager(BarSeries barSeries,
            BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> similarityMetricFactory, int maxParallelism) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        if (barSeries.isEmpty()) {
            throw new IllegalArgumentException("barSeries must not be empty");
        }
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("maxParallelism must be at least 1");
        }
        this.similarityMetricFactory = Objects.requireNonNull(similarityMetricFactory, "similarityMetricFactory");
        this.maxParallelism = maxParallelism;
    }

    /**
     * Analyzes indicator families with the default similarity threshold.
     *
     * @param indicators named indicators in deterministic iteration order
     * @return family analysis result
     * @since 0.22.7
     */
    public IndicatorFamilyResult analyze(Map<String, Indicator<Num>> indicators) {
        return analyze(indicators, DEFAULT_SIMILARITY_THRESHOLD);
    }

    /**
     * Analyzes indicator families with a caller-provided similarity threshold.
     *
     * @param indicators          named indicators in deterministic iteration order
     * @param similarityThreshold minimum absolute average correlation required to
     *                            merge two indicators into the same family;
     *                            converted with this manager's {@link BarSeries}
     *                            number factory
     * @return family analysis result
     * @since 0.22.7
     */
    public IndicatorFamilyResult analyze(Map<String, Indicator<Num>> indicators, Number similarityThreshold) {
        Num threshold = requireSimilarityThreshold(similarityThreshold);
        LinkedHashMap<String, Indicator<Num>> orderedIndicators = validateIndicators(indicators);

        List<String> indicatorNames = new ArrayList<>(orderedIndicators.keySet());
        List<Indicator<Num>> indicatorValues = new ArrayList<>(orderedIndicators.values());
        List<PairRequest> pairRequests = buildPairRequests(indicatorNames, indicatorValues);
        List<PairAnalysis> pairAnalyses = analyzePairs(pairRequests);

        int stableIndex = Math.max(barSeries.getBeginIndex(), maximumUnstableBars(indicatorValues));
        List<IndicatorFamilyResult.PairSimilarity> pairSimilarities = new ArrayList<>(pairAnalyses.size());
        for (PairAnalysis pairAnalysis : pairAnalyses) {
            stableIndex = Math.max(stableIndex, pairAnalysis.stableIndex());
            pairSimilarities.add(pairAnalysis.pairSimilarity());
        }

        List<IndicatorFamilyResult.Family> families = clusterIntoFamilies(indicatorNames, pairAnalyses, threshold,
                barSeries.numFactory());
        Map<String, String> familyByIndicator = mapIndicatorToFamily(families);
        return new IndicatorFamilyResult(threshold, stableIndex, familyByIndicator, families, pairSimilarities);
    }

    private static BiFunction<Indicator<Num>, Indicator<Num>, Indicator<Num>> defaultSimilarityMetric(
            int correlationWindow) {
        if (correlationWindow < 2) {
            throw new IllegalArgumentException("correlationWindow must be at least 2");
        }
        return (first, second) -> new CorrelationCoefficientIndicator(first, second, correlationWindow,
                SampleType.POPULATION);
    }

    private LinkedHashMap<String, Indicator<Num>> validateIndicators(Map<String, Indicator<Num>> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            throw new IllegalArgumentException("indicators must not be empty");
        }

        LinkedHashMap<String, Indicator<Num>> orderedIndicators = new LinkedHashMap<>(indicators.size());
        for (Map.Entry<String, Indicator<Num>> entry : indicators.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "indicator names must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("indicator names must not be blank");
            }

            Indicator<Num> indicator = IndicatorUtils.requireIndicator(entry.getValue(), "indicator " + name);
            BarSeries indicatorSeries = Objects.requireNonNull(indicator.getBarSeries(),
                    "indicator " + name + " must reference a bar series");
            if (indicatorSeries != barSeries) {
                throw new IllegalArgumentException("indicator " + name + " must use the manager bar series");
            }
            orderedIndicators.put(name, indicator);
        }
        return orderedIndicators;
    }

    private Num requireSimilarityThreshold(Number similarityThreshold) {
        Objects.requireNonNull(similarityThreshold, "similarityThreshold");
        Num threshold;
        try {
            threshold = barSeries.numFactory().numOf(similarityThreshold);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0", exception);
        }
        if (IndicatorUtils.isInvalid(threshold) || threshold.isNegative()
                || threshold.isGreaterThan(barSeries.numFactory().one())) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        return threshold;
    }

    private static int maximumUnstableBars(List<Indicator<Num>> indicators) {
        int unstableBars = 0;
        for (Indicator<Num> indicator : indicators) {
            unstableBars = Math.max(unstableBars, indicator.getCountOfUnstableBars());
        }
        return unstableBars;
    }

    private List<PairRequest> buildPairRequests(List<String> indicatorNames, List<Indicator<Num>> indicatorValues) {
        List<PairRequest> pairRequests = new ArrayList<>(pairCount(indicatorValues.size()));
        int ordinal = 0;
        for (int left = 0; left < indicatorValues.size(); left++) {
            for (int right = left + 1; right < indicatorValues.size(); right++) {
                pairRequests.add(new PairRequest(ordinal, left, right, indicatorNames.get(left),
                        indicatorNames.get(right), indicatorValues.get(left), indicatorValues.get(right)));
                ordinal++;
            }
        }
        return pairRequests;
    }

    private List<PairAnalysis> analyzePairs(List<PairRequest> pairRequests) {
        if (pairRequests.isEmpty()) {
            return List.of();
        }
        if (maxParallelism == 1 || pairRequests.size() == 1) {
            List<PairAnalysis> pairAnalyses = new ArrayList<>(pairRequests.size());
            for (PairRequest pairRequest : pairRequests) {
                pairAnalyses.add(analyzePair(pairRequest));
            }
            return pairAnalyses;
        }

        int parallelism = Math.min(maxParallelism, pairRequests.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<PairAnalysis>> futures = new ArrayList<>(pairRequests.size());
            for (PairRequest pairRequest : pairRequests) {
                futures.add(executor.submit(() -> analyzePair(pairRequest)));
            }

            PairAnalysis[] pairAnalyses = new PairAnalysis[pairRequests.size()];
            for (Future<PairAnalysis> future : futures) {
                PairAnalysis pairAnalysis = getPairAnalysis(future);
                pairAnalyses[pairAnalysis.ordinal()] = pairAnalysis;
            }

            List<PairAnalysis> ordered = new ArrayList<>(pairAnalyses.length);
            for (PairAnalysis pairAnalysis : pairAnalyses) {
                ordered.add(pairAnalysis);
            }
            return ordered;
        } finally {
            executor.shutdownNow();
        }
    }

    private static PairAnalysis getPairAnalysis(Future<PairAnalysis> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while analyzing indicator similarities", exception);
        } catch (ExecutionException exception) {
            rethrowParallelFailure(exception.getCause());
            throw new IllegalStateException("Unreachable pair-analysis failure", exception);
        }
    }

    private static void rethrowParallelFailure(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Indicator similarity analysis failed", cause);
    }

    private PairAnalysis analyzePair(PairRequest pairRequest) {
        Indicator<Num> similarityMetric = Objects.requireNonNull(
                similarityMetricFactory.apply(pairRequest.leftIndicator(), pairRequest.rightIndicator()),
                "similarityMetricFactory must return an indicator");
        BarSeries metricSeries = Objects.requireNonNull(similarityMetric.getBarSeries(),
                "similarity metric must reference a bar series");
        if (metricSeries != barSeries) {
            throw new IllegalArgumentException("similarity metric must use the manager bar series");
        }

        int stableIndex = Math.max(metricSeries.getBeginIndex(), similarityMetric.getCountOfUnstableBars());
        IndicatorFamilyResult.PairSimilarity pairSimilarity = estimatePairSimilarity(pairRequest, similarityMetric);
        return new PairAnalysis(pairRequest.ordinal(), pairRequest.leftIndex(), pairRequest.rightIndex(), stableIndex,
                pairSimilarity);
    }

    private static IndicatorFamilyResult.PairSimilarity estimatePairSimilarity(PairRequest pairRequest,
            Indicator<Num> similarityMetric) {
        int startIndex = Math.max(Math.max(0, similarityMetric.getBarSeries().getBeginIndex()),
                similarityMetric.getCountOfUnstableBars());
        int endIndex = similarityMetric.getBarSeries().getEndIndex();
        NumFactory numFactory = similarityMetric.getBarSeries().numFactory();
        Num absoluteTotal = numFactory.zero();
        Num signedTotal = numFactory.zero();
        Num latestSignedSimilarity = numFactory.zero();
        Num minimumSignedSimilarity = null;
        Num maximumSignedSimilarity = null;
        int samples = 0;
        for (int index = startIndex; index <= endIndex; index++) {
            Num value = similarityMetric.getValue(index);
            if (IndicatorUtils.isInvalid(value)) {
                continue;
            }
            requireSignedSimilarityValue(value);
            Num signedSimilarity = clampSigned(value, numFactory);

            absoluteTotal = absoluteTotal.plus(signedSimilarity.abs());
            signedTotal = signedTotal.plus(signedSimilarity);
            latestSignedSimilarity = signedSimilarity;
            minimumSignedSimilarity = minimumSignedSimilarity == null ? signedSimilarity
                    : minimumSignedSimilarity.min(signedSimilarity);
            maximumSignedSimilarity = maximumSignedSimilarity == null ? signedSimilarity
                    : maximumSignedSimilarity.max(signedSimilarity);
            samples++;
        }
        if (samples == 0) {
            return new IndicatorFamilyResult.PairSimilarity(pairRequest.leftName(), pairRequest.rightName(),
                    numFactory.zero(), numFactory.zero(), numFactory.zero(), 0, numFactory.zero(), numFactory.zero());
        }
        Num sampleCount = numFactory.numOf(samples);
        Num similarity = clamp(absoluteTotal.dividedBy(sampleCount), numFactory);
        Num signedAverageSimilarity = clampSigned(signedTotal.dividedBy(sampleCount), numFactory);
        return new IndicatorFamilyResult.PairSimilarity(pairRequest.leftName(), pairRequest.rightName(), similarity,
                signedAverageSimilarity, latestSignedSimilarity, samples, minimumSignedSimilarity,
                maximumSignedSimilarity);
    }

    private static void requireSignedSimilarityValue(Num value) {
        NumFactory numFactory = value.getNumFactory();
        Num lowerBound = numFactory.minusOne().minus(numFactory.epsilon());
        Num upperBound = numFactory.one().plus(numFactory.epsilon());
        if (value.isLessThan(lowerBound) || value.isGreaterThan(upperBound)) {
            throw new IllegalArgumentException("similarity metric values must be between -1.0 and 1.0");
        }
    }

    private static List<IndicatorFamilyResult.Family> clusterIntoFamilies(List<String> indicatorNames,
            List<PairAnalysis> pairAnalyses, Num similarityThreshold, NumFactory numFactory) {
        UnionFind unionFind = new UnionFind(indicatorNames.size());
        Num[][] similarityByIndex = new Num[indicatorNames.size()][indicatorNames.size()];
        for (int index = 0; index < indicatorNames.size(); index++) {
            similarityByIndex[index][index] = numFactory.one();
        }
        for (PairAnalysis pair : pairAnalyses) {
            similarityByIndex[pair.leftIndex()][pair.rightIndex()] = pair.similarity();
            similarityByIndex[pair.rightIndex()][pair.leftIndex()] = pair.similarity();
            if (pair.similarity().isGreaterThanOrEqual(similarityThreshold)) {
                unionFind.union(pair.leftIndex(), pair.rightIndex());
            }
        }

        Map<Integer, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < indicatorNames.size(); index++) {
            grouped.computeIfAbsent(unionFind.find(index), ignored -> new ArrayList<>()).add(index);
        }

        List<IndicatorFamilyResult.Family> families = new ArrayList<>(grouped.size());
        for (List<Integer> familyMemberIndexes : grouped.values()) {
            List<String> familyMembers = new ArrayList<>(familyMemberIndexes.size());
            for (int index : familyMemberIndexes) {
                familyMembers.add(indicatorNames.get(index));
            }
            FamilyStats familyStats = computeFamilyStats(familyMemberIndexes, indicatorNames, similarityByIndex,
                    numFactory);
            String familyId = String.format(Locale.ROOT, "%s%03d", FAMILY_ID_PREFIX, families.size() + 1);
            families.add(
                    new IndicatorFamilyResult.Family(familyId, familyMembers, familyStats.representativeIndicatorName(),
                            familyStats.averageInternalSimilarity(), familyStats.minimumInternalSimilarity()));
        }
        return families;
    }

    private static FamilyStats computeFamilyStats(List<Integer> familyMemberIndexes, List<String> indicatorNames,
            Num[][] similarityByIndex, NumFactory numFactory) {
        if (familyMemberIndexes.size() == 1) {
            return new FamilyStats(indicatorNames.get(familyMemberIndexes.get(0)), numFactory.one(), numFactory.one());
        }

        Num internalTotal = numFactory.zero();
        Num minimumInternalSimilarity = numFactory.one();
        int internalPairs = 0;
        String representativeIndicatorName = indicatorNames.get(familyMemberIndexes.get(0));
        Num representativeScore = numFactory.minusOne();

        for (int memberIndex : familyMemberIndexes) {
            Num memberTotal = numFactory.zero();
            for (int otherIndex : familyMemberIndexes) {
                if (memberIndex == otherIndex) {
                    continue;
                }
                memberTotal = memberTotal.plus(similarityByIndex[memberIndex][otherIndex]);
            }
            Num memberAverage = memberTotal.dividedBy(numFactory.numOf(familyMemberIndexes.size() - 1));
            if (memberAverage.isGreaterThan(representativeScore)) {
                representativeScore = memberAverage;
                representativeIndicatorName = indicatorNames.get(memberIndex);
            }
        }

        for (int left = 0; left < familyMemberIndexes.size(); left++) {
            for (int right = left + 1; right < familyMemberIndexes.size(); right++) {
                Num similarity = similarityByIndex[familyMemberIndexes.get(left)][familyMemberIndexes.get(right)];
                internalTotal = internalTotal.plus(similarity);
                minimumInternalSimilarity = minimumInternalSimilarity.min(similarity);
                internalPairs++;
            }
        }

        Num averageInternalSimilarity = internalTotal.dividedBy(numFactory.numOf(internalPairs));
        return new FamilyStats(representativeIndicatorName, averageInternalSimilarity, minimumInternalSimilarity);
    }

    private static Map<String, String> mapIndicatorToFamily(List<IndicatorFamilyResult.Family> families) {
        Map<String, String> familyByIndicator = new LinkedHashMap<>();
        for (IndicatorFamilyResult.Family family : families) {
            for (String indicatorName : family.indicatorNames()) {
                familyByIndicator.put(indicatorName, family.familyId());
            }
        }
        return familyByIndicator;
    }

    private static Num clamp(Num value, NumFactory numFactory) {
        return value.max(numFactory.zero()).min(numFactory.one());
    }

    private static Num clampSigned(Num value, NumFactory numFactory) {
        return value.max(numFactory.minusOne()).min(numFactory.one());
    }

    private static int pairCount(int indicatorCount) {
        return Math.max(0, (indicatorCount * (indicatorCount - 1)) / 2);
    }

    private record PairRequest(int ordinal, int leftIndex, int rightIndex, String leftName, String rightName,
            Indicator<Num> leftIndicator, Indicator<Num> rightIndicator) {
    }

    private record PairAnalysis(int ordinal, int leftIndex, int rightIndex, int stableIndex,
            IndicatorFamilyResult.PairSimilarity pairSimilarity) {

        private Num similarity() {
            return pairSimilarity.similarity();
        }
    }

    private record FamilyStats(String representativeIndicatorName, Num averageInternalSimilarity,
            Num minimumInternalSimilarity) {
    }

    private static final class UnionFind {

        private final int[] parent;

        private UnionFind(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot != secondRoot) {
                parent[secondRoot] = firstRoot;
            }
        }
    }
}
