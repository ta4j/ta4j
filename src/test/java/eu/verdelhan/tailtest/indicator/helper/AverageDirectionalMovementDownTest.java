package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class AverageDirectionalMovementDownTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		DefaultTick tick1 = new DefaultTick(0, 0, 13, 7);
		DefaultTick tick2 = new DefaultTick(0, 0, 11, 5);
		DefaultTick tick3 = new DefaultTick(0, 0, 15, 3);
		DefaultTick tick4 = new DefaultTick(0, 0, 14, 2);
		DefaultTick tick5 = new DefaultTick(0, 0, 13, 0.2);
		
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(tick1);
		ticks.add(tick2);
		ticks.add(tick3);
		ticks.add(tick4);
		ticks.add(tick5);
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		AverageDirectionalMovementDown admdown = new AverageDirectionalMovementDown(series, 3);
		assertEquals((double) 1d, (double) admdown.getValue(0));
		assertEquals((double) 1d * 2d/3 +2d / 3, (double) admdown.getValue(1));
		assertEquals((double) (1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0, (double) admdown.getValue(2));
		assertEquals((double) ((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3, (double) admdown.getValue(3));
		assertEquals((double) (((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3, (double) admdown.getValue(4));
	}
}
