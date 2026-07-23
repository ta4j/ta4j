/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.Num;

/**
 * Portfolio-level correlation analytics for aligned asset universes.
 *
 * <p>
 * The utility turns each asset in an {@link AlignedPortfolioSeries} into an
 * aligned close-price series, derives one-bar log returns, and delegates the
 * rolling correlation calculation to ta4j's existing
 * {@link CorrelationCoefficientIndicator}. Correlations therefore use the same
 * strict common-end-time timeline as portfolio execution instead of relying on
 * raw source indexes that may not line up across assets.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioCorrelations {

    private PortfolioCorrelations() {
    }

    /**
     * Builds a population log-return correlation matrix ending at the final aligned
     * bar.
     *
     * @param series   aligned portfolio series
     * @param barCount number of one-bar log returns in the rolling correlation
     *                 window
     * @return correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix logReturnMatrix(AlignedPortfolioSeries series, int barCount) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return logReturnMatrix(portfolioSeries, portfolioSeries.getBarCount() - 1, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population log-return correlation matrix ending at a specific
     * aligned index.
     *
     * @param series   aligned portfolio series
     * @param index    aligned portfolio index at which to evaluate the matrix
     * @param barCount number of one-bar log returns in the rolling correlation
     *                 window
     * @return correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix logReturnMatrix(AlignedPortfolioSeries series, int index, int barCount) {
        return logReturnMatrix(series, index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a log-return correlation matrix ending at a specific aligned index.
     *
     * @param series     aligned portfolio series
     * @param index      aligned portfolio index at which to evaluate the matrix
     * @param barCount   number of one-bar log returns in the rolling correlation
     *                   window
     * @param sampleType sample/population normalization used by the underlying
     *                   rolling statistic
     * @return correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix logReturnMatrix(AlignedPortfolioSeries series, int index, int barCount,
            SampleType sampleType) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        requireIndex(portfolioSeries, index);
        requireBarCount(barCount);
        SampleType effectiveSampleType = Objects.requireNonNull(sampleType, "sampleType");

        List<PortfolioAsset> assets = portfolioSeries.assets();
        Map<PortfolioAsset, Indicator<Num>> returnsByAsset = logReturnsByAsset(portfolioSeries, assets);
        Map<PortfolioAsset, Map<PortfolioAsset, Num>> coefficients = new LinkedHashMap<>();
        Num one = portfolioSeries.numFactory().one();

        for (int rowIndex = 0; rowIndex < assets.size(); rowIndex++) {
            PortfolioAsset rowAsset = assets.get(rowIndex);
            Map<PortfolioAsset, Num> row = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < assets.size(); columnIndex++) {
                PortfolioAsset columnAsset = assets.get(columnIndex);
                Num coefficient;
                if (rowIndex == columnIndex) {
                    coefficient = one;
                } else if (columnIndex < rowIndex) {
                    coefficient = coefficients.get(columnAsset).get(rowAsset);
                } else {
                    coefficient = new CorrelationCoefficientIndicator(returnsByAsset.get(rowAsset),
                            returnsByAsset.get(columnAsset), barCount, effectiveSampleType).getValue(index);
                }
                row.put(columnAsset, coefficient);
            }
            coefficients.put(rowAsset, row);
        }

        return new CorrelationMatrix(assets, index, barCount, effectiveSampleType, coefficients);
    }

    private static Map<PortfolioAsset, Indicator<Num>> logReturnsByAsset(AlignedPortfolioSeries series,
            List<PortfolioAsset> assets) {
        Map<PortfolioAsset, Indicator<Num>> returnsByAsset = new LinkedHashMap<>();
        for (PortfolioAsset asset : assets) {
            returnsByAsset.put(asset, new LogReturnIndicator(alignedCloseSeries(series, asset)));
        }
        return returnsByAsset;
    }

    private static BarSeries alignedCloseSeries(AlignedPortfolioSeries series, PortfolioAsset asset) {
        BarSeries closeSeries = new BaseBarSeriesBuilder().withName(asset.id() + "-aligned-close")
                .withNumFactory(series.numFactory())
                .build();
        Num zero = series.numFactory().zero();
        for (int index = 0; index < series.getBarCount(); index++) {
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

    private static void requireIndex(AlignedPortfolioSeries series, int index) {
        if (index < 0 || index >= series.getBarCount()) {
            throw new IndexOutOfBoundsException("index must be between 0 and " + (series.getBarCount() - 1));
        }
    }

    private static void requireBarCount(int barCount) {
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be >= 2");
        }
    }

    /**
     * Immutable correlation matrix for one aligned portfolio index.
     *
     * @since 0.23.1
     */
    public static final class CorrelationMatrix {

        private final List<PortfolioAsset> assets;
        private final int index;
        private final int barCount;
        private final SampleType sampleType;
        private final Map<PortfolioAsset, Map<PortfolioAsset, Num>> values;
        private final List<CorrelationPair> pairs;

        private CorrelationMatrix(List<PortfolioAsset> assets, int index, int barCount, SampleType sampleType,
                Map<PortfolioAsset, Map<PortfolioAsset, Num>> values) {
            this.assets = List.copyOf(assets);
            this.index = index;
            this.barCount = barCount;
            this.sampleType = Objects.requireNonNull(sampleType, "sampleType");
            this.values = immutableValues(this.assets, values);
            this.pairs = correlationPairs(this.assets, this.values);
        }

        /**
         * @return assets in deterministic portfolio order
         * @since 0.23.1
         */
        public List<PortfolioAsset> assets() {
            return assets;
        }

        /**
         * @return aligned portfolio index at which the matrix was evaluated
         * @since 0.23.1
         */
        public int index() {
            return index;
        }

        /**
         * @return number of one-bar log returns in the rolling correlation window
         * @since 0.23.1
         */
        public int barCount() {
            return barCount;
        }

        /**
         * @return sample/population normalization used for the matrix
         * @since 0.23.1
         */
        public SampleType sampleType() {
            return sampleType;
        }

        /**
         * Returns whether the evaluated index has a full log-return correlation window.
         * Off-diagonal values before this threshold may be {@code NaN}.
         *
         * @return {@code true} when the matrix is beyond its warm-up range
         * @since 0.23.1
         */
        public boolean isStable() {
            return index >= barCount;
        }

        /**
         * @return immutable symmetric matrix values keyed by row asset, then column
         *         asset
         * @since 0.23.1
         */
        public Map<PortfolioAsset, Map<PortfolioAsset, Num>> values() {
            return immutableValues(assets, values);
        }

        /**
         * Returns the correlation coefficient for two assets.
         *
         * @param firstAsset  row asset
         * @param secondAsset column asset
         * @return correlation coefficient; the diagonal is {@code 1} by convention
         * @since 0.23.1
         */
        public Num coefficient(PortfolioAsset firstAsset, PortfolioAsset secondAsset) {
            Objects.requireNonNull(secondAsset, "secondAsset");
            Map<PortfolioAsset, Num> row = row(firstAsset);
            Num coefficient = row.get(secondAsset);
            if (coefficient == null) {
                throw new IllegalArgumentException("asset is not in this correlation matrix: " + secondAsset);
            }
            return coefficient;
        }

        /**
         * @return immutable unique off-diagonal pairs in deterministic portfolio order
         * @since 0.23.1
         */
        public List<CorrelationPair> pairs() {
            return List.copyOf(pairs);
        }

        private Map<PortfolioAsset, Num> row(PortfolioAsset asset) {
            Objects.requireNonNull(asset, "firstAsset");
            Map<PortfolioAsset, Num> row = values.get(asset);
            if (row == null) {
                throw new IllegalArgumentException("asset is not in this correlation matrix: " + asset);
            }
            return row;
        }

        private static Map<PortfolioAsset, Map<PortfolioAsset, Num>> immutableValues(List<PortfolioAsset> assets,
                Map<PortfolioAsset, Map<PortfolioAsset, Num>> values) {
            Map<PortfolioAsset, Map<PortfolioAsset, Num>> matrix = new LinkedHashMap<>();
            for (PortfolioAsset rowAsset : assets) {
                Map<PortfolioAsset, Num> sourceRow = Objects.requireNonNull(values.get(rowAsset),
                        "values row must not be null");
                Map<PortfolioAsset, Num> row = new LinkedHashMap<>();
                for (PortfolioAsset columnAsset : assets) {
                    row.put(columnAsset, Objects.requireNonNull(sourceRow.get(columnAsset),
                            "correlation coefficient must not be null"));
                }
                matrix.put(rowAsset, Collections.unmodifiableMap(row));
            }
            return Collections.unmodifiableMap(matrix);
        }

        private static List<CorrelationPair> correlationPairs(List<PortfolioAsset> assets,
                Map<PortfolioAsset, Map<PortfolioAsset, Num>> values) {
            List<CorrelationPair> pairs = new ArrayList<>();
            for (int firstIndex = 0; firstIndex < assets.size(); firstIndex++) {
                PortfolioAsset firstAsset = assets.get(firstIndex);
                for (int secondIndex = firstIndex + 1; secondIndex < assets.size(); secondIndex++) {
                    PortfolioAsset secondAsset = assets.get(secondIndex);
                    pairs.add(new CorrelationPair(firstAsset, secondAsset, values.get(firstAsset).get(secondAsset)));
                }
            }
            return List.copyOf(pairs);
        }
    }

    /**
     * One unique off-diagonal portfolio correlation.
     *
     * @param firstAsset  first asset in portfolio order
     * @param secondAsset second asset in portfolio order
     * @param coefficient correlation coefficient for the pair
     * @since 0.23.1
     */
    public record CorrelationPair(PortfolioAsset firstAsset, PortfolioAsset secondAsset, Num coefficient) {

        /**
         * Creates a correlation pair.
         *
         * @param firstAsset  first asset in portfolio order
         * @param secondAsset second asset in portfolio order
         * @param coefficient correlation coefficient for the pair
         * @since 0.23.1
         */
        public CorrelationPair {
            Objects.requireNonNull(firstAsset, "firstAsset");
            Objects.requireNonNull(secondAsset, "secondAsset");
            Objects.requireNonNull(coefficient, "coefficient");
            if (firstAsset.equals(secondAsset)) {
                throw new IllegalArgumentException("correlation pairs must contain two different assets");
            }
        }

        /**
         * @return absolute correlation coefficient magnitude
         * @since 0.23.1
         */
        public Num absoluteCoefficient() {
            return coefficient.abs();
        }
    }
}
