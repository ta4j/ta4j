package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class MaxPriceIndicatorTest {
	private MaxPriceIndicator maxPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		maxPriceIndicator = new MaxPriceIndicator(timeSeries);

	}

	@Test
	public void testIndicatorShouldRetrieveTickMaxPrice() {
		for (int i = 0; i < 10; i++) {
			assertThat(timeSeries.getTick(i).getMaxPrice()).isEqualTo(maxPriceIndicator.getValue(i));
		}
	}
}
