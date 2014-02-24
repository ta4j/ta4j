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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class WMAIndicatorTest {
	@Test
	public void testWMACalculate()
	{
		MockTimeSeries series = new MockTimeSeries(1d, 2d, 3d, 4d, 5d, 6d);
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 3);
		
		assertThat(wmaIndicator.getValue(0)).isEqualTo(1d);
		assertThat(wmaIndicator.getValue(1)).isEqualTo(5d/3);
		assertThat(wmaIndicator.getValue(2)).isEqualTo(14d/6);
		assertThat(wmaIndicator.getValue(3)).isEqualTo(20d/6);
		assertThat(wmaIndicator.getValue(4)).isEqualTo(26d/6);
		assertThat(wmaIndicator.getValue(5)).isEqualTo(32d/6);
	}
	
	@Test
	public void testWMACalculateJumpingIndex()
	{
		MockTimeSeries series = new MockTimeSeries(1d, 2d, 3d, 4d, 5d, 6d);
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 3);
		
		assertThat(wmaIndicator.getValue(5)).isEqualTo(32d/6);
	}
	
	@Test
	public void testWMACalculateWithTimeFrameGreaterThanSeriesSize()
	{
		MockTimeSeries series = new MockTimeSeries(1d, 2d, 3d, 4d, 5d, 6d);
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 55);
		
		assertThat(wmaIndicator.getValue(0)).isEqualTo(1d);
		assertThat(wmaIndicator.getValue(1)).isEqualTo(5d/3);
		assertThat(wmaIndicator.getValue(2)).isEqualTo(14d/6);
		assertThat(wmaIndicator.getValue(3)).isEqualTo(30d/10);
		assertThat(wmaIndicator.getValue(4)).isEqualTo(55d/15);
		assertThat(wmaIndicator.getValue(5)).isEqualTo(91d/21);
	}

}
