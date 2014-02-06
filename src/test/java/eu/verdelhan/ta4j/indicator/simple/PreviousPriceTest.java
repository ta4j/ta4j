package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class PreviousPriceTest {
	private PreviousPrice previousPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		previousPriceIndicator = new PreviousPrice(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickPreviousPrice() {
		assertThat(timeSeries.getTick(0).getClosePrice()).isEqualTo(previousPriceIndicator.getValue(0));
		for (int i = 1; i < 10; i++) {
			assertThat(timeSeries.getTick(i-1).getClosePrice()).isEqualTo(previousPriceIndicator.getValue(i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		previousPriceIndicator.getValue(10);
	}
}
