/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
/**
 * 
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * @author jtomkinson
 *
 */
public class ChopIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    protected BarSeries series;
    protected final BaseBarSeriesBuilder BarSeriesBuilder = new BaseBarSeriesBuilder().withNumTypeOf(numFunction);

    public ChopIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    /**
     * this will assert that choppiness is high if market price is not moving
     */
    @Test
    public void testChoppy() {
        series = BarSeriesBuilder.withName("low volatility series").withNumTypeOf(numFunction).build();
        for (int i = 0; i < 50; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(100000 - i);
            series.addBar(date, 21.5, 21.5 + 1, 21.5 - 1, 21.5);
        }
        ChopIndicator ci1 = new ChopIndicator(series, 14, 100);
        int HIGH_CHOPPINESS_VALUE = 85;
        assertTrue(ci1.getValue(series.getEndIndex()).doubleValue() > HIGH_CHOPPINESS_VALUE);
    }

    /**
     * this will assert that choppiness is low if market price is trending
     * significantly
     */
    @Test
    public void testTradeableTrend() {
        series = BarSeriesBuilder.withName("low volatility series").withNumTypeOf(numFunction).build();
        float value = 21.5f;
        for (int i = 0; i < 50; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(100000 - i);
            series.addBar(date, value, value + 1, value - 1, value);
            value += 2.0f;
        }
        ChopIndicator ci1 = new ChopIndicator(series, 14, 100);
        int LOW_CHOPPINESS_VALUE = 30;
        assertTrue(ci1.getValue(series.getEndIndex()).doubleValue() < LOW_CHOPPINESS_VALUE);
    }

    // TODO: this test class needs better cases

}
