/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.*;
import static junit.framework.TestCase.assertEquals;

import eu.verdelhan.ta4j.*;
import eu.verdelhan.ta4j.indicators.helpers.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class HighestValueIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void highestValueUsingTimeFrame5UsingClosePrice() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(highestValue.getValue(4), "4");
        assertDecimalEquals(highestValue.getValue(5), "4");
        assertDecimalEquals(highestValue.getValue(6), "5");
        assertDecimalEquals(highestValue.getValue(7), "6");
        assertDecimalEquals(highestValue.getValue(8), "6");
        assertDecimalEquals(highestValue.getValue(9), "6");
        assertDecimalEquals(highestValue.getValue(10), "6");
        assertDecimalEquals(highestValue.getValue(11), "6");
        assertDecimalEquals(highestValue.getValue(12), "4");
    }

    @Test
    public void firstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
        assertDecimalEquals(highestValue.getValue(0), "1");
    }

    @Test
    public void highestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
        assertDecimalEquals(highestValue.getValue(12), "6");
    }

    @Test
    public void onlyNaNValues(){
        List<Tick> ticks = new ArrayList<>();
        for (long i = 0; i<= 10000; i++){
            Tick tick = new BaseTick(ZonedDateTime.now().plusDays(i),Decimal.NaN, Decimal.NaN,Decimal.NaN, Decimal.NaN, Decimal.NaN);
            ticks.add(tick);
        }

        BaseTimeSeries series = new BaseTimeSeries("NaN test",ticks);
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 5);
        for (int i = series.getBeginIndex(); i<= series.getEndIndex(); i++){
            assertEquals(Decimal.NaN.toString(),highestValue.getValue(i).toString());
        }
    }

    @Test
    public void naNValuesInIntervall(){
        List<Tick> ticks = new ArrayList<>();
        for (long i = 0; i<= 10; i++){ // (0, NaN, 2, NaN, 3, NaN, 4, NaN, 5, ...)
            Decimal closePrice = i % 2 == 0 ? Decimal.valueOf(i): Decimal.NaN;
            Tick tick = new BaseTick(ZonedDateTime.now().plusDays(i),Decimal.NaN, Decimal.NaN,Decimal.NaN, closePrice, Decimal.NaN);
            ticks.add(tick);
        }

        BaseTimeSeries series = new BaseTimeSeries("NaN test",ticks);
        HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(series), 2);

        // index is the biggest of (index, index-1)
        for (int i = series.getBeginIndex(); i<= series.getEndIndex(); i++){
            if (i % 2 != 0) // current is NaN take the previous as highest
                assertEquals(series.getTick(i-1).getClosePrice().toString(),highestValue.getValue(i).toString());
            else // current is not NaN but previous, take the current
                assertEquals(series.getTick(i).getClosePrice().toString(),highestValue.getValue(i).toString());
        }
    }
}
