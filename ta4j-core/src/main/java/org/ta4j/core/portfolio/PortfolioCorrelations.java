/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Portfolio-level correlation analytics for one aligned
 * {@link PortfolioSeries}.
 *
 * <p>
 * Price, one-bar simple-return, and one-bar log-return matrices reuse ta4j's
 * rolling statistics indicators. Matrix values retain the portfolio numeric
 * factory and deterministic asset order.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioCorrelations {

    private final PortfolioSeries series;

    /**
     * Creates correlation analytics for a portfolio series.
     *
     * @param series aligned portfolio series
     * @since 0.23.1
     */
    public PortfolioCorrelations(PortfolioSeries series) {
        this.series = Objects.requireNonNull(series, "series");
    }

    /**
     * @return analyzed portfolio series
     * @since 0.23.1
     */
    public PortfolioSeries getPortfolioSeries() {
        return series;
    }

    /**
     * Builds a population close-price matrix over the full aligned history.
     *
     * @return final close-price correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getPriceMatrix() {
        return getPriceMatrix(series.getBarCount());
    }

    /**
     * Builds a population close-price matrix ending at the final aligned bar.
     *
     * @param barCount number of close-price observations
     * @return final close-price correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getPriceMatrix(int barCount) {
        return getPriceMatrix(series.getEndIndex(), barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population close-price matrix at an aligned index.
     *
     * @param index    aligned portfolio index
     * @param barCount number of close-price observations
     * @return close-price correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getPriceMatrix(int index, int barCount) {
        return getPriceMatrix(index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a close-price matrix at an aligned index.
     *
     * @param index      aligned portfolio index
     * @param barCount   number of close-price observations
     * @param sampleType sample or population normalization
     * @return close-price correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getPriceMatrix(int index, int barCount, SampleType sampleType) {
        return matrix(index, barCount, sampleType, closePriceIndicators(), 0);
    }

    /**
     * Builds a population simple-return matrix over all available one-bar returns.
     *
     * @return final simple-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getSimpleReturnMatrix() {
        return getSimpleReturnMatrix(series.getEndIndex());
    }

    /**
     * Builds a population simple-return matrix ending at the final aligned bar.
     *
     * @param barCount number of one-bar simple returns
     * @return final simple-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getSimpleReturnMatrix(int barCount) {
        return getSimpleReturnMatrix(series.getEndIndex(), barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population simple-return matrix at an aligned index.
     *
     * @param index    aligned portfolio index
     * @param barCount number of one-bar simple returns
     * @return simple-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getSimpleReturnMatrix(int index, int barCount) {
        return getSimpleReturnMatrix(index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a simple-return matrix at an aligned index.
     *
     * <p>
     * Simple returns are decimal returns:
     * {@code close[index] / close[index - 1] - 1}.
     * </p>
     *
     * @param index      aligned portfolio index
     * @param barCount   number of one-bar simple returns
     * @param sampleType sample or population normalization
     * @return simple-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getSimpleReturnMatrix(int index, int barCount, SampleType sampleType) {
        return matrix(index, barCount, sampleType,
                transformedIndicators(closeSeries -> new SimpleReturnIndicator(new ClosePriceIndicator(closeSeries))),
                1);
    }

    /**
     * Builds a population log-return matrix over all available one-bar returns.
     *
     * @return final log-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getLogReturnMatrix() {
        return getLogReturnMatrix(series.getEndIndex());
    }

    /**
     * Builds a population log-return matrix ending at the final aligned bar.
     *
     * @param barCount number of one-bar log returns
     * @return final log-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getLogReturnMatrix(int barCount) {
        return getLogReturnMatrix(series.getEndIndex(), barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population log-return matrix at an aligned index.
     *
     * @param index    aligned portfolio index
     * @param barCount number of one-bar log returns
     * @return log-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getLogReturnMatrix(int index, int barCount) {
        return getLogReturnMatrix(index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a log-return matrix at an aligned index.
     *
     * @param index      aligned portfolio index
     * @param barCount   number of one-bar log returns
     * @param sampleType sample or population normalization
     * @return log-return correlation matrix
     * @since 0.23.1
     */
    public CorrelationMatrix getLogReturnMatrix(int index, int barCount, SampleType sampleType) {
        return matrix(index, barCount, sampleType, transformedIndicators(LogReturnIndicator::new), 1);
    }

    private CorrelationMatrix matrix(int index, int barCount, SampleType sampleType,
            Map<String, Indicator<Num>> indicatorsByAsset, int sourceUnstableBars) {
        requireIndex(index);
        requireBarCount(barCount);
        SampleType effectiveSampleType = Objects.requireNonNull(sampleType, "sampleType");

        List<String> assets = series.getAssets();
        Map<String, Map<String, Num>> coefficients = new LinkedHashMap<>();
        Num one = series.numFactory().one();

        for (int rowIndex = 0; rowIndex < assets.size(); rowIndex++) {
            String rowAsset = assets.get(rowIndex);
            Map<String, Num> row = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < assets.size(); columnIndex++) {
                String columnAsset = assets.get(columnIndex);
                Num coefficient;
                if (rowIndex == columnIndex) {
                    coefficient = one;
                } else if (columnIndex < rowIndex) {
                    coefficient = coefficients.get(columnAsset).get(rowAsset);
                } else {
                    coefficient = new CorrelationCoefficientIndicator(indicatorsByAsset.get(rowAsset),
                            indicatorsByAsset.get(columnAsset), barCount, effectiveSampleType).getValue(index);
                }
                row.put(columnAsset, coefficient);
            }
            coefficients.put(rowAsset, row);
        }

        return new CorrelationMatrix(assets, index, barCount, sourceUnstableBars + barCount - 1, effectiveSampleType,
                coefficients);
    }

    private Map<String, Indicator<Num>> closePriceIndicators() {
        return transformedIndicators(ClosePriceIndicator::new);
    }

    private Map<String, Indicator<Num>> transformedIndicators(Function<BarSeries, Indicator<Num>> indicatorFactory) {
        Map<String, Indicator<Num>> indicatorsByAsset = new LinkedHashMap<>();
        for (String asset : series.getAssets()) {
            indicatorsByAsset.put(asset, indicatorFactory.apply(alignedCloseSeries(asset)));
        }
        return indicatorsByAsset;
    }

    private BarSeries alignedCloseSeries(String asset) {
        BarSeries closeSeries = new BaseBarSeriesBuilder().withName(asset + "-aligned-close")
                .withNumFactory(series.numFactory())
                .build();
        Num zero = series.numFactory().zero();
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            Bar sourceBar = series.getBar(asset, index);
            Num close = series.getClosePrice(asset, index);
            closeSeries.barBuilder()
                    .timePeriod(sourceBar.getTimePeriod())
                    .endTime(sourceBar.getEndTime())
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return closeSeries;
    }

    private void requireIndex(int index) {
        if (index < series.getBeginIndex() || index > series.getEndIndex()) {
            throw new IndexOutOfBoundsException(
                    "index must be between " + series.getBeginIndex() + " and " + series.getEndIndex());
        }
    }

    private static void requireBarCount(int barCount) {
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be >= 2");
        }
    }

    private static final class SimpleReturnIndicator extends CachedIndicator<Num> implements ReturnIndicator {

        private final Indicator<Num> indicator;

        private SimpleReturnIndicator(Indicator<Num> indicator) {
            super(Objects.requireNonNull(indicator, "indicator"));
            this.indicator = indicator;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return ReturnRepresentation.DECIMAL;
        }

        @Override
        protected Num calculate(int index) {
            if (index < getCountOfUnstableBars()) {
                return NaN.NaN;
            }
            Num current = indicator.getValue(index);
            Num previous = indicator.getValue(index - 1);
            if (!Num.isFinite(current) || !Num.isFinite(previous) || previous.isZero()) {
                return NaN.NaN;
            }
            Num result = current.dividedBy(previous).minus(current.getNumFactory().one());
            return Num.isFinite(result) ? result : NaN.NaN;
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars() + 1;
        }
    }

    /**
     * Immutable correlation matrix for one aligned portfolio index.
     *
     * @since 0.23.1
     */
    public static final class CorrelationMatrix {

        private final List<String> assets;
        private final int index;
        private final int barCount;
        private final int countOfUnstableBars;
        private final SampleType sampleType;
        private final Map<String, Map<String, Num>> values;
        private final List<CorrelationPair> pairs;

        private CorrelationMatrix(List<String> assets, int index, int barCount, int countOfUnstableBars,
                SampleType sampleType, Map<String, Map<String, Num>> values) {
            this.assets = List.copyOf(assets);
            this.index = index;
            this.barCount = barCount;
            this.countOfUnstableBars = countOfUnstableBars;
            this.sampleType = Objects.requireNonNull(sampleType, "sampleType");
            this.values = immutableValues(this.assets, values);
            this.pairs = correlationPairs(this.assets, this.values);
        }

        /**
         * @return assets in deterministic portfolio order
         * @since 0.23.1
         */
        public List<String> getAssets() {
            return assets;
        }

        /**
         * @return aligned portfolio index at which the matrix was evaluated
         * @since 0.23.1
         */
        public int getIndex() {
            return index;
        }

        /**
         * @return observation count in the rolling correlation window
         * @since 0.23.1
         */
        public int getBarCount() {
            return barCount;
        }

        /**
         * @return first index with a full transform and correlation window
         * @since 0.23.1
         */
        public int getCountOfUnstableBars() {
            return countOfUnstableBars;
        }

        /**
         * @return sample or population normalization
         * @since 0.23.1
         */
        public SampleType getSampleType() {
            return sampleType;
        }

        /**
         * @return whether the evaluated index has a full correlation window
         * @since 0.23.1
         */
        public boolean isStable() {
            return index >= countOfUnstableBars;
        }

        /**
         * @return immutable symmetric matrix values
         * @since 0.23.1
         */
        public Map<String, Map<String, Num>> getValues() {
            return immutableValues(assets, values);
        }

        /**
         * Returns the correlation coefficient for two assets.
         *
         * @param firstAsset  row asset
         * @param secondAsset column asset
         * @return correlation coefficient
         * @since 0.23.1
         */
        public Num getCoefficient(String firstAsset, String secondAsset) {
            Objects.requireNonNull(secondAsset, "secondAsset");
            Map<String, Num> row = values.get(Objects.requireNonNull(firstAsset, "firstAsset"));
            if (row == null || !row.containsKey(secondAsset)) {
                String missing = row == null ? firstAsset : secondAsset;
                throw new IllegalArgumentException("asset is not in this correlation matrix: " + missing);
            }
            return row.get(secondAsset);
        }

        /**
         * @return unique off-diagonal pairs in portfolio order
         * @since 0.23.1
         */
        public List<CorrelationPair> getPairs() {
            return Collections.unmodifiableList(new ArrayList<>(pairs));
        }

        /**
         * Performs Euclidean complete-linkage clustering over the matrix rows.
         *
         * <p>
         * This matches {@code scipy.cluster.hierarchy.linkage(matrix,
         * method="complete")} for finite correlation matrices.
         * </p>
         *
         * @return deterministic complete-linkage hierarchy
         * @since 0.23.1
         */
        public CorrelationHierarchy completeLinkage() {
            int assetCount = assets.size();
            Num[][] rows = new Num[assetCount][assetCount];
            for (int rowIndex = 0; rowIndex < assetCount; rowIndex++) {
                for (int columnIndex = 0; columnIndex < assetCount; columnIndex++) {
                    Num value = getCoefficient(assets.get(rowIndex), assets.get(columnIndex));
                    if (!Num.isFinite(value)) {
                        throw new IllegalStateException("complete linkage requires finite correlation coefficients");
                    }
                    rows[rowIndex][columnIndex] = value;
                }
            }

            List<WorkingCluster> clusters = new ArrayList<>(assetCount);
            for (int index = 0; index < assetCount; index++) {
                clusters.add(new WorkingCluster(index, List.of(index)));
            }

            List<ClusterMerge> merges = new ArrayList<>(assetCount - 1);
            while (clusters.size() > 1) {
                ClusterPair closest = closestPair(clusters, rows);
                WorkingCluster left = clusters.get(closest.leftIndex());
                WorkingCluster right = clusters.get(closest.rightIndex());
                List<Integer> mergedMembers = new ArrayList<>(left.members());
                mergedMembers.addAll(right.members());
                int mergedClusterIndex = assetCount + merges.size();
                merges.add(new ClusterMerge(left.clusterIndex(), right.clusterIndex(), closest.distance(),
                        mergedMembers.size()));
                clusters.remove(closest.rightIndex());
                clusters.remove(closest.leftIndex());
                clusters.add(new WorkingCluster(mergedClusterIndex, List.copyOf(mergedMembers)));
            }

            return new CorrelationHierarchy(assets, merges);
        }

        private static ClusterPair closestPair(List<WorkingCluster> clusters, Num[][] rows) {
            ClusterPair closest = null;
            for (int leftIndex = 0; leftIndex < clusters.size(); leftIndex++) {
                for (int rightIndex = leftIndex + 1; rightIndex < clusters.size(); rightIndex++) {
                    Num distance = completeDistance(clusters.get(leftIndex), clusters.get(rightIndex), rows);
                    ClusterPair candidate = new ClusterPair(leftIndex, rightIndex, distance);
                    if (closest == null || candidate.compareTo(closest) < 0) {
                        closest = candidate;
                    }
                }
            }
            return Objects.requireNonNull(closest, "closest");
        }

        private static Num completeDistance(WorkingCluster left, WorkingCluster right, Num[][] rows) {
            Num distance = rows[0][0].getNumFactory().zero();
            for (int leftMember : left.members()) {
                for (int rightMember : right.members()) {
                    distance = distance.max(euclideanDistance(rows[leftMember], rows[rightMember]));
                }
            }
            return distance;
        }

        private static Num euclideanDistance(Num[] left, Num[] right) {
            Num squaredDistance = left[0].getNumFactory().zero();
            for (int index = 0; index < left.length; index++) {
                Num difference = left[index].minus(right[index]);
                squaredDistance = squaredDistance.plus(difference.multipliedBy(difference));
            }
            return squaredDistance.sqrt();
        }

        private static Map<String, Map<String, Num>> immutableValues(List<String> assets,
                Map<String, Map<String, Num>> values) {
            Map<String, Map<String, Num>> matrix = new LinkedHashMap<>();
            for (String rowAsset : assets) {
                Map<String, Num> sourceRow = Objects.requireNonNull(values.get(rowAsset),
                        "values row must not be null");
                Map<String, Num> row = new LinkedHashMap<>();
                for (String columnAsset : assets) {
                    row.put(columnAsset, Objects.requireNonNull(sourceRow.get(columnAsset),
                            "correlation coefficient must not be null"));
                }
                matrix.put(rowAsset, Collections.unmodifiableMap(row));
            }
            return Collections.unmodifiableMap(matrix);
        }

        private static List<CorrelationPair> correlationPairs(List<String> assets,
                Map<String, Map<String, Num>> values) {
            List<CorrelationPair> pairs = new ArrayList<>();
            for (int firstIndex = 0; firstIndex < assets.size(); firstIndex++) {
                String firstAsset = assets.get(firstIndex);
                for (int secondIndex = firstIndex + 1; secondIndex < assets.size(); secondIndex++) {
                    String secondAsset = assets.get(secondIndex);
                    pairs.add(new CorrelationPair(firstAsset, secondAsset, values.get(firstAsset).get(secondAsset)));
                }
            }
            return List.copyOf(pairs);
        }
    }

    /**
     * Immutable complete-linkage hierarchy for a correlation matrix.
     *
     * @since 0.23.1
     */
    public static final class CorrelationHierarchy {

        private final List<String> assets;
        private final List<ClusterMerge> merges;
        private final List<String> leafOrder;

        private CorrelationHierarchy(List<String> assets, List<ClusterMerge> merges) {
            this.assets = List.copyOf(assets);
            this.merges = List.copyOf(merges);
            List<String> orderedLeaves = new ArrayList<>(assets.size());
            appendLeaves(getRootClusterIndex(), orderedLeaves);
            this.leafOrder = List.copyOf(orderedLeaves);
        }

        /**
         * @return assets in original matrix order
         * @since 0.23.1
         */
        public List<String> getAssets() {
            return assets;
        }

        /**
         * @return linkage merges in construction order
         * @since 0.23.1
         */
        public List<ClusterMerge> getMerges() {
            return merges;
        }

        /**
         * @return assets in deterministic dendrogram leaf order
         * @since 0.23.1
         */
        public List<String> getLeafOrder() {
            return leafOrder;
        }

        /**
         * @return root cluster index
         * @since 0.23.1
         */
        public int getRootClusterIndex() {
            return assets.size() + merges.size() - 1;
        }

        private void appendLeaves(int clusterIndex, List<String> orderedLeaves) {
            if (clusterIndex < assets.size()) {
                orderedLeaves.add(assets.get(clusterIndex));
                return;
            }
            ClusterMerge merge = merges.get(clusterIndex - assets.size());
            appendLeaves(merge.getLeftClusterIndex(), orderedLeaves);
            appendLeaves(merge.getRightClusterIndex(), orderedLeaves);
        }
    }

    /**
     * One complete-linkage hierarchy merge.
     *
     * @since 0.23.1
     */
    public static final class ClusterMerge {

        private final int leftClusterIndex;
        private final int rightClusterIndex;
        private final Num distance;
        private final int size;

        private ClusterMerge(int leftClusterIndex, int rightClusterIndex, Num distance, int size) {
            this.leftClusterIndex = leftClusterIndex;
            this.rightClusterIndex = rightClusterIndex;
            this.distance = Objects.requireNonNull(distance, "distance");
            this.size = size;
        }

        /**
         * @return left child cluster index
         * @since 0.23.1
         */
        public int getLeftClusterIndex() {
            return leftClusterIndex;
        }

        /**
         * @return right child cluster index
         * @since 0.23.1
         */
        public int getRightClusterIndex() {
            return rightClusterIndex;
        }

        /**
         * @return complete-linkage distance
         * @since 0.23.1
         */
        public Num getDistance() {
            return distance;
        }

        /**
         * @return number of leaves in the merged cluster
         * @since 0.23.1
         */
        public int getSize() {
            return size;
        }
    }

    /**
     * One unique off-diagonal portfolio correlation.
     *
     * @since 0.23.1
     */
    public static final class CorrelationPair {

        private final String firstAsset;
        private final String secondAsset;
        private final Num coefficient;

        private CorrelationPair(String firstAsset, String secondAsset, Num coefficient) {
            this.firstAsset = Objects.requireNonNull(firstAsset, "firstAsset");
            this.secondAsset = Objects.requireNonNull(secondAsset, "secondAsset");
            this.coefficient = Objects.requireNonNull(coefficient, "coefficient");
        }

        /**
         * @return first asset
         * @since 0.23.1
         */
        public String getFirstAsset() {
            return firstAsset;
        }

        /**
         * @return second asset
         * @since 0.23.1
         */
        public String getSecondAsset() {
            return secondAsset;
        }

        /**
         * @return correlation coefficient
         * @since 0.23.1
         */
        public Num getCoefficient() {
            return coefficient;
        }

        /**
         * @return absolute coefficient magnitude
         * @since 0.23.1
         */
        public Num getAbsoluteCoefficient() {
            return coefficient.abs();
        }
    }

    private record WorkingCluster(int clusterIndex, List<Integer> members) {
    }

    private record ClusterPair(int leftIndex, int rightIndex, Num distance) implements Comparable<ClusterPair> {

        @Override
        public int compareTo(ClusterPair other) {
            return Comparator.comparing(ClusterPair::distance)
                    .thenComparingInt(ClusterPair::leftIndex)
                    .thenComparingInt(ClusterPair::rightIndex)
                    .compare(this, other);
        }
    }
}
