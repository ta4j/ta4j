package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class MinPriceTest {
	private MinPrice minPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		minPriceIndicator = new MinPrice(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickMinPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals((double) minPriceIndicator.getValue(i), timeSeries.getTick(i).getMinPrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		minPriceIndicator.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("MinPriceIndicator", minPriceIndicator.getName());
	}
}
