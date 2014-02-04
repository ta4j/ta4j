package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class ClosePriceTest {
	private ClosePrice closePrice;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
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
}
