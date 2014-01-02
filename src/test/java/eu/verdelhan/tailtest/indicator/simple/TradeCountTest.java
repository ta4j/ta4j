package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class TradeCountTest {
	private TradeCount tradeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		tradeIndicator = new TradeCount(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickTrade() {
		for (int i = 0; i < 10; i++) {
			assertEquals((double) tradeIndicator.getValue(i), timeSeries.getTick(i).getTrades());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		tradeIndicator.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("TradeIndicator", tradeIndicator.getName());
	}
}
