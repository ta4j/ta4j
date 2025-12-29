/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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