package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class AmountIndicatorTest {
	private AmountIndicator amountIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		amountIndicator = new AmountIndicator(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickAmountPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals(amountIndicator.getValue(i), timeSeries.getTick(i).getAmount());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		amountIndicator.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("AmountIndicator", amountIndicator.getName());
	}
}
