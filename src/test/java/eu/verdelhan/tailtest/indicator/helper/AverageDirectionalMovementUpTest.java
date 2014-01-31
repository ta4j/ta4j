package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.mocks.MockTick;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class AverageDirectionalMovementUpTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 0, 10, 2));
		ticks.add(new MockTick(0, 0, 12, 2));
		ticks.add(new MockTick(0, 0, 15, 2));
		ticks.add(new MockTick(0, 0, 11, 2));
		ticks.add(new MockTick(0, 0, 13, 7));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		AverageDirectionalMovementUp admup = new AverageDirectionalMovementUp(series, 3);
		assertEquals((double) 1d, (double) admup.getValue(0));
		assertEquals((double) 2d / 3 + 2d/3 , (double) admup.getValue(1));
		assertEquals((double) (2d / 3 + 2d/3) * 2d/3 + 1, (double) admup.getValue(2));
		assertEquals((double) ((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 + 1d/3 * 0, (double) admup.getValue(3));
		assertEquals((double) ((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 * 2d/3  + 2 * 1d / 3, (double) admup.getValue(4));
	}
}
