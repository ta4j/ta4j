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

/**
 * Tests for {@link ElliottWaveFacade}.
 */
class ElliottWaveFacadeTest {

    @Test
    void fractalFactory_shouldCreateAllIndicators() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);

        assertThat(suite.series()).isSameAs(series);
        assertThat(suite.swing()).isNotNull();
        assertThat(suite.phase()).isNotNull();
        assertThat(suite.ratio()).isNotNull();
        assertThat(suite.channel()).isNotNull();
        assertThat(suite.waveCount()).isNotNull();
        assertThat(suite.confluence()).isNotNull();
        assertThat(suite.invalidation()).isNotNull();
    }

    @Test
    void indicatorsAreLazilyCreatedAndReused() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);

        // First call creates the indicator
        var phase1 = suite.phase();
        // Second call returns the same instance
        var phase2 = suite.phase();
        assertThat(phase1).isSameAs(phase2);

        // Same for other indicators
        assertThat(suite.ratio()).isSameAs(suite.ratio());
        assertThat(suite.channel()).isSameAs(suite.channel());
    }

    @Test
    void zigZagFactory_shouldCreateIndicators() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6, 16, 5, 17, 4, 18, 3 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.zigZag(series, ElliottDegree.INTERMEDIATE);

        assertThat(suite.swing()).isNotNull();
        assertThat(suite.phase().getValue(series.getEndIndex())).isNotNull();
    }

    @Test
    void suiteIndicatorsShareSameSwingSource() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }

        var suite = ElliottWaveFacade.fractal(series, 1, ElliottDegree.MINOR);

        // All indicators should use the same swing source
        assertThat(suite.phase().getSwingIndicator()).isSameAs(suite.swing());
    }
}
