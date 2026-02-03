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
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.supportresistance.BounceCountResistanceIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BounceCountResistanceIndicatorTest extends AbstractIndicatorTest<BounceCountResistanceIndicator, Num> {

    public BounceCountResistanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldPreferHigherResistanceWhenBounceCountsTie() {
        BarSeries series = buildSeries(5.0, 6.0, 5.0, 7.0, 5.0);
        var indicator = new BounceCountResistanceIndicator(series, numOf(0));

        assertThat(indicator.getValue(4)).as("ties should favour higher resistance bucket")
                .isEqualByComparingTo(numOf(7));
        assertThat(indicator.getBounceIndex(4)).isEqualTo(3);
    }

    @Test
    public void shouldGroupResistanceBouncesWithinBucketSize() {
        BarSeries series = buildSeries(10.0, 10.6, 10.9, 10.4, 10.8, 10.2, 10.6);
        var indicator = new BounceCountResistanceIndicator(series, numOf(0.5));

        assertThat(indicator.getValue(6).doubleValue()).as("resistance average within bucket")
                .isCloseTo(10.85, within(1e-6));
        assertThat(indicator.getBounceIndex(6)).isEqualTo(4);
    }

    @Test
    public void shouldCountBounceWhenWindowStartsAfterTrend() {
        BarSeries series = buildSeries(3.0, 4.0, 5.0, 4.0);
        var indicator = new BounceCountResistanceIndicator(new ClosePriceIndicator(series), 2, numOf(0));

        assertThat(indicator.getValue(3)).as("bounce within truncated window")
                .isEqualByComparingTo(numOf(5));
        assertThat(indicator.getBounceIndex(3)).isEqualTo(2);
    }

    @Test
    public void shouldIgnoreBouncesOutsideWindow() {
        BarSeries series = buildSeries(3.0, 4.0, 3.0, 2.0);
        var indicator = new BounceCountResistanceIndicator(new ClosePriceIndicator(series), 2, numOf(0.1));

        assertThat(indicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(indicator.getBounceIndex(3)).isEqualTo(-1);
    }

    @Test
    public void shouldReturnNaNWhenNoBounceExists() {
        BarSeries series = buildSeries(4.0, 3.5, 3.0, 2.5);
        var indicator = new BounceCountResistanceIndicator(series, numOf(0.2));

        assertThat(indicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(indicator.getBounceIndex(3)).isEqualTo(-1);
    }

    private BarSeries buildSeries(double... closes) {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        BarSeries series = builder.build();
        for (double close : closes) {
            series.barBuilder()
                    .closePrice(close)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}
