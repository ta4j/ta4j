package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AverageHighLowTest {
	private AverageHighLow average;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();

		ticks.add(new DefaultTick(0, 0, 16, 8));
		ticks.add(new DefaultTick(0, 0, 12, 6));
		ticks.add(new DefaultTick(0, 0, 18, 14));
		ticks.add(new DefaultTick(0, 0, 10, 6));
		ticks.add(new DefaultTick(0, 0, 32, 6));
		ticks.add(new DefaultTick(0, 0, 2, 2));
		ticks.add(new DefaultTick(0, 0, 0, 0));
		ticks.add(new DefaultTick(0, 0, 8, 1));
		ticks.add(new DefaultTick(0, 0, 83, 32));
		ticks.add(new DefaultTick(0, 0, 9, 3));
		

		this.timeSeries = new MockTimeSeries(ticks);
		average = new AverageHighLow(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickClosePrice() {
		BigDecimal result;
		for (int i = 0; i < 10; i++) {
			result = timeSeries.getTick(i).getMaxPrice().add(timeSeries.getTick(i).getMinPrice()).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
			assertEquals(average.getValue(i), result);
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		average.getValue(10);
	}
}
