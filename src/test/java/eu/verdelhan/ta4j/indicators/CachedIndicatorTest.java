/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.EMAIndicator;
import eu.verdelhan.ta4j.indicators.trackers.RSIIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void testIfCacheWorks() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        Double firstTime = sma.getValue(4);
        Double seconTime = sma.getValue(4);
        assertThat(seconTime).isEqualTo(firstTime);
    }

    @Test
    public void testIncreaseArrayMethod() {
        double[] d = new double[200];
        Arrays.fill(d, 10);
        TimeSeries dataMax = new MockTimeSeries(d);
        SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(dataMax), 100);
        assertThat(quoteSMA.getValue(105)).isEqualTo((double) 10d);
    }

    @Test
    public void testReallyBigCachedEMAExtendsCachedIndicator() {
        int maxIndex = 1000000;
        List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
        TimeSeries longData = new MockTimeSeries(ticks);
        EMAIndicator quoteEMA = new EMAIndicator(new ClosePriceIndicator(longData), 10);

        quoteEMA.getValue(maxIndex - 1);

    }

    @Test
    public void testReallyCachedBigRSINotExtendsCachedIndicator() {
        int maxIndex = 1000000;
        List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
        TimeSeries longData = new MockTimeSeries(ticks);
        RSIIndicator RSI = new RSIIndicator(new ClosePriceIndicator(longData), 10);

        RSI.getValue(maxIndex - 1);

    }
}
