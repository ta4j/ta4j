package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AmountTest {
	private Amount amountIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		amountIndicator = new Amount(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickAmountPrice() {
		for (int i = 0; i < 10; i++) {
			assertEquals((double) amountIndicator.getValue(i), (double) timeSeries.getTick(i).getAmount());
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
