package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class TypicalPriceTest {

	private TypicalPrice typicalPriceIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		typicalPriceIndicator = new TypicalPrice(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickMaxPrice() {
		for (int i = 0; i < 10; i++) {
			Tick tick = timeSeries.getTick(i);
			BigDecimal typicalPrice = tick.getMaxPrice().add(tick.getMinPrice()).add(tick.getClosePrice()).divide(BigDecimal.valueOf(3), TAUtils.MATH_CONTEXT);
			assertThat(typicalPriceIndicator.getValue(i)).isEqualTo(typicalPrice);
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		typicalPriceIndicator.getValue(10);
	}
}
