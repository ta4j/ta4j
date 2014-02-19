package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class TypicalPriceIndicatorTest {

	private TypicalPriceIndicator typicalPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		typicalPriceIndicator = new TypicalPriceIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickMaxPrice() {
		for (int i = 0; i < 10; i++) {
			Tick tick = timeSeries.getTick(i);
			double typicalPrice = (tick.getMaxPrice() + tick.getMinPrice() + tick.getClosePrice()) / 3d;
			assertThat(typicalPriceIndicator.getValue(i)).isEqualTo(typicalPrice);
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		typicalPriceIndicator.getValue(10);
	}
}
