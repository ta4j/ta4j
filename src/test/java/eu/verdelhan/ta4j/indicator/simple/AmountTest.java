package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.Amount;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AmountTest {

	private Amount amountIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		amountIndicator = new Amount(timeSeries);
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
}
