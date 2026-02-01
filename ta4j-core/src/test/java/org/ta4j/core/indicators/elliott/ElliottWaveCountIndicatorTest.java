/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ElliottWaveCountIndicatorTest {

    @Test
    void countsRawSwings() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var waveCount = new ElliottWaveCountIndicator(swingIndicator);

        assertThat(waveCount.getValue(series.getEndIndex())).isEqualTo(6);
        assertThat(waveCount.getSwings(series.getEndIndex())).hasSize(6);
    }

    @Test
    void countsCompressedSwings() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var compressor = new ElliottSwingCompressor(series.numFactory().numOf(6), 0);
        var waveCount = new ElliottWaveCountIndicator(swingIndicator, compressor);

        assertThat(waveCount.getValue(series.getEndIndex())).isEqualTo(3);
        assertThat(waveCount.getSwings(series.getEndIndex()))
                .allMatch(s -> s.amplitude().isGreaterThanOrEqual(series.numFactory().numOf(6)));
    }
}