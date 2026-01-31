/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.Indicator;
import org.ta4j.core.BarSeries;

class ElliottSwingIndicatorTest {

    @Test
    void detectsSwingsWithoutLookahead() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);

        assertThat(indicator.getValue(2)).isEmpty();
        assertThat(indicator.getValue(3)).hasSize(1);

        var swings = indicator.getValue(series.getEndIndex());
        assertThat(swings).hasSize(6);

        var first = swings.get(0);
        assertThat(first.fromIndex()).isEqualTo(1);
        assertThat(first.toIndex()).isEqualTo(2);
        assertThat(first.fromPrice()).isEqualByComparingTo(series.getBar(1).getClosePrice());
        assertThat(first.toPrice()).isEqualByComparingTo(series.getBar(2).getClosePrice());
        assertThat(first.degree()).isEqualTo(ElliottDegree.MINOR);

        var last = swings.get(swings.size() - 1);
        assertThat(last.fromIndex()).isEqualTo(6);
        assertThat(last.toIndex()).isEqualTo(7);
        assertThat(last.isRising()).isTrue();
    }

    @Test
    void skipsNanBars() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(0).add();
        series.barBuilder().openPrice(NaN.NaN).highPrice(NaN.NaN).lowPrice(NaN.NaN).closePrice(NaN.NaN).volume(0).add();
        series.barBuilder().openPrice(12).highPrice(12).lowPrice(12).closePrice(12).volume(0).add();
        series.barBuilder().openPrice(8).highPrice(8).lowPrice(8).closePrice(8).volume(0).add();
        series.barBuilder().openPrice(14).highPrice(14).lowPrice(14).closePrice(14).volume(0).add();
        series.barBuilder().openPrice(7).highPrice(7).lowPrice(7).closePrice(7).volume(0).add();

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        assertThat(swings).hasSize(1);
        var swing = swings.get(0);
        assertThat(swing.fromIndex()).isEqualTo(3);
        assertThat(swing.toIndex()).isEqualTo(4);
        assertThat(swing.amplitude()).isEqualByComparingTo(series.numFactory().numOf(6));
    }

    @Test
    void skipsDoubleNaNBarsFromDoubleFactory() {
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(0).add();
        series.barBuilder()
                .openPrice(Double.NaN)
                .highPrice(Double.NaN)
                .lowPrice(Double.NaN)
                .closePrice(Double.NaN)
                .volume(0)
                .add();
        series.barBuilder().openPrice(12).highPrice(12).lowPrice(12).closePrice(12).volume(0).add();
        series.barBuilder().openPrice(8).highPrice(8).lowPrice(8).closePrice(8).volume(0).add();
        series.barBuilder().openPrice(14).highPrice(14).lowPrice(14).closePrice(14).volume(0).add();
        series.barBuilder().openPrice(7).highPrice(7).lowPrice(7).closePrice(7).volume(0).add();

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        assertThat(swings).hasSize(1);
        var swing = swings.get(0);
        assertThat(swing.fromIndex()).isEqualTo(3);
        assertThat(swing.toIndex()).isEqualTo(4);
        assertThat(swing.amplitude()).isEqualByComparingTo(series.numFactory().numOf(6));
    }

    @Test
    void respectsSuppliedIndicatorValues() {
        var series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 6; i++) {
            series.barBuilder().openPrice(0).highPrice(0).lowPrice(0).closePrice(0).volume(0).add();
        }

        var numFactory = series.numFactory();
        var source = new FixedIndicator<>(series, numFactory.numOf(1), numFactory.numOf(3), numFactory.numOf(1),
                numFactory.numOf(4), numFactory.numOf(1), numFactory.numOf(5));
        var indicator = new ElliottSwingIndicator(source, 1, ElliottDegree.MINOR);

        var swings = indicator.getValue(series.getEndIndex());

        assertThat(swings).hasSize(3);
        assertThat(swings.get(0).fromPrice()).isEqualByComparingTo(numFactory.numOf(3));
        assertThat(swings.get(0).toPrice()).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(swings.get(2).toPrice()).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(swings).allMatch(s -> s.degree() == ElliottDegree.MINOR);
    }

    @Test
    void handlesPlateausSymmetrically() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 12, 9, 9, 13, 13, 8, 8, 14 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        assertThat(swings).hasSize(3);
        assertThat(swings.get(0).fromIndex()).isEqualTo(2);
        assertThat(swings.get(0).toIndex()).isEqualTo(4);
        assertThat(swings.get(1).fromIndex()).isEqualTo(4);
        assertThat(swings.get(1).toIndex()).isEqualTo(6);
        assertThat(swings.get(2).toIndex()).isEqualTo(8);
    }

    @Test
    void supportsZigZagSwingSources() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var price = new ClosePriceIndicator(series);
        var stateIndicator = new ZigZagStateIndicator(price, 1);
        var indicator = ElliottSwingIndicator.zigZag(stateIndicator, price, ElliottDegree.MINOR);

        assertThat(indicator.getValue(series.getEndIndex())).hasSize(6);
        assertThat(indicator.getPivotIndexes(series.getEndIndex())).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void selectsPivotWhenHighAndLowIndexesCoincideInitially() {
        var series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 5; i++) {
            series.barBuilder().openPrice(0).highPrice(0).lowPrice(0).closePrice(0).volume(0).add();
        }

        var factory = series.numFactory();
        var highPrice = new FixedIndicator<>(series, factory.zero(), factory.zero(), factory.numOf(12), factory.zero(),
                factory.zero());
        var lowPrice = new FixedIndicator<>(series, factory.zero(), factory.zero(), factory.numOf(10), factory.zero(),
                factory.numOf(8));

        var swingHigh = new FixedRecentSwingIndicator(series, highPrice, List.of(2));
        var swingLow = new FixedRecentSwingIndicator(series, lowPrice, List.of(2, 4));
        var indicator = new ElliottSwingIndicator(swingHigh, swingLow, ElliottDegree.MINOR);

        assertThat(indicator.getPivotIndexes(series.getEndIndex())).containsExactly(2, 4);

        var swings = indicator.getValue(series.getEndIndex());
        assertThat(swings).hasSize(1);
        assertThat(swings.get(0).fromIndex()).isEqualTo(2);
        assertThat(swings.get(0).toIndex()).isEqualTo(4);
        assertThat(swings.get(0).fromPrice()).isEqualByComparingTo(factory.numOf(12));
        assertThat(swings.get(0).toPrice()).isEqualByComparingTo(factory.numOf(8));
    }

    private static final class FixedRecentSwingIndicator implements RecentSwingIndicator {

        private final BarSeries series;
        private final Indicator<Num> priceIndicator;
        private final List<Integer> swingIndexes;

        private FixedRecentSwingIndicator(final BarSeries series, final Indicator<Num> priceIndicator,
                final List<Integer> swingIndexes) {
            this.series = series;
            this.priceIndicator = priceIndicator;
            this.swingIndexes = swingIndexes;
        }

        @Override
        public int getLatestSwingIndex(final int index) {
            int latest = -1;
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    latest = swingIndex;
                } else {
                    break;
                }
            }
            return latest;
        }

        @Override
        public List<Integer> getSwingPointIndexesUpTo(final int index) {
            final var filtered = new java.util.ArrayList<Integer>(swingIndexes.size());
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    filtered.add(swingIndex);
                } else {
                    break;
                }
            }
            return List.copyOf(filtered);
        }

        @Override
        public Indicator<Num> getPriceIndicator() {
            return priceIndicator;
        }

        @Override
        public Num getValue(final int index) {
            final int swingIndex = getLatestSwingIndex(index);
            return swingIndex < 0 ? NaN.NaN : priceIndicator.getValue(swingIndex);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}
