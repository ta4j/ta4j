package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class MinPriceIndicatorTest {
	private MinPriceIndicator minPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		minPriceIndicator = new MinPriceIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickMinPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals(minPriceIndicator.getValue(i), timeSeries.getTick(i).getMinPrice());
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
