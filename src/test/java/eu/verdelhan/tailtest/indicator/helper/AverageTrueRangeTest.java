package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class AverageTrueRangeTest {
	@Test
	public void testGetValue() {
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 12, 15, 8));
		ticks.add(new DefaultTick(0, 8, 11, 6));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 15, 17, 14));
		ticks.add(new DefaultTick(0, 0, 0, 2));
		AverageTrueRange atr = new AverageTrueRange(new MockTimeSeries(ticks), 3);
		
		assertEquals((double) 1d, (double) atr.getValue(0));
		assertEquals((double) 2d/3 + 6d/3, (double) atr.getValue(1));
		assertEquals((double) (2d/3 + 6d/3) * 2d/3 + 9d/3, (double) atr.getValue(2));
		assertEquals((double) ((2d/3 + 6d/3) * 2d/3 + 9d/3) * 2d/3 + 3d/3, (double) atr.getValue(3));
		assertEquals((double) (((2d/3 + 6d/3) * 2d/3 + 9d/3) * 2d/3 + 3d/3) * 2d/3 + 15d/3, (double) atr.getValue(4));
		
	}
}
