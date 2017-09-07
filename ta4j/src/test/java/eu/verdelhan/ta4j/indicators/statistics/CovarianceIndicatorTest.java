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
package eu.verdelhan.ta4j.indicators.statistics;

import eu.verdelhan.ta4j.BaseTimeSeries;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.helpers.VolumeIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class CovarianceIndicatorTest {
    private TimeSeries data;

    private Indicator<Decimal> close, volume;
    
    @Before
    public void setUp() {
        List<Tick> ticks = new ArrayList<Tick>();
        // close, volume
        ticks.add(new MockTick(6, 100));
        ticks.add(new MockTick(7, 105));
        ticks.add(new MockTick(9, 130));
        ticks.add(new MockTick(12, 160));
        ticks.add(new MockTick(11, 150));
        ticks.add(new MockTick(10, 130));
        ticks.add(new MockTick(11, 95));
        ticks.add(new MockTick(13, 120));
        ticks.add(new MockTick(15, 180));
        ticks.add(new MockTick(12, 160));
        ticks.add(new MockTick(8, 150));
        ticks.add(new MockTick(4, 200));
        ticks.add(new MockTick(3, 150));
        ticks.add(new MockTick(4, 85));
        ticks.add(new MockTick(3, 70));
        ticks.add(new MockTick(5, 90));
        ticks.add(new MockTick(8, 100));
        ticks.add(new MockTick(9, 95));
        ticks.add(new MockTick(11, 110));
        ticks.add(new MockTick(10, 95));

        data = new BaseTimeSeries(ticks);
        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void usingTimeFrame5UsingClosePriceAndVolume() {
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 5);

		assertDecimalEquals(covar.getValue(0), 0);
		assertDecimalEquals(covar.getValue(1), 26.25);
		assertDecimalEquals(covar.getValue(2), 63.3333);
		assertDecimalEquals(covar.getValue(3), 143.75);
		assertDecimalEquals(covar.getValue(4), 156);
		assertDecimalEquals(covar.getValue(5), 60.8);
		assertDecimalEquals(covar.getValue(6), 15.2);
		assertDecimalEquals(covar.getValue(7), -17.6);
		assertDecimalEquals(covar.getValue(8), 4);
		assertDecimalEquals(covar.getValue(9), 11.6);
		assertDecimalEquals(covar.getValue(10), -14.4);
		assertDecimalEquals(covar.getValue(11), -100.2);
		assertDecimalEquals(covar.getValue(12), -70.0);
		assertDecimalEquals(covar.getValue(13), 24.6);
		assertDecimalEquals(covar.getValue(14), 35.0);
		assertDecimalEquals(covar.getValue(15), -19.0);
		assertDecimalEquals(covar.getValue(16), -47.8);
		assertDecimalEquals(covar.getValue(17), 11.4);
		assertDecimalEquals(covar.getValue(18), 55.8);
		assertDecimalEquals(covar.getValue(19), 33.4);
    }

    @Test
    public void firstValueShouldBeZero() {
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 5);
        assertDecimalEquals(covar.getValue(0), 0);
    }

    @Test
    public void shouldBeZeroWhenTimeFrameIs1() {
        CovarianceIndicator covar = new CovarianceIndicator(close, volume, 1);
        assertDecimalEquals(covar.getValue(3), 0);
        assertDecimalEquals(covar.getValue(8), 0);
    }
}
