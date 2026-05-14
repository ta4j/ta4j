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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.Num;

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
 *
 * @since 0.22.7
 */
public final class IndicatorFamilyManager {

    private static final int DEFAULT_CORRELATION_WINDOW = 120;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.93;
    private static final String FAMILY_ID_PREFIX = "family-";

    private final BarSeries barSeries;
    private final int correlationWindow;

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
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        if (barSeries.isEmpty()) {
            throw new IllegalArgumentException("barSeries must not be empty");
        }
        if (correlationWindow < 2) {
            throw new IllegalArgumentException("correlationWindow must be at least 2");
        }
        this.correlationWindow = correlationWindow;
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
     *                            merge two indicators into the same family
     * @return family analysis result
     * @since 0.22.7
     */
    public IndicatorFamilyResult analyze(Map<String, Indicator<Num>> indicators, double similarityThreshold) {
        validateSimilarityThreshold(similarityThreshold);
        LinkedHashMap<String, Indicator<Num>> orderedIndicators = validateIndicators(indicators);

        List<String> indicatorNames = new ArrayList<>(orderedIndicators.keySet());
        List<Indicator<Num>> indicatorValues = new ArrayList<>(orderedIndicators.values());
        List<PairSimilarityWithIndexes> indexedPairSimilarities = new ArrayList<>(
                Math.max(0, (indicatorValues.size() * (indicatorValues.size() - 1)) / 2));
        List<IndicatorFamilyResult.PairSimilarity> pairSimilarities = new ArrayList<>(
                Math.max(0, (indicatorValues.size() * (indicatorValues.size() - 1)) / 2));

        int stableIndex = Math.max(barSeries.getBeginIndex(), maximumUnstableBars(indicatorValues));
        for (int left = 0; left < indicatorValues.size(); left++) {
            for (int right = left + 1; right < indicatorValues.size(); right++) {
                CorrelationCoefficientIndicator correlation = new CorrelationCoefficientIndicator(
                        indicatorValues.get(left), indicatorValues.get(right), correlationWindow,
                        SampleType.POPULATION);
                stableIndex = Math.max(stableIndex,
                        Math.max(correlation.getBarSeries().getBeginIndex(), correlation.getCountOfUnstableBars()));

                double similarity = estimatePairSimilarity(correlation);
                String leftName = indicatorNames.get(left);
                String rightName = indicatorNames.get(right);
                indexedPairSimilarities.add(new PairSimilarityWithIndexes(left, right, similarity));
                pairSimilarities.add(new IndicatorFamilyResult.PairSimilarity(leftName, rightName, similarity));
            }
        }

        List<IndicatorFamilyResult.Family> families = clusterIntoFamilies(indicatorNames, indexedPairSimilarities,
                similarityThreshold);
        Map<String, String> familyByIndicator = mapIndicatorToFamily(families);
        return new IndicatorFamilyResult(similarityThreshold, stableIndex, familyByIndicator, families,
                pairSimilarities);
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

    private static void validateSimilarityThreshold(double similarityThreshold) {
        if (!Double.isFinite(similarityThreshold) || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
    }

    private static int maximumUnstableBars(List<Indicator<Num>> indicators) {
        int unstableBars = 0;
        for (Indicator<Num> indicator : indicators) {
            unstableBars = Math.max(unstableBars, indicator.getCountOfUnstableBars());
        }
        return unstableBars;
    }

    private static double estimatePairSimilarity(CorrelationCoefficientIndicator correlation) {
        int startIndex = Math.max(Math.max(0, correlation.getBarSeries().getBeginIndex()),
                correlation.getCountOfUnstableBars());
        int endIndex = correlation.getBarSeries().getEndIndex();
        double total = 0.0;
        int samples = 0;
        for (int index = startIndex; index <= endIndex; index++) {
            Num value = correlation.getValue(index);
            if (IndicatorUtils.isInvalid(value)) {
                continue;
            }

            double similarity = Math.abs(value.doubleValue());
            if (Double.isFinite(similarity)) {
                total += similarity;
                samples++;
            }
        }
        if (samples == 0) {
            return 0.0;
        }
        return clamp(total / samples);
    }

    private static List<IndicatorFamilyResult.Family> clusterIntoFamilies(List<String> indicatorNames,
            List<PairSimilarityWithIndexes> pairSimilarities, double similarityThreshold) {
        UnionFind unionFind = new UnionFind(indicatorNames.size());
        for (PairSimilarityWithIndexes pair : pairSimilarities) {
            if (pair.similarity() >= similarityThreshold) {
                unionFind.union(pair.leftIndex(), pair.rightIndex());
            }
        }

        Map<Integer, List<String>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < indicatorNames.size(); index++) {
            grouped.computeIfAbsent(unionFind.find(index), ignored -> new ArrayList<>()).add(indicatorNames.get(index));
        }

        List<IndicatorFamilyResult.Family> families = new ArrayList<>(grouped.size());
        for (List<String> familyMembers : grouped.values()) {
            String familyId = String.format(Locale.ROOT, "%s%03d", FAMILY_ID_PREFIX, families.size() + 1);
            families.add(new IndicatorFamilyResult.Family(familyId, familyMembers));
        }
        return families;
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

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record PairSimilarityWithIndexes(int leftIndex, int rightIndex, double similarity) {
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
