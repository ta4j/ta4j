package eu.verdelhan.tailtest.indicator.tracker;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;

public class ParabolicSarIndicatorTest {

	@Test
	public void trendSwitchTest()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 10, 13, 8));
		ticks.add(new DefaultTick(0, 8, 11, 6));
		ticks.add(new DefaultTick(0, 6, 9, 4));
		ticks.add(new DefaultTick(0, 11, 15, 9));
		ticks.add(new DefaultTick(0, 13, 15, 9));
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new SampleTimeSeries(ticks), 1);
		
		assertEquals(10d, sar.getValue(0));
		assertEquals(8d, sar.getValue(1));
		assertEquals(11d, sar.getValue(2));
		assertEquals(4d, sar.getValue(3));
		assertEquals(4d, sar.getValue(4));
		
	}
	
	@Test
	public void TrendSwitchTest2()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 10, 13, 11));
		ticks.add(new DefaultTick(0, 10, 15, 13));
		ticks.add(new DefaultTick(0, 12, 18, 11));
		ticks.add(new DefaultTick(0, 10, 15, 9));
		ticks.add(new DefaultTick(0, 9, 15, 9));
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new SampleTimeSeries(ticks), 1);
		
		assertEquals(10d, sar.getValue(0));
		assertEquals(10d, sar.getValue(1));
		assertEquals(0.04 * (18d - 10) + 10d, sar.getValue(2));
		assertEquals(18d, sar.getValue(3));
		assertEquals(18d, sar.getValue(3));
		assertEquals(18d, sar.getValue(4));
	}
	@Test
	public void UpTrendTest()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 10, 13, 11));
		ticks.add(new DefaultTick(0, 17, 15, 11.38));
		ticks.add(new DefaultTick(0, 18, 16, 14));
		ticks.add(new DefaultTick(0, 19, 17, 12));
		ticks.add(new DefaultTick(0, 20, 18, 9));
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new SampleTimeSeries(ticks), 1);
		
		assertEquals(10d, sar.getValue(0));
		assertEquals(17d, sar.getValue(1));
		assertEquals(11.38d, sar.getValue(2));
		assertEquals(11.38d, sar.getValue(3));
		assertEquals(18d, sar.getValue(4));
	}
	
	@Test
	public void DownTrendTest()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(0, 20, 18, 9));
		ticks.add(new DefaultTick(0, 19, 17, 12));
		ticks.add(new DefaultTick(0, 18, 16, 14));
		ticks.add(new DefaultTick(0, 17, 15, 11.38));
		ticks.add(new DefaultTick(0, 10, 13, 11));
		ticks.add(new DefaultTick(0, 10, 30, 11));
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new SampleTimeSeries(ticks), 1);
		
		assertEquals(20d, sar.getValue(0));
		assertEquals(19d, sar.getValue(1));
		assertEquals(0.04d * (14d - 19d) + 19d, sar.getValue(2));
		double value = 0.06d * (11.38d - 18.8d) + 18.8d;
		assertEquals(0.06d * (11.38d - 18.8d) + 18.8d, sar.getValue(3));
		assertEquals(0.08d * (11d - value) + value, sar.getValue(4));
		assertEquals(11d, sar.getValue(5));
		
	}
}
