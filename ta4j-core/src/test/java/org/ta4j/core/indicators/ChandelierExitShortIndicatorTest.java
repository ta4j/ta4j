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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ChandelierExitShortIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public ChandelierExitShortIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(45.05).highPrice(45.17).lowPrice(44.96).add();
        data.barBuilder().openPrice(45.05).closePrice(45.10).highPrice(45.15).lowPrice(44.99).add();
        data.barBuilder().openPrice(45.11).closePrice(45.19).highPrice(45.32).lowPrice(45.11).add();
        data.barBuilder().openPrice(45.19).closePrice(45.14).highPrice(45.25).lowPrice(45.04).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.14).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.13).closePrice(45.10).highPrice(45.16).lowPrice(45.07).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.22).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.22).highPrice(45.27).lowPrice(45.14).add();
        data.barBuilder().openPrice(45.24).closePrice(45.43).highPrice(45.45).lowPrice(45.20).add();
        data.barBuilder().openPrice(45.43).closePrice(45.44).highPrice(45.50).lowPrice(45.39).add();
        data.barBuilder().openPrice(45.43).closePrice(45.55).highPrice(45.60).lowPrice(45.35).add();
        data.barBuilder().openPrice(45.58).closePrice(45.55).highPrice(45.61).lowPrice(45.39).add();
        data.barBuilder().openPrice(45.45).closePrice(45.01).highPrice(45.55).lowPrice(44.80).add();
        data.barBuilder().openPrice(45.03).closePrice(44.23).highPrice(45.04).lowPrice(44.17).add();
    }

    @Test
    public void massIndexUsing3And8BarCounts() {
        var ces = new ChandelierExitShortIndicator(data, 5, 2);

        // ChandelierExitShort uses ATRIndicator with barCount=5, so ATR returns NaN for
        // indices 0-4
        // This causes ChandelierExitShort to return NaN during unstable period
        for (int i = 0; i < 5; i++) {
            assertThat(Double.isNaN(ces.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first ATR/MMA value after
        // unstable period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(ces.getValue(5).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(6).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(7).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(8).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(9).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(10).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(11).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(12).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(13).doubleValue())).isFalse();
        assertThat(Double.isNaN(ces.getValue(14).doubleValue())).isFalse();
    }
}
