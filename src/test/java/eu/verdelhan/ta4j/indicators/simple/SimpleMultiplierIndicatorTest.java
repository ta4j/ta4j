package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.ConstantIndicator;
import eu.verdelhan.ta4j.indicators.simple.SimpleMultiplierIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class SimpleMultiplierIndicatorTest {
	private ConstantIndicator<Double> constantIndicator;
	private SimpleMultiplierIndicator simpleMultiplier;

	@Before
	public void setUp() {
		constantIndicator = new ConstantIndicator<Double>(5d);
		simpleMultiplier = new SimpleMultiplierIndicator(constantIndicator, 5d);
	}

	@Test
	public void testConstantIndicator() {
		assertThat(simpleMultiplier.getValue(10)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(1)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(0)).isEqualTo(25d);
		assertThat(simpleMultiplier.getValue(30)).isEqualTo(25d);
	}
}
