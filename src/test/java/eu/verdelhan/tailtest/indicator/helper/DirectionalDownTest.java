package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class DirectionalDownTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 0, 13, 7));
		ticks.add(new DefaultTick(0, 0, 11, 5));
		ticks.add(new DefaultTick(0, 0, 15, 3));
		ticks.add(new DefaultTick(0, 0, 14, 2));
		ticks.add(new DefaultTick(0, 0, 13, 0.2));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalDown ddown = new DirectionalDown(series, 3);
		assertEquals((double) 1d, (double) ddown.getValue(0));
		assertEquals((double) (1d * 2d/3 +2d / 3) / (2d/3 + 11d/3), (double) ddown.getValue(1));
		assertEquals((double) ((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) / (((2d/3 + 11d/3) * 2d/3) + 15d/3), (double) ddown.getValue(2));
		assertEquals((double) (((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) / (((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3), (double) ddown.getValue(3));
		assertEquals((double) ((((1d * 2d/3 + 2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3) / (((((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3) * 2d/3) + 13d/3), (double) ddown.getValue(4));
	}
}
