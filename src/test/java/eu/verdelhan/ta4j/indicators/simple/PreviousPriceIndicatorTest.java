package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class PreviousPriceIndicatorTest {
	private PreviousPriceIndicator previousPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		previousPriceIndicator = new PreviousPriceIndicator(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickPreviousPrice() {
		assertThat(timeSeries.getTick(0).getClosePrice()).isEqualTo(previousPriceIndicator.getValue(0));
		for (int i = 1; i < 10; i++) {
			assertThat(timeSeries.getTick(i-1).getClosePrice()).isEqualTo(previousPriceIndicator.getValue(i));
		}
	}
}
