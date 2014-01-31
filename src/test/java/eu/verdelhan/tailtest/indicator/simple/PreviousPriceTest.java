package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class PreviousPriceTest {
	private PreviousPrice previousPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		previousPriceIndicator = new PreviousPrice(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickPreviousPrice() {
		assertEquals(previousPriceIndicator.getValue(0), timeSeries.getTick(0).getClosePrice());
		for (int i = 1; i < 10; i++) {
			assertEquals(previousPriceIndicator.getValue(i), timeSeries.getTick(i-1).getClosePrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		previousPriceIndicator.getValue(10);
	}
}
