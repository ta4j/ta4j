package net.sf.tail.indicator.helper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Test;


public class AverageDirectionalMovementUpIndicatorTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 0, 10, 2));
		ticks.add(new DefaultTick(0, 0, 12, 2));
		ticks.add(new DefaultTick(0, 0, 15, 2));
		ticks.add(new DefaultTick(0, 0, 11, 2));
		ticks.add(new DefaultTick(0, 0, 13, 7));
		
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		AverageDirectionalMovementUpIndicator admup = new AverageDirectionalMovementUpIndicator(series, 3);
		assertEquals(1d, admup.getValue(0));
		assertEquals(2d / 3 + 2d/3 , admup.getValue(1));
		assertEquals((2d / 3 + 2d/3) * 2d/3 + 1, admup.getValue(2));
		assertEquals(((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 + 1d/3 * 0, admup.getValue(3));
		assertEquals(((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 * 2d/3  + 2 * 1d / 3, admup.getValue(4));
	}
}
