package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class MinPriceTest {
	private MinPrice minPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		minPriceIndicator = new MinPrice(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickMinPrice() {
		for (int i = 0; i < 10; i++) {
			assertThat(timeSeries.getTick(i).getMinPrice()).isEqualTo(minPriceIndicator.getValue(i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		minPriceIndicator.getValue(10);
	}
}
