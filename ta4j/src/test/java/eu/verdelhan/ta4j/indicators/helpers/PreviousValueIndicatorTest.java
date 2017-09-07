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

import eu.verdelhan.ta4j.BaseTick;
import eu.verdelhan.ta4j.BaseTimeSeries;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.EMAIndicator;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class PreviousValueIndicatorTest {
    
    private PreviousValueIndicator prevValueIndicator;

    private ClosePriceIndicator closePriceIndicator;
    private OpenPriceIndicator openPriceIndicator;
    private MinPriceIndicator minPriceIndicator;
    private MaxPriceIndicator maxPriceIndicator;

    private VolumeIndicator volumeIndicator;
    private EMAIndicator emaIndicator;

    private TimeSeries series;

    @Before
    public void setUp() {
        Random r = new Random();
        List<Tick> ticks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double open = r.nextDouble();
            double close = r.nextDouble();
            double max = Math.max(close+r.nextDouble(), open+r.nextDouble());
            double min = Math.min(0, Math.min(close-r.nextDouble(), open-r.nextDouble()));
            ZonedDateTime dateTime = ZonedDateTime.now();
            Tick tick = new BaseTick(dateTime, open, close, max, min, i);
            ticks.add(tick);
        }
        this.series = new BaseTimeSeries("test", ticks);

        this.openPriceIndicator = new OpenPriceIndicator(this.series);
        this.minPriceIndicator = new MinPriceIndicator(this.series);
        this.maxPriceIndicator = new MaxPriceIndicator(this.series);
        this.volumeIndicator = new VolumeIndicator(this.series);
        this.closePriceIndicator = new ClosePriceIndicator(this.series);
        this.emaIndicator = new EMAIndicator(this.closePriceIndicator, 20);
    }

    @Test
    public void shouldBePreviousValueFromIndicator() {

        //test 1 with openPrice-indicator
        prevValueIndicator = new PreviousValueIndicator(openPriceIndicator);
        assertEquals(prevValueIndicator.getValue(0), openPriceIndicator.getValue(0));
        for (int i = 1; i < this.series.getTickCount(); i++) {
            assertEquals(prevValueIndicator.getValue(i), openPriceIndicator.getValue(i-1));
        }

        //test 2 with minPrice-indicator
        prevValueIndicator = new PreviousValueIndicator(minPriceIndicator);
        assertEquals(prevValueIndicator.getValue(0), minPriceIndicator.getValue(0));
        for (int i = 1; i < this.series.getTickCount(); i++) {
            assertEquals(prevValueIndicator.getValue(i), minPriceIndicator.getValue(i-1));
        }

        //test 3 with maxPrice-indicator
        prevValueIndicator = new PreviousValueIndicator(maxPriceIndicator);
        assertEquals(prevValueIndicator.getValue(0), maxPriceIndicator.getValue(0));
        for (int i = 1; i < this.series.getTickCount(); i++) {
            assertEquals(prevValueIndicator.getValue(i), maxPriceIndicator.getValue(i-1));
        }
    }

    @Test
    public void shouldBeNthPreviousValueFromIndicator() {
        for (int i = 0; i < this.series.getTickCount(); i++) {
            testWithN(i);
        }
    }

    private void testWithN(int n) {

        // test 1 with volume-indicator
        prevValueIndicator = new PreviousValueIndicator(volumeIndicator,n);
        for (int i = 0; i < n; i++) {
            assertEquals(prevValueIndicator.getValue(i), volumeIndicator.getValue(0));
        }
        for (int i = n; i < this.series.getTickCount(); i++) {
            assertEquals(prevValueIndicator.getValue(i), volumeIndicator.getValue(i-n));
        }

        // test 2 with ema-indicator
        prevValueIndicator = new PreviousValueIndicator(emaIndicator,n);
        for (int i = 0; i < n; i++) {
            assertEquals(prevValueIndicator.getValue(i), emaIndicator.getValue(0));
        }
        for (int i = n; i < this.series.getTickCount(); i++) {
            assertEquals(prevValueIndicator.getValue(i), emaIndicator.getValue(i-n));
        }
    }
}