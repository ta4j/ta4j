package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.Constant;
import static org.assertj.core.api.Assertions.*;
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
		assertThat(constantIndicator.getValue(10)).isEqualTo(30.33);
		assertThat(constantIndicator.getValue(1)).isEqualTo(30.33);
		assertThat(constantIndicator.getValue(0)).isEqualTo(30.33);
		assertThat(constantIndicator.getValue(30)).isEqualTo(30.33);
	}
}
