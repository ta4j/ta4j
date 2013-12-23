package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class OpenPriceIndicatorTest {
	private OpenPriceIndicator openPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		openPriceIndicator = new OpenPriceIndicator(timeSeries);
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

	@Test
	public void testGetName() {
		assertEquals("OpenPriceIndicator", openPriceIndicator.getName());
	}
}
