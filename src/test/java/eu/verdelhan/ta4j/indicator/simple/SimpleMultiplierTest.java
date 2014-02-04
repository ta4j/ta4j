package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.indicator.simple.SimpleMultiplier;
import eu.verdelhan.ta4j.indicator.simple.Constant;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class SimpleMultiplierTest {
	private Constant<Double> constantIndicator;
	private SimpleMultiplier simpleMultiplier;

	@Before
	public void setUp() {
		constantIndicator = new Constant<Double>(5d);
		simpleMultiplier = new SimpleMultiplier(constantIndicator, 5d);
	}

	@Test
	public void testConstantIndicator() {
		assertEquals(25d, (double) simpleMultiplier.getValue(10));
		assertEquals(25d, (double) simpleMultiplier.getValue(1));
		assertEquals(25d, (double) simpleMultiplier.getValue(0));
		assertEquals(25d, (double) simpleMultiplier.getValue(30));
	}
}
