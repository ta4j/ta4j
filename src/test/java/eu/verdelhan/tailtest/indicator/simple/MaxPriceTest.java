package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class MaxPriceTest {
	private MaxPrice maxPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
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
