package eu.verdelhan.tailtest.indicator.volume;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.mocks.MockTick;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AccumulationDistributionTest {

	@Test
	public void testAccumulationDistribution()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(null, 0d, 10d, 12d, 8d, 0d, 0d, 200d,0));//2-2 * 200 / 4
		ticks.add(new MockTick(null, 0d, 8d, 10d, 7d, 0d, 0d, 100d,0));//1-2 *100 / 3
		ticks.add(new MockTick(null, 0d, 9d, 15d, 6d, 0d, 0d, 300d,0));//3-6 *300 /9
		ticks.add(new MockTick(null, 0d, 20d, 40d, 5d, 0d, 0d, 50d,0));//15-20 *50 / 35
		ticks.add(new MockTick(null, 0d, 30d, 30d, 3d, 0d, 0d, 600d,0));//27-0 *600 /27
		
		TimeSeries series = new MockTimeSeries(ticks);
		AccumulationDistribution ac = new AccumulationDistribution(series);
//		Assert.assertEquals(0d, ac.getValue(0));
//		assertEquals(-100d / 3, ac.getValue(1));
//		assertEquals(-100d -(100d / 3) , ac.getValue(2));
//		assertEquals((-250d/35) + (-100d -(100d / 3)), ac.getValue(3));
//		assertEquals(600d + ((-250d/35) + (-100d -(100d / 3))), ac.getValue(4));
		
	}
}
