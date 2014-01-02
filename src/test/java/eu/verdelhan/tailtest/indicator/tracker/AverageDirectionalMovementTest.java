package eu.verdelhan.tailtest.indicator.tracker;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;


public class AverageDirectionalMovementTest {
	@Test
	public void testGetValue()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		
		ticks.add(new DefaultTick(0, 0, 10, 2));
		ticks.add(new DefaultTick(0, 0, 12, 2));
		ticks.add(new DefaultTick(0, 0, 15, 2));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		AverageDirectionalMovement adm = new AverageDirectionalMovement(series, 3);
		
		assertEquals(1d, adm.getValue(0));
		double dup = (2d / 3 + 2d/3) / (2d/3 + 12d/3);
		double ddown = (2d/3) /(2d/3 + 12d/3);
		double firstdm = (dup - ddown) / (dup + ddown) * 100;
		assertEquals( 2d/3 + firstdm / 3, adm.getValue(1));
		dup = ((2d / 3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3);
		ddown = (4d/9) /((2d/3 + 12d/3) * 2d/3 + 15d/3);
		double secondDm = (dup - ddown) / (dup + ddown) * 100;
		assertEquals( (2d/3 + firstdm / 3) * 2d/3 + secondDm / 3, adm.getValue(2));

	}
}
