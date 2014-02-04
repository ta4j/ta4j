package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.indicator.helper.DirectionalMovementDown;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class DirectionalMovementDownTest {

	
	@Test
	public void testZeroDirectionalMovement()
	{
		MockTick yesterdayTick = new MockTick(0, 0, 10, 2);
		MockTick todayTick = new MockTick(0, 0, 6, 6);
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	
	@Test
	public void testZeroDirectionalMovement2()
	{
		MockTick yesterdayTick = new MockTick(0, 0, 6, 12);
		MockTick todayTick = new MockTick(0, 0, 12, 6);
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	@Test
	public void testZeroDirectionalMovement3()
	{
		MockTick yesterdayTick = new MockTick(0, 0, 6, 6);
		MockTick todayTick = new MockTick(0, 0, 12, 4);
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(0d, (double) down.getValue(1));
	}
	@Test
	public void testPositiveDirectionalMovement()
	{
		MockTick yesterdayTick = new MockTick(0, 0, 6, 20);
		MockTick todayTick = new MockTick(0, 0, 12, 4);
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(yesterdayTick);
		ticks.add(todayTick);
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalMovementDown down = new DirectionalMovementDown(series);
		assertEquals(16d, (double) down.getValue(1));
	}
}
