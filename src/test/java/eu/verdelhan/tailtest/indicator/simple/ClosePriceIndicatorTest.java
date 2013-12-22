package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class ClosePriceIndicatorTest {
	private ClosePriceIndicator closePrice;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		closePrice = new ClosePriceIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickClosePrice() {

		for (int i = 0; i < 10; i++) {

			assertEquals(closePrice.getValue(i), timeSeries.getTick(i).getClosePrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		closePrice.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("ClosePriceIndicator", closePrice.getName());
	}
}
