package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.TradeCount;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class TradeCountTest {
	private TradeCount tradeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
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
}
