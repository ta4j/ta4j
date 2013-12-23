package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class ConstantIndicatorTest {
	private ConstantIndicator<Double> constantIndicator;

	@Before
	public void setUp() {

		constantIndicator = new ConstantIndicator<Double>(30.33);
	}

	@Test
	public void testConstantIndicator() {
		assertEquals(30.33, constantIndicator.getValue(10));
		assertEquals(30.33, constantIndicator.getValue(1));
		assertEquals(30.33, constantIndicator.getValue(0));
		assertEquals(30.33, constantIndicator.getValue(30));
	}

	@Test
	public void testGetName() {
		assertEquals("ConstantIndicator Value: 30.33", constantIndicator.getName());
	}
}
