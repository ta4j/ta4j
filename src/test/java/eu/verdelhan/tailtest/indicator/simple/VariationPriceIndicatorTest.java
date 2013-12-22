package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

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
