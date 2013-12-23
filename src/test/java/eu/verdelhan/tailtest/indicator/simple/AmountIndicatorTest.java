package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

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
