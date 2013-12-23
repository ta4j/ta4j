package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Test;


public class ParabolicSarAndDMIStrategyTest {

	@Test
	public void shouldEnterTest()
	{
		TimeSeries series1 = new SampleTimeSeries(new double[] {10, 9, 6, 10, 5 });
		TimeSeries series2 = new SampleTimeSeries(new double[] {8, 7, 7, 8, 6 });
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, null);
		assertFalse(parabolicStrategy.shouldEnter(0));
		assertFalse(parabolicStrategy.shouldEnter(1));
		assertTrue(parabolicStrategy.shouldEnter(2));
		assertFalse(parabolicStrategy.shouldEnter(3));
		assertTrue(parabolicStrategy.shouldEnter(4));
	}
	
	@Test
	public void shouldExitTest()
	{
		TimeSeries series1 = new SampleTimeSeries(new double[] {6, 11, 6, 5, 9 });
		TimeSeries series2 = new SampleTimeSeries(new double[] {10, 9, 7, 6, 6 });
		
		TimeSeries series3 = new SampleTimeSeries(new double[] {1, 1, 1, 1, 1} );
		TimeSeries series4 = new SampleTimeSeries(new double[] {2, 2, 2, 2, 0} );
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		IndicatorOverIndicatorStrategy indicatorOverIndicator = new IndicatorOverIndicatorStrategy(new ClosePriceIndicator(series3), new ClosePriceIndicator(series4));
		
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, indicatorOverIndicator);
		
		assertFalse(parabolicStrategy.shouldExit(0));
		assertFalse(parabolicStrategy.shouldExit(1));
		assertFalse(parabolicStrategy.shouldExit(2));
		assertFalse(parabolicStrategy.shouldExit(3));
		assertTrue(parabolicStrategy.shouldExit(4));
	}
}
