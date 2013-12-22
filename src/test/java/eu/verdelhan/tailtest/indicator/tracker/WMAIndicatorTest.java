package net.sf.tail.indicator.tracker;

import static org.junit.Assert.assertEquals;
import net.sf.tail.Indicator;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Test;

public class WMAIndicatorTest {
	@Test
	public void testWMACalculate()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 3);
		
		assertEquals(1d, wmaIndicator.getValue(0));
		assertEquals(5d/3, wmaIndicator.getValue(1));
		assertEquals(14d/6, wmaIndicator.getValue(2));
		assertEquals(20d/6, wmaIndicator.getValue(3));
		assertEquals(26d/6, wmaIndicator.getValue(4));
		assertEquals(32d/6, wmaIndicator.getValue(5));
	}
	
	@Test
	public void testWMACalculateJumpingIndex()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 3);
		
		assertEquals(32d/6, wmaIndicator.getValue(5));
	}
	
	@Test
	public void testWMACalculateWithTimeFrameGreaterThanSeriesSize()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<Double> close = new ClosePriceIndicator(series);
		Indicator<Double> wmaIndicator = new WMAIndicator(close, 55);
		
		assertEquals(1d, wmaIndicator.getValue(0));
		assertEquals(5d/3, wmaIndicator.getValue(1));
		assertEquals(14d/6, wmaIndicator.getValue(2));
		assertEquals(30d/10, wmaIndicator.getValue(3));
		assertEquals(55d/15, wmaIndicator.getValue(4));
		assertEquals(91d/21, wmaIndicator.getValue(5));
	}

}
