package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class ClosePriceTest {
	private ClosePrice closePrice;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		closePrice = new ClosePrice(timeSeries);
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
