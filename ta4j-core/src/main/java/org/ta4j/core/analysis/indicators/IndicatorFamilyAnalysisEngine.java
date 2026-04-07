/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

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
 * Engine for deterministic indicator-family analysis.
 *
 * @since 0.22.7
 */
public final class IndicatorFamilyAnalysisEngine {

    private static final String FINGERPRINT_DELIMITER = "|";
    private static final String FAMILY_ID_PREFIX = "family-";

    private IndicatorFamilyAnalysisEngine() {
    }

    /**
     * Builds analysis artifacts for each requested configuration.
     *
     * @param series   source series
     * @param manifest curated indicator manifest
     * @param configs  configs to evaluate
     * @return deterministic family catalogs and drift records
     * @since 0.22.7
     */
    public static IndicatorFamilyAnalysisResult run(BarSeries series, IndicatorFamilyManifest manifest,
            List<IndicatorFamilyAnalysisConfig> configs) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(manifest, "manifest");
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("configs must not be empty");
        }
        for (int index = 0; index < configs.size(); index++) {
            if (configs.get(index) == null) {
                throw new IllegalArgumentException("configs[" + index + "] must not be null");
            }
        }

        List<IndicatorFamilyManifest.ResolvedManifestItem> indicators = manifest.resolveIndicators(series);
        if (indicators.isEmpty()) {
            throw new IllegalArgumentException("manifest must contain at least one indicator");
        }

        List<IndicatorFamilyCatalog> catalogs = new ArrayList<>(configs.size());
        for (IndicatorFamilyAnalysisConfig config : configs) {
            catalogs.add(buildCatalog(manifest, indicators, config));
        }
        return new IndicatorFamilyAnalysisResult(manifest.manifestId(), manifest.configHash(), List.copyOf(catalogs),
                List.copyOf(buildDrifts(catalogs)));
    }

    /**
     * Builds one catalog for a single configuration.
     *
     * @param series   source series
     * @param manifest curated indicator manifest
     * @param config   config to apply
     * @return deterministic family catalog
     * @since 0.22.7
     */
    public static IndicatorFamilyCatalog runSingleConfig(BarSeries series, IndicatorFamilyManifest manifest,
            IndicatorFamilyAnalysisConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(config, "config");
        return buildCatalog(manifest, manifest.resolveIndicators(series), config);
    }

    private static IndicatorFamilyCatalog buildCatalog(IndicatorFamilyManifest manifest,
            List<IndicatorFamilyManifest.ResolvedManifestItem> resolvedIndicators,
            IndicatorFamilyAnalysisConfig config) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(resolvedIndicators, "resolvedIndicators");
        Objects.requireNonNull(config, "config");

        List<Indicator<Num>> indicators = new ArrayList<>(resolvedIndicators.size());
        List<String> indicatorIds = new ArrayList<>(resolvedIndicators.size());
        for (IndicatorFamilyManifest.ResolvedManifestItem item : resolvedIndicators) {
            indicators.add(item.indicator());
            indicatorIds.add(item.indicatorId());
        }

        List<PairSimilarity> pairSimilarities = new ArrayList<>(
                Math.max(0, (indicatorIds.size() * (indicatorIds.size() - 1)) / 2));
        int stableIndex = maximumUnstableBars(indicators);

        for (int left = 0; left < indicators.size(); left++) {
            for (int right = left + 1; right < indicators.size(); right++) {
                Indicator<Num> leftIndicator = indicators.get(left);
                Indicator<Num> rightIndicator = indicators.get(right);
                CorrelationCoefficientIndicator correlation = new CorrelationCoefficientIndicator(leftIndicator,
                        rightIndicator, config.correlationWindow(), SampleType.POPULATION);

                double similarity = estimatePairSimilarity(correlation, config);
                String pairKey = buildPairKey(indicatorIds.get(left), indicatorIds.get(right));
                pairSimilarities.add(new PairSimilarity(pairKey, left, right, similarity));
            }
        }

        Map<String, Double> pairSimilarityMap = new LinkedHashMap<>(pairSimilarities.size());
        for (PairSimilarity pair : pairSimilarities) {
            pairSimilarityMap.put(pair.key(), pair.similarity());
        }

        List<IndicatorFamilyCatalog.Family> families = clusterIntoFamilies(indicatorIds, pairSimilarities,
                config.similarityThreshold());
        Map<String, String> familyByIndicator = mapIndicatorToFamily(indicatorIds, families);
        String catalogId = String.format(Locale.ROOT, "%s|%s|%s", manifest.manifestId(), manifest.configHash(),
                config.configId());
        String pairwiseFingerprint = buildPairwiseFingerprint(pairSimilarityMap);

        return new IndicatorFamilyCatalog(catalogId, manifest.manifestId(), manifest.configHash(), config, stableIndex,
                familyByIndicator, families, Map.copyOf(pairSimilarityMap), pairwiseFingerprint);
    }

    private static double estimatePairSimilarity(CorrelationCoefficientIndicator correlation,
            IndicatorFamilyAnalysisConfig config) {
        int start = correlation.getCountOfUnstableBars();
        int end = correlation.getBarSeries().getEndIndex();
        double total = 0;
        int samples = 0;
        for (int index = start; index <= end; index++) {
            Num rawCorrelation = correlation.getValue(index);
            if (rawCorrelation == null || rawCorrelation.isNaN()) {
                continue;
            }
            int overlapBars = Math.min(config.correlationWindow(), index - start + 1);
            double score = config.scoringMode()
                    .score(rawCorrelation.doubleValue(), overlapBars, config.correlationWindow());
            if (Double.isFinite(score)) {
                total += score;
                samples++;
            }
        }
        if (samples == 0) {
            return 0.0;
        }
        return clamp(total / samples);
    }

    private static Map<String, String> mapIndicatorToFamily(List<String> indicatorIds,
            List<IndicatorFamilyCatalog.Family> families) {
        Map<String, String> familyByIndicator = new LinkedHashMap<>(indicatorIds.size());
        for (IndicatorFamilyCatalog.Family family : families) {
            for (String indicatorId : family.indicatorIds()) {
                familyByIndicator.put(indicatorId, family.familyId());
            }
        }
        return familyByIndicator;
    }

    private static List<IndicatorFamilyCatalog.FamilyDrift> buildDrifts(List<IndicatorFamilyCatalog> catalogs) {
        if (catalogs.size() < 2) {
            return List.of();
        }

        List<IndicatorFamilyCatalog.FamilyDrift> drifts = new ArrayList<>(catalogs.size() - 1);
        for (int index = 1; index < catalogs.size(); index++) {
            IndicatorFamilyCatalog previous = catalogs.get(index - 1);
            IndicatorFamilyCatalog current = catalogs.get(index);
            List<IndicatorFamilyCatalog.FamilyTransition> transitions = new ArrayList<>();
            for (Map.Entry<String, String> entry : current.familyByIndicator().entrySet()) {
                String indicatorId = entry.getKey();
                String fromFamily = previous.familyByIndicator().get(indicatorId);
                String toFamily = entry.getValue();
                if (fromFamily != null && !fromFamily.equals(toFamily)) {
                    transitions.add(new IndicatorFamilyCatalog.FamilyTransition(indicatorId, fromFamily, toFamily));
                }
            }
            drifts.add(new IndicatorFamilyCatalog.FamilyDrift(previous.config().configId(), current.config().configId(),
                    transitions.size(), transitions));
        }
        return drifts;
    }

    private static int maximumUnstableBars(List<Indicator<Num>> indicators) {
        int unstable = 0;
        for (Indicator<Num> indicator : indicators) {
            unstable = Math.max(unstable, indicator.getCountOfUnstableBars());
        }
        return unstable;
    }

    private static String buildPairKey(String firstIndicatorId, String secondIndicatorId) {
        return firstIndicatorId + "/" + secondIndicatorId;
    }

    private static List<IndicatorFamilyCatalog.Family> clusterIntoFamilies(List<String> orderedIndicatorIds,
            List<PairSimilarity> pairSimilarities, double similarityThreshold) {
        UnionFind unionFind = new UnionFind(orderedIndicatorIds.size());
        for (PairSimilarity pair : pairSimilarities) {
            if (pair.similarity() >= similarityThreshold) {
                unionFind.union(pair.leftIndex(), pair.rightIndex());
            }
        }

        Map<Integer, List<String>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < orderedIndicatorIds.size(); index++) {
            String indicatorId = orderedIndicatorIds.get(index);
            grouped.computeIfAbsent(unionFind.find(index), ignored -> new ArrayList<>()).add(indicatorId);
        }

        List<IndicatorFamilyCatalog.Family> families = new ArrayList<>(grouped.size());
        for (List<String> group : grouped.values()) {
            families.add(new IndicatorFamilyCatalog.Family(
                    String.format(Locale.ROOT, "%s%03d", FAMILY_ID_PREFIX, families.size() + 1), List.copyOf(group)));
        }
        return families;
    }

    private static String buildPairwiseFingerprint(Map<String, Double> pairSimilarity) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Double> entry : pairSimilarity.entrySet()) {
            builder.append(entry.getKey())
                    .append("=")
                    .append(formatFingerprintValue(entry.getValue()))
                    .append(FINGERPRINT_DELIMITER);
        }
        return Integer.toHexString(builder.toString().hashCode());
    }

    private static String formatFingerprintValue(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.12f", value);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static final class UnionFind {
        private final int[] parent;

        private UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        private int find(int node) {
            while (parent[node] != node) {
                parent[node] = parent[parent[node]];
                node = parent[node];
            }
            return node;
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot == rightRoot) {
                return;
            }
            int root = Math.min(leftRoot, rightRoot);
            int attached = Math.max(leftRoot, rightRoot);
            parent[attached] = root;
        }
    }

    private record PairSimilarity(String key, int leftIndex, int rightIndex, double similarity) {
    }
}
