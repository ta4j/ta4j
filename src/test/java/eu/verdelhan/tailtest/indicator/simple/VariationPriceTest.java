package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class VariationPriceTest {

	private Variation variationIndicator;

	private TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		variationIndicator = new Variation(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickVariation() {
		assertEquals(variationIndicator.getValue(0), BigDecimal.valueOf(1.0));
		for (int i = 1; i < 10; i++) {
			BigDecimal previousTickClosePrice = timeSeries.getTick(i - 1).getClosePrice();
			BigDecimal currentTickClosePrice = timeSeries.getTick(i).getClosePrice();
			assertEquals(variationIndicator.getValue(i), currentTickClosePrice.divide(previousTickClosePrice));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		variationIndicator.getValue(10);
	}
}
