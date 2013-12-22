package net.sf.tail.indicator.volume;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Test;

public class OnBalanceVolumeIndicatorTest {
	@Test
	public void testGetValue()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(null, 0, 10, 0, 0, 0, 0, 0, 4, 0));
		ticks.add(new DefaultTick(null, 0, 5, 0, 0, 0, 0, 0, 2, 0));
		ticks.add(new DefaultTick(null, 0, 6, 0, 0, 0, 0, 0, 3, 0));
		ticks.add(new DefaultTick(null, 0, 7, 0, 0, 0, 0, 0, 8, 0));
		ticks.add(new DefaultTick(null, 0, 7, 0, 0, 0, 0, 0, 6, 0));
		ticks.add(new DefaultTick(null, 0, 6, 0, 0, 0, 0, 0, 10, 0));
		OnBalanceVolumeIndicator onBalance = new OnBalanceVolumeIndicator(new SampleTimeSeries(ticks));
		
		assertEquals(0d, onBalance.getValue(0));
		assertEquals(-2d, onBalance.getValue(1));
		assertEquals(1d, onBalance.getValue(2));
		assertEquals(9d, onBalance.getValue(3));
		assertEquals(9d, onBalance.getValue(4));
		assertEquals(-1d, onBalance.getValue(5));

	}
}
