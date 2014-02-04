package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.MaxPrice;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class MaxPriceTest {
	private MaxPrice maxPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		maxPriceIndicator = new MaxPrice(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickMaxPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals(maxPriceIndicator.getValue(i), timeSeries.getTick(i).getMaxPrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		maxPriceIndicator.getValue(10);
	}
}
