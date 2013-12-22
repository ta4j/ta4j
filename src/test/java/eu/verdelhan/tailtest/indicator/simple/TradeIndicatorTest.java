package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class TradeIndicatorTest {
	private TradeIndicator tradeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		tradeIndicator = new TradeIndicator(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickTrade() {
		for (int i = 0; i < 10; i++) {
			assertEquals(tradeIndicator.getValue(i), timeSeries.getTick(i).getTrades());
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
