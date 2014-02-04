package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.OpenPrice;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class OpenPriceTest {
	private OpenPrice openPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		openPriceIndicator = new OpenPrice(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickOpenPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals(openPriceIndicator.getValue(i), timeSeries.getTick(i).getOpenPrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		openPriceIndicator.getValue(10);
	}
}
