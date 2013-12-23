package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;


public class AverageTrueRangeIndicatorTest {
	@Test
	public void testGetValue() {
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 12, 15, 8));
		ticks.add(new DefaultTick(0, 8, 11, 6));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 0, 0, 2));
		AverageTrueRangeIndicator atr = new AverageTrueRangeIndicator(new SampleTimeSeries(ticks), 3);
		
		assertEquals(1d, atr.getValue(0));
		assertEquals(2d/3 + 6d/3, atr.getValue(1));
		assertEquals((2d/3 + 6d/3) * 2d/3 + 9d/3, atr.getValue(2));
		assertEquals(((2d/3 + 6d/3) * 2d/3 + 9d/3) * 2d/3 + 3d/3, atr.getValue(3));
		assertEquals((((2d/3 + 6d/3) * 2d/3 + 9d/3) * 2d/3 + 3d/3) * 2d/3 + 15d/3, atr.getValue(4));
		
	}
}
