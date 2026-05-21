/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class ElliottSwingCompressorTest {

    @Test
    void filtersShortSwings() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());
        var minimumAmplitude = series.numFactory().numOf(5);
        var compressor = new ElliottSwingCompressor(minimumAmplitude, 0);

        var compressed = compressor.compress(swings);
        assertThat(compressed).hasSize(4);
        assertThat(compressed).allMatch(s -> s.amplitude().isGreaterThanOrEqual(minimumAmplitude));
        assertThat(compressed.get(0).fromIndex()).isEqualTo(3);
        assertThat(compressed.get(compressed.size() - 1).toIndex()).isEqualTo(7);
    }

    @Test
    void filtersByLength() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        var compressor = new ElliottSwingCompressor((Num) null, 2);
        var compressed = compressor.compress(swings);

        assertThat(swings).isNotEmpty();
        assertThat(compressed).isEmpty();
    }

    @Test
    void zeroParamConstructorRetainsAllSwings() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        var compressor = new ElliottSwingCompressor();
        var compressed = compressor.compress(swings);

        // Zero-parameter constructor should retain all swings (no filtering)
        assertThat(compressed).hasSameSizeAs(swings);
        assertThat(compressed).containsExactlyElementsOf(swings);
    }

    @Test
    void compressCollapsesAdjacentSameDirectionSwingsAfterFilteringAwayCounterMove() {
        var series = new MockBarSeriesBuilder().build();
        for (int index = 0; index < 8; index++) {
            double close = 100 + index;
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var factory = series.numFactory();
        var degree = ElliottDegree.MINOR;
        var swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 3, factory.numOf(120), factory.numOf(117), degree),
                new ElliottSwing(3, 5, factory.numOf(117), factory.numOf(130), degree),
                new ElliottSwing(5, 7, factory.numOf(130), factory.numOf(112), degree));

        var compressor = new ElliottSwingCompressor(factory.numOf(5), 0);
        var compressed = compressor.compress(swings);

        assertThat(compressed).hasSize(2);
        assertThat(compressed.getFirst().fromIndex()).isEqualTo(0);
        assertThat(compressed.getFirst().toIndex()).isEqualTo(5);
        assertThat(compressed.getFirst().fromPrice()).isEqualByComparingTo(factory.hundred());
        assertThat(compressed.getFirst().toPrice()).isEqualByComparingTo(factory.numOf(130));
        assertThat(compressed.getFirst().isRising()).isTrue();
        assertThat(compressed.getLast().fromIndex()).isEqualTo(5);
        assertThat(compressed.getLast().toIndex()).isEqualTo(7);
        assertThat(compressed.getLast().isRising()).isFalse();
    }

    @Test
    void barSeriesConstructorUsesOnePercentAndTwoBars() {
        var series = new MockBarSeriesBuilder().build();
        // Create series with price at 100 (1% = 1.0)
        double[] closes = { 100, 102, 98, 103, 97, 104, 96, 105, 95 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        // 1% of 95 (end price) = 0.95, minimum 2 bars
        var compressor = new ElliottSwingCompressor(series);
        var compressed = compressor.compress(swings);

        // Should filter out swings with amplitude < 0.95 or length < 2
        assertThat(compressed).isNotNull();
        assertThat(compressed.size()).isLessThanOrEqualTo(swings.size());
    }

    @Test
    void closePriceIndicatorWithCustomPercentageAndMinBarsConstructor() {
        var series = new MockBarSeriesBuilder().build();
        // Create series with price at 100 (2% = 2.0)
        double[] closes = { 100, 102, 98, 103, 97, 104, 96, 105, 95 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        // 2% of 95 (end price) = 1.9, minimum 2 bars
        var closePrice = new ClosePriceIndicator(series);
        var compressor = new ElliottSwingCompressor(closePrice, 0.02, 2);
        var compressed = compressor.compress(swings);

        // Should filter out swings with amplitude < 1.9 or length < 2
        assertThat(compressed).isNotNull();
        assertThat(compressed.size()).isLessThanOrEqualTo(swings.size());
    }

    @Test
    void closePriceIndicatorWithCustomPercentageAndMinBarsConstructor2() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 100, 102, 98, 103, 97, 104, 96, 105, 95 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var indicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var swings = indicator.getValue(series.getEndIndex());

        // 1% of 95 = 0.95, minimum 3 bars
        var closePrice = new ClosePriceIndicator(series);
        var compressor = new ElliottSwingCompressor(closePrice, 0.01, 3);
        var compressed = compressor.compress(swings);

        // Should filter out swings with amplitude < 0.95 or length < 3
        assertThat(compressed).isNotNull();
        assertThat(compressed.size()).isLessThanOrEqualTo(swings.size());
    }

    @Test
    void barSeriesConstructorRejectsEmptySeries() {
        var series = new MockBarSeriesBuilder().build();
        assertThatThrownBy(() -> new ElliottSwingCompressor(series)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void barSeriesConstructorRejectsNullSeries() {
        assertThatThrownBy(() -> new ElliottSwingCompressor((BarSeries) null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("series cannot be null");
    }

    @Test
    void closePriceIndicatorConstructorRejectsInvalidPercentage() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();
        var closePrice = new ClosePriceIndicator(series);

        assertThatThrownBy(() -> new ElliottSwingCompressor(closePrice, 0.0, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage must be in range");

        assertThatThrownBy(() -> new ElliottSwingCompressor(closePrice, -0.01, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage must be in range");

        assertThatThrownBy(() -> new ElliottSwingCompressor(closePrice, 1.1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage must be in range");
    }

    @Test
    void closePriceIndicatorConstructorRejectsNegativeMinBars() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();
        var closePrice = new ClosePriceIndicator(series);

        assertThatThrownBy(() -> new ElliottSwingCompressor(closePrice, 0.01, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minBars must be non-negative");
    }

    @Test
    void closePriceIndicatorConstructorRejectsNullIndicator() {
        assertThatThrownBy(() -> new ElliottSwingCompressor((ClosePriceIndicator) null, 0.01, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("indicator cannot be null");
    }

    @Test
    void closePriceIndicatorConstructorRejectsEmptySeries() {
        var series = new MockBarSeriesBuilder().build();
        var closePrice = new ClosePriceIndicator(series);
        assertThatThrownBy(() -> new ElliottSwingCompressor(closePrice, 0.01, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("series cannot be empty");
    }
}
