/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class DelayIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries barSeries;
    private ClosePriceIndicator closePriceIndicator;

    public DelayIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeries(numFunction);
        closePriceIndicator = new ClosePriceIndicator(barSeries);
    }

    @Test
    public void indicatorReturnsPastValuesOnPositiveDelay() {
        int delay = 3;
        DelayIndicator indicator = new DelayIndicator(closePriceIndicator, delay);
        boolean loopCalled = false;
        for (int i = barSeries.getBeginIndex() + delay; i <= barSeries.getEndIndex() + delay; i++) {
            loopCalled = true;
            assertEquals(closePriceIndicator.getValue(i - delay), indicator.getValue(i));
        }
        assertTrue(loopCalled);
    }

    @Test
    public void indicatorReturnsFutureValuesOnNegativeDelay() {
        int delay = -3;
        DelayIndicator indicator = new DelayIndicator(closePriceIndicator, delay);
        boolean loopCalled = false;
        for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex() + delay; i++) {
            loopCalled = true;
            assertEquals(closePriceIndicator.getValue(i - delay), indicator.getValue(i));
        }
        assertTrue(loopCalled);
    }

    @Test
    public void indicatorReturnsNanIfBarIsNotAvailableInPast() {
        int delay = 3;
        DelayIndicator indicator = new DelayIndicator(closePriceIndicator, delay);
        boolean loopCalled = false;
        for (int i = barSeries.getBeginIndex(); i < barSeries.getBeginIndex() + delay; i++) {
            loopCalled = true;
            assertEquals(NaN, indicator.getValue(i));
        }
        assertTrue(loopCalled);
    }

    @Test
    public void indicatorReturnsNanIfBarIsNotAvailableInFuture() {
        int delay = -3;
        DelayIndicator indicator = new DelayIndicator(closePriceIndicator, delay);
        boolean loopCalled = false;
        for (int i = barSeries.getEndIndex() + 1 + delay; i <= barSeries.getEndIndex(); i++) {
            loopCalled = true;
            assertEquals(NaN, indicator.getValue(i));
        }
        assertTrue(loopCalled);
    }
}
