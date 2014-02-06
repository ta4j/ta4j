package eu.verdelhan.ta4j.indicator.simple;

import static org.assertj.core.api.Assertions.*;
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
		assertThat(simpleMultiplier.getValue(10)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(1)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(0)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(30)).isEqualTo(25d);
	}
}
