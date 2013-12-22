package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class PreviousPriceIndicatorTest {
	private PreviousPriceIndicator previousPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		previousPriceIndicator = new PreviousPriceIndicator(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickPreviousPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals(previousPriceIndicator.getValue(i), timeSeries.getTick(i).getPreviousPrice());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		previousPriceIndicator.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("PreviousPriceIndicator", previousPriceIndicator.getName());
	}
}
