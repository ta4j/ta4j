package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class MaxPriceIndicatorTest {
	private MaxPriceIndicator maxPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		maxPriceIndicator = new MaxPriceIndicator(timeSeries);

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

	@Test
	public void testGetName() {
		assertEquals("MaxPriceIndicator", maxPriceIndicator.getName());
	}

}
