package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class PriceVariationIndicatorTest {

	private PriceVariationIndicator variationIndicator;

	private TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		variationIndicator = new PriceVariationIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickVariation() {
		assertThat(variationIndicator.getValue(0)).isEqualTo(1);
		for (int i = 1; i < 10; i++) {
			double previousTickClosePrice = timeSeries.getTick(i - 1).getClosePrice();
			double currentTickClosePrice = timeSeries.getTick(i).getClosePrice();
			assertThat(currentTickClosePrice / previousTickClosePrice).isEqualTo(variationIndicator.getValue(i));
		}
	}
}
