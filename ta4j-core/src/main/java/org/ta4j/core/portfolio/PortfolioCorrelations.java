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
 * Portfolio-level correlation analytics for aligned asset universes.
 *
 * <p>
 * The utility turns each asset in an {@link AlignedPortfolioSeries} into an
 * aligned close-price series and delegates rolling correlation calculations to
 * ta4j's existing {@link CorrelationCoefficientIndicator}. Callers can
 * correlate close prices directly, one-bar simple returns equivalent to pandas
 * {@code pct_change()}, or one-bar log returns while keeping the same strict
 * common-end-time timeline as portfolio execution.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioCorrelations {

    private PortfolioCorrelations() {
    }

    /**
     * Builds a population close-price correlation matrix ending at the final
     * aligned bar and using the full aligned history.
     *
     * @param series aligned portfolio series
     * @return close-price correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix priceMatrix(AlignedPortfolioSeries series) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return priceMatrix(portfolioSeries, portfolioSeries.getBarCount());
    }

    /**
     * Builds a population close-price correlation matrix ending at the final
     * aligned bar.
     *
     * @param series   aligned portfolio series
     * @param barCount number of close-price observations in the rolling correlation
     *                 window
     * @return close-price correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix priceMatrix(AlignedPortfolioSeries series, int barCount) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return priceMatrix(portfolioSeries, portfolioSeries.getBarCount() - 1, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population close-price correlation matrix ending at a specific
     * aligned index.
     *
     * @param series   aligned portfolio series
     * @param index    aligned portfolio index at which to evaluate the matrix
     * @param barCount number of close-price observations in the rolling correlation
     *                 window
     * @return close-price correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix priceMatrix(AlignedPortfolioSeries series, int index, int barCount) {
        return priceMatrix(series, index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a close-price correlation matrix ending at a specific aligned index.
     *
     * @param series     aligned portfolio series
     * @param index      aligned portfolio index at which to evaluate the matrix
     * @param barCount   number of close-price observations in the rolling
     *                   correlation window
     * @param sampleType sample/population normalization used by the underlying
     *                   rolling statistic
     * @return close-price correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix priceMatrix(AlignedPortfolioSeries series, int index, int barCount,
            SampleType sampleType) {
        return matrix(series, index, barCount, sampleType,
                closePriceIndicators(Objects.requireNonNull(series, "series")), 0);
    }

    /**
     * Builds a population simple-return correlation matrix ending at the final
     * aligned bar and using every available one-bar simple return.
     *
     * <p>
     * Simple returns are decimal returns: {@code close[i] / close[i - 1] - 1},
     * matching pandas {@code pct_change()} rather than percentage points.
     * </p>
     *
     * @param series aligned portfolio series
     * @return simple-return correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix simpleReturnMatrix(AlignedPortfolioSeries series) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return simpleReturnMatrix(portfolioSeries, portfolioSeries.getBarCount() - 1);
    }

    /**
     * Builds a population simple-return correlation matrix ending at the final
     * aligned bar.
     *
     * @param series   aligned portfolio series
     * @param barCount number of one-bar simple returns in the rolling correlation
     *                 window
     * @return simple-return correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix simpleReturnMatrix(AlignedPortfolioSeries series, int barCount) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return simpleReturnMatrix(portfolioSeries, portfolioSeries.getBarCount() - 1, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a population simple-return correlation matrix ending at a specific
     * aligned index.
     *
     * @param series   aligned portfolio series
     * @param index    aligned portfolio index at which to evaluate the matrix
     * @param barCount number of one-bar simple returns in the rolling correlation
     *                 window
     * @return simple-return correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix simpleReturnMatrix(AlignedPortfolioSeries series, int index, int barCount) {
        return simpleReturnMatrix(series, index, barCount, SampleType.POPULATION);
    }

    /**
     * Builds a simple-return correlation matrix ending at a specific aligned index.
     *
     * @param series     aligned portfolio series
     * @param index      aligned portfolio index at which to evaluate the matrix
     * @param barCount   number of one-bar simple returns in the rolling correlation
     *                   window
     * @param sampleType sample/population normalization used by the underlying
     *                   rolling statistic
     * @return simple-return correlation matrix for the requested aligned index
     * @since 0.23.1
     */
    public static CorrelationMatrix simpleReturnMatrix(AlignedPortfolioSeries series, int index, int barCount,
            SampleType sampleType) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return matrix(portfolioSeries, index, barCount, sampleType, transformedIndicators(portfolioSeries,
                alignedClose -> new SimpleReturnIndicator(new ClosePriceIndicator(alignedClose))), 1);
    }

    /**
     * Builds a population log-return correlation matrix ending at the final aligned
     * bar and using every available one-bar log return.
     *
     * @param series aligned portfolio series
     * @return log-return correlation matrix for the final aligned bar
     * @since 0.23.1
     */
    public static CorrelationMatrix logReturnMatrix(AlignedPortfolioSeries series) {
        AlignedPortfolioSeries portfolioSeries = Objects.requireNonNull(series, "series");
        return logReturnMatrix(portfolioSeries, portfolioSeries.getBarCount() - 1);
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
        return matrix(portfolioSeries, index, barCount, sampleType,
                transformedIndicators(portfolioSeries, LogReturnIndicator::new), 1);
    }

    private static CorrelationMatrix matrix(AlignedPortfolioSeries series, int index, int barCount,
            SampleType sampleType, Map<PortfolioAsset, Indicator<Num>> indicatorsByAsset, int sourceUnstableBars) {
        requireIndex(series, index);
        requireBarCount(barCount);
        SampleType effectiveSampleType = Objects.requireNonNull(sampleType, "sampleType");

        List<PortfolioAsset> assets = series.assets();
        Map<PortfolioAsset, Map<PortfolioAsset, Num>> coefficients = new LinkedHashMap<>();
        Num one = series.numFactory().one();

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

    private static Map<PortfolioAsset, Indicator<Num>> closePriceIndicators(AlignedPortfolioSeries series) {
        return transformedIndicators(series, ClosePriceIndicator::new);
    }

    private static Map<PortfolioAsset, Indicator<Num>> transformedIndicators(AlignedPortfolioSeries series,
            Function<BarSeries, Indicator<Num>> indicatorFactory) {
        Map<PortfolioAsset, Indicator<Num>> indicatorsByAsset = new LinkedHashMap<>();
        for (PortfolioAsset asset : series.assets()) {
            indicatorsByAsset.put(asset, indicatorFactory.apply(alignedCloseSeries(series, asset)));
        }
        return indicatorsByAsset;
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
            if (!Num.isFinite(result)) {
                return NaN.NaN;
            }
            return result;
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

        private final List<PortfolioAsset> assets;
        private final int index;
        private final int barCount;
        private final int countOfUnstableBars;
        private final SampleType sampleType;
        private final Map<PortfolioAsset, Map<PortfolioAsset, Num>> values;
        private final List<CorrelationPair> pairs;

        private CorrelationMatrix(List<PortfolioAsset> assets, int index, int barCount, int countOfUnstableBars,
                SampleType sampleType, Map<PortfolioAsset, Map<PortfolioAsset, Num>> values) {
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
         * @return number of observations in the rolling correlation window
         * @since 0.23.1
         */
        public int barCount() {
            return barCount;
        }

        /**
         * Returns the first aligned index at which off-diagonal coefficients have a
         * full source-transformation and rolling-correlation window.
         *
         * @return count of unstable matrix bars
         * @since 0.23.1
         */
        public int getCountOfUnstableBars() {
            return countOfUnstableBars;
        }

        /**
         * @return sample/population normalization used for the matrix
         * @since 0.23.1
         */
        public SampleType sampleType() {
            return sampleType;
        }

        /**
         * Returns whether the evaluated index has a full source-transformation and
         * rolling-correlation window. Off-diagonal values before this threshold may be
         * {@code NaN}.
         *
         * @return {@code true} when the matrix is beyond its warm-up range
         * @since 0.23.1
         */
        public boolean isStable() {
            return index >= countOfUnstableBars;
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
