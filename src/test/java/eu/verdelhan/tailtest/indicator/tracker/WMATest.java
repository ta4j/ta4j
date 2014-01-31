package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class WMATest {
	@Test
	public void testWMACalculate()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 3);
		
		assertEquals(1d, (double) wmaIndicator.getValue(0));
		assertEquals(5d/3, (double) wmaIndicator.getValue(1));
		assertEquals(14d/6, (double) wmaIndicator.getValue(2));
		assertEquals(20d/6, (double) wmaIndicator.getValue(3));
		assertEquals(26d/6, (double) wmaIndicator.getValue(4));
		assertEquals(32d/6, (double) wmaIndicator.getValue(5));
	}
	
	@Test
	public void testWMACalculateJumpingIndex()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 3);
		
		assertEquals(32d/6, (double) wmaIndicator.getValue(5));
	}
	
	@Test
	public void testWMACalculateWithTimeFrameGreaterThanSeriesSize()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 55);
		
		assertEquals(1d, (double) wmaIndicator.getValue(0));
		assertEquals(5d/3, (double) wmaIndicator.getValue(1));
		assertEquals(14d/6, (double) wmaIndicator.getValue(2));
		assertEquals(30d/10, (double) wmaIndicator.getValue(3));
		assertEquals(55d/15, (double) wmaIndicator.getValue(4));
		assertEquals(91d/21, (double) wmaIndicator.getValue(5));
	}

}
