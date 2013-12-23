package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;


public class DirectionalDownIndicatorTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 0, 13, 7));
		ticks.add(new DefaultTick(0, 0, 11, 5));
		ticks.add(new DefaultTick(0, 0, 15, 3));
		ticks.add(new DefaultTick(0, 0, 14, 2));
		ticks.add(new DefaultTick(0, 0, 13, 0.2));
		
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		DirectionalDownIndicator ddown = new DirectionalDownIndicator(series, 3);
		assertEquals(1d, ddown.getValue(0));
		assertEquals((1d * 2d/3 +2d / 3) / (2d/3 + 11d/3), ddown.getValue(1));
		assertEquals(((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) / (((2d/3 + 11d/3) * 2d/3) + 15d/3), ddown.getValue(2));
		assertEquals((((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) / (((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3), ddown.getValue(3));
		assertEquals(((((1d * 2d/3 + 2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3) / (((((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3) * 2d/3) + 13d/3), ddown.getValue(4));
	}
}
