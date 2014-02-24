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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class ParabolicSarAndDMIStrategyTest {

	@Test
	public void shouldEnterTest()
	{
		TimeSeries series1 = new MockTimeSeries(10, 9, 6, 10, 5);
		TimeSeries series2 = new MockTimeSeries(8, 7, 7, 8, 6);
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, null);
		assertThat(parabolicStrategy.shouldEnter(0)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(1)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(2)).isTrue();
		assertThat(parabolicStrategy.shouldEnter(3)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(4)).isTrue();
	}
	
	@Test
	public void shouldExitTest()
	{
		TimeSeries series1 = new MockTimeSeries(6, 11, 6, 5, 9);
		TimeSeries series2 = new MockTimeSeries(10, 9, 7, 6, 6);
		
		TimeSeries series3 = new MockTimeSeries(1, 1, 1, 1, 1);
		TimeSeries series4 = new MockTimeSeries(2, 2, 2, 2, 0);
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		IndicatorOverIndicatorStrategy indicatorOverIndicator = new IndicatorOverIndicatorStrategy(new ClosePriceIndicator(series3), new ClosePriceIndicator(series4));
		
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, indicatorOverIndicator);
		
		assertThat(parabolicStrategy.shouldExit(0)).isFalse();
		assertThat(parabolicStrategy.shouldExit(1)).isFalse();
		assertThat(parabolicStrategy.shouldExit(2)).isFalse();
		assertThat(parabolicStrategy.shouldExit(3)).isFalse();
		assertThat(parabolicStrategy.shouldExit(4)).isTrue();
	}
}
