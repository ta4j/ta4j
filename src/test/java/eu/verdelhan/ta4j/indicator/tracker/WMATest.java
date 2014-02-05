package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class WMATest {
	@Test
	public void testWMACalculate()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 3);
		
		assertThat((double) wmaIndicator.getValue(0)).isEqualTo(1d);
		assertThat((double) wmaIndicator.getValue(1)).isEqualTo(5d/3);
		assertThat((double) wmaIndicator.getValue(2)).isEqualTo(14d/6);
		assertThat((double) wmaIndicator.getValue(3)).isEqualTo(20d/6);
		assertThat((double) wmaIndicator.getValue(4)).isEqualTo(26d/6);
		assertThat((double) wmaIndicator.getValue(5)).isEqualTo(32d/6);
	}
	
	@Test
	public void testWMACalculateJumpingIndex()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 3);
		
		assertThat((double) wmaIndicator.getValue(5)).isEqualTo(32d/6);
	}
	
	@Test
	public void testWMACalculateWithTimeFrameGreaterThanSeriesSize()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {1d, 2d, 3d, 4d, 5d, 6d});
		Indicator<BigDecimal> close = new ClosePrice(series);
		Indicator<Double> wmaIndicator = new WMA(close, 55);
		
		assertThat((double) wmaIndicator.getValue(0)).isEqualTo(1d);
		assertThat((double) wmaIndicator.getValue(1)).isEqualTo(5d/3);
		assertThat((double) wmaIndicator.getValue(2)).isEqualTo(14d/6);
		assertThat((double) wmaIndicator.getValue(3)).isEqualTo(30d/10);
		assertThat((double) wmaIndicator.getValue(4)).isEqualTo(55d/15);
		assertThat((double) wmaIndicator.getValue(5)).isEqualTo(91d/21);
	}

}
