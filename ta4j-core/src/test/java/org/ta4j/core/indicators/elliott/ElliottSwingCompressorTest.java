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

        var compressor = new ElliottSwingCompressor(null, 2);
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
}