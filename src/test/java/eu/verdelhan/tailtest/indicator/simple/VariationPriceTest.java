package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class VariationPriceTest {

	private Variation variationIndicator;

	private TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		variationIndicator = new Variation(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickVariation() {
		for (int i = 0; i < 10; i++) {
			assertEquals(variationIndicator.getValue(i), timeSeries.getTick(i).getVariation());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		variationIndicator.getValue(10);
	}
}
