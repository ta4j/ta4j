package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Before;
import org.junit.Test;

public class AverageHighLowIndicatorTest {
	private AverageHighLowIndicator average;

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
		

		this.timeSeries = new SampleTimeSeries(ticks);
		average = new AverageHighLowIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickClosePrice() {
		double result;
		for (int i = 0; i < 10; i++) {
			result = (timeSeries.getTick(i).getMaxPrice() + timeSeries.getTick(i).getMinPrice()) / 2;  
			assertEquals(average.getValue(i), result);
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		average.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("AverageHighLowIndicator", average.getName());
	}
}
