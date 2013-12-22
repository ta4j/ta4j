package net.sf.tail.indicator.helper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Test;

public class TrueRangeIndicatorTest {

	@Test
	public void testGetValue() {
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 12, 15, 8));
		ticks.add(new DefaultTick(0, 8, 11, 6));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 0, 0, 2));
		TrueRangeIndicator tr = new TrueRangeIndicator(new SampleTimeSeries(ticks));
		
		assertEquals(7d, tr.getValue(0));
		assertEquals(6d, tr.getValue(1));
		assertEquals(9d, tr.getValue(2));
		assertEquals(3d, tr.getValue(3));
		assertEquals(15d, tr.getValue(4));
		
	}

}
