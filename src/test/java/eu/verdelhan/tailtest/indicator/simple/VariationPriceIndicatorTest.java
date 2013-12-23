package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class VariationPriceIndicatorTest {

	private VariationIndicator variationIndicator;

	private TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		variationIndicator = new VariationIndicator(timeSeries);

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

	@Test
	public void testGetName() {
		assertEquals("VariationIndicator", variationIndicator.getName());
	}
}
