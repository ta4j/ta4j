package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class DirectionalMovementDownTest {

	
	@Test
	public void testZeroDirectionalMovement()
	{
		DefaultTick yesterdayTick = new DefaultTick(0, 0, 10, 2);
		DefaultTick todayTick = new DefaultTick(0, 0, 6, 6);
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	
	@Test
	public void testZeroDirectionalMovement2()
	{
		DefaultTick yesterdayTick = new DefaultTick(0, 0, 6, 12);
		DefaultTick todayTick = new DefaultTick(0, 0, 12, 6);
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	@Test
	public void testZeroDirectionalMovement3()
	{
		DefaultTick yesterdayTick = new DefaultTick(0, 0, 6, 6);
		DefaultTick todayTick = new DefaultTick(0, 0, 12, 4);
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	@Test
	public void testPositiveDirectionalMovement()
	{
		DefaultTick yesterdayTick = new DefaultTick(0, 0, 6, 20);
		DefaultTick todayTick = new DefaultTick(0, 0, 12, 4);
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(16d, (double) down.getValue(1));
	}
}
