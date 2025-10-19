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

import org.junit.Test;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;

public class ElliottSwingIndicatorTest {

    @Test
    public void detectsSwingsWithoutLookahead() {
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
    public void skipsNanBars() {
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
}
