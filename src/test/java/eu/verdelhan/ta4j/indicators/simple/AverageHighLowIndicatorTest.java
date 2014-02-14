package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.AverageHighLowIndicator;
import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AverageHighLowIndicatorTest {
	private AverageHighLowIndicator average;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		List<Tick> ticks = new ArrayList<Tick>();

		ticks.add(new MockTick(0, 0, 16, 8));
		ticks.add(new MockTick(0, 0, 12, 6));
		ticks.add(new MockTick(0, 0, 18, 14));
		ticks.add(new MockTick(0, 0, 10, 6));
		ticks.add(new MockTick(0, 0, 32, 6));
		ticks.add(new MockTick(0, 0, 2, 2));
		ticks.add(new MockTick(0, 0, 0, 0));
		ticks.add(new MockTick(0, 0, 8, 1));
		ticks.add(new MockTick(0, 0, 83, 32));
		ticks.add(new MockTick(0, 0, 9, 3));
		

		this.timeSeries = new MockTimeSeries(ticks);
		average = new AverageHighLowIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickClosePrice() {
		BigDecimal result;
		for (int i = 0; i < 10; i++) {
			result = timeSeries.getTick(i).getMaxPrice().add(timeSeries.getTick(i).getMinPrice()).divide(BigDecimal.valueOf(2), TAUtils.MATH_CONTEXT);
			assertThat(result).isEqualTo(average.getValue(i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		average.getValue(10);
	}
}
