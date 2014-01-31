package eu.verdelhan.tailtest.indicator.helper;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;


public class DirectionalUpTest {
	
	@Test
	public void testGetValue()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		
		ticks.add(new DefaultTick(0, 0, 10, 2));
		ticks.add(new DefaultTick(0, 0, 12, 2));
		ticks.add(new DefaultTick(0, 0, 15, 2));
		ticks.add(new DefaultTick(0, 0, 11, 2));
		ticks.add(new DefaultTick(0, 0, 13, 7));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalUp dup = new DirectionalUp(series, 3);
		assertEquals(1d, dup.getValue(0));
		assertEquals((2d / 3 + 2d/3) / (2d/3 + 12d/3) , dup.getValue(1));
		assertEquals(((2d / 3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3), dup.getValue(2));
		assertEquals((((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 + 1d/3 * 0) / ((((2d/3 + 12d/3) * 2d/3 + 15d/3) * 2d/3) + 11d/3), dup.getValue(3));
		assertEquals((((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 * 2d/3  + 2 * 1d / 3) / ((((((2d/3 + 12d/3) * 2d/3 + 15d/3) * 2d/3) + 11d/3) * 2d/3) + 13d/3), dup.getValue(4));
	}
}
