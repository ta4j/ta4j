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
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class HighestValueIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public HighestValueIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void highestValueUsingBarCount5UsingClosePrice() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals("4.0", highestValue.getValue(4));
        assertNumEquals("4.0", highestValue.getValue(5));
        assertNumEquals("5.0", highestValue.getValue(6));
        assertNumEquals("6.0", highestValue.getValue(7));
        assertNumEquals("6.0", highestValue.getValue(8));
        assertNumEquals("6.0", highestValue.getValue(9));
        assertNumEquals("6.0", highestValue.getValue(10));
        assertNumEquals("6.0", highestValue.getValue(11));
        assertNumEquals("4.0", highestValue.getValue(12));
    }

    @Test
    public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals("1.0", highestValue.getValue(0));
    }

    @Test
    public void highestValueIndicatorWhenBarCountIsGreaterThanIndex() {
        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
        assertNumEquals("6.0", highestValue.getValue(12));
    }

    @Test
    public void onlyNaNValues() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 10000; i++) {
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(NaN)
                    .highPrice(NaN)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        var highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 5);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertEquals(NaN.toString(), highestValue.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInIntervall() {
        var series = new MockBarSeriesBuilder().withName("NaN test").build();
        var now = Instant.now();
        for (long i = 0; i <= 10; i++) { // (0, NaN, 2, NaN, 3, NaN, 4, NaN, 5, ...)
            Num closePrice = i % 2 == 0 ? series.numFactory().numOf(i) : NaN;
            series.barBuilder()
                    .endTime(now.plus(Duration.ofDays(i)))
                    .openPrice(NaN)
                    .closePrice(closePrice)
                    .highPrice(NaN)
                    .lowPrice(NaN)
                    .volume(NaN)
                    .add();
        }

        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 2);

        // index is the biggest of (index, index-1)
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            if (i % 2 != 0) // current is NaN take the previous as highest
                assertEquals(series.getBar(i - 1).getClosePrice().toString(), highestValue.getValue(i).toString());
            else // current is not NaN but previous, take the current
                assertEquals(series.getBar(i).getClosePrice().toString(), highestValue.getValue(i).toString());
        }
    }
}
