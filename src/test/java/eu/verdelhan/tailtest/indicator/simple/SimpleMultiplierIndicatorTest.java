package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class SimpleMultiplierIndicatorTest {
	private ConstantIndicator<Double> constantIndicator;
	private SimpleMultiplierIndicator simpleMultiplier;

	@Before
	public void setUp() {
		
		constantIndicator = new ConstantIndicator<Double>(5d);
		this.simpleMultiplier = new SimpleMultiplierIndicator(constantIndicator, 5d);
	}

	@Test
	public void testConstantIndicator() {
		assertEquals(25d, simpleMultiplier.getValue(10));
		assertEquals(25d, simpleMultiplier.getValue(1));
		assertEquals(25d, simpleMultiplier.getValue(0));
		assertEquals(25d, simpleMultiplier.getValue(30));
	}
}
