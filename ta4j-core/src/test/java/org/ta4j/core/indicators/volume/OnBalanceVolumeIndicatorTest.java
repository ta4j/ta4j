/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OnBalanceVolumeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public OnBalanceVolumeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(10).volume(4).add();
        series.barBuilder().closePrice(5).volume(2).add();
        series.barBuilder().closePrice(6).volume(3).add();
        series.barBuilder().closePrice(7).volume(8).add();
        series.barBuilder().closePrice(7).volume(6).add();
        series.barBuilder().closePrice(6).volume(10).add();

        var obv = new OnBalanceVolumeIndicator(series);
        assertNumEquals(0, obv.getValue(0));
        assertNumEquals(-2, obv.getValue(1));
        assertNumEquals(1, obv.getValue(2));
        assertNumEquals(9, obv.getValue(3));
        assertNumEquals(9, obv.getValue(4));
        assertNumEquals(-1, obv.getValue(5));
    }

    @Test
    public void noStackOverflowError() {
        var bigSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10000; i++) {
            bigSeries.barBuilder().closePrice(i).volume(0).add();
        }
        var obv = new OnBalanceVolumeIndicator(bigSeries);
        // If a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertNumEquals(0, obv.getValue(9999));
    }
}
