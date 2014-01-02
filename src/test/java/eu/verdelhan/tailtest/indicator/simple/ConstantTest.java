package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class ConstantTest {
	private Constant<Double> constantIndicator;

	@Before
	public void setUp() {

		constantIndicator = new Constant<Double>(30.33);
	}

	@Test
	public void testConstantIndicator() {
		assertEquals(30.33, (double) constantIndicator.getValue(10));
		assertEquals(30.33, (double) constantIndicator.getValue(1));
		assertEquals(30.33, (double) constantIndicator.getValue(0));
		assertEquals(30.33, (double) constantIndicator.getValue(30));
	}

	@Test
	public void testGetName() {
		assertEquals("ConstantIndicator Value: 30.33", constantIndicator.getName());
	}
}
