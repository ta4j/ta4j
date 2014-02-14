package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.AmountIndicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AmountIndicatorTest {

	private AmountIndicator amountIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		amountIndicator = new AmountIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickAmountPrice() {
		for (int i = 0; i < 10; i++) {
			assertThat(timeSeries.getTick(i).getAmount()).isEqualTo(amountIndicator.getValue(i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		amountIndicator.getValue(10);
	}
}
