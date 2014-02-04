package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.indicator.tracker.ParabolicSar;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ParabolicSarTest {

	@Test
	public void trendSwitchTest()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 10, 13, 8));
		ticks.add(new MockTick(0, 8, 11, 6));
		ticks.add(new MockTick(0, 6, 9, 4));
		ticks.add(new MockTick(0, 11, 15, 9));
		ticks.add(new MockTick(0, 13, 15, 9));
		ParabolicSar sar = new ParabolicSar(new MockTimeSeries(ticks), 1);
		
		assertEquals(BigDecimal.valueOf(10d), sar.getValue(0));
		assertEquals(BigDecimal.valueOf(8d), sar.getValue(1));
		assertEquals(BigDecimal.valueOf(11d), sar.getValue(2));
		assertEquals(BigDecimal.valueOf(4d), sar.getValue(3));
		assertEquals(BigDecimal.valueOf(4d), sar.getValue(4));
		
	}
	
	@Test
	public void TrendSwitchTest2()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 10, 13, 11));
		ticks.add(new MockTick(0, 10, 15, 13));
		ticks.add(new MockTick(0, 12, 18, 11));
		ticks.add(new MockTick(0, 10, 15, 9));
		ticks.add(new MockTick(0, 9, 15, 9));
		
		ParabolicSar sar = new ParabolicSar(new MockTimeSeries(ticks), 1);
		
		assertEquals(BigDecimal.valueOf(10d), sar.getValue(0));
		assertEquals(BigDecimal.valueOf(10d), sar.getValue(1));
		assertEquals(BigDecimal.valueOf(0.04 * (18d - 10) + 10d), sar.getValue(2));
		assertEquals(BigDecimal.valueOf(18d), sar.getValue(3));
		assertEquals(BigDecimal.valueOf(18d), sar.getValue(3));
		assertEquals(BigDecimal.valueOf(18d), sar.getValue(4));
	}
	@Test
	public void UpTrendTest()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 10, 13, 11));
		ticks.add(new MockTick(0, 17, 15, 11.38));
		ticks.add(new MockTick(0, 18, 16, 14));
		ticks.add(new MockTick(0, 19, 17, 12));
		ticks.add(new MockTick(0, 20, 18, 9));
		
		ParabolicSar sar = new ParabolicSar(new MockTimeSeries(ticks), 1);
		
		assertEquals(BigDecimal.valueOf(10d), sar.getValue(0));
		assertEquals(BigDecimal.valueOf(17d), sar.getValue(1));
		assertEquals(BigDecimal.valueOf(11.38d), sar.getValue(2));
		assertEquals(BigDecimal.valueOf(11.38d), sar.getValue(3));
		assertEquals(BigDecimal.valueOf(18d), sar.getValue(4));
	}
	
	@Test
	public void DownTrendTest()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 20, 18, 9));
		ticks.add(new MockTick(0, 19, 17, 12));
		ticks.add(new MockTick(0, 18, 16, 14));
		ticks.add(new MockTick(0, 17, 15, 11.38));
		ticks.add(new MockTick(0, 10, 13, 11));
		ticks.add(new MockTick(0, 10, 30, 11));
		
		ParabolicSar sar = new ParabolicSar(new MockTimeSeries(ticks), 1);
		
		assertEquals(BigDecimal.valueOf(20d), sar.getValue(0));
		assertEquals(BigDecimal.valueOf(19d), sar.getValue(1));
		assertEquals(BigDecimal.valueOf(0.04d * (14d - 19d) + 19d), sar.getValue(2));
		double value = 0.06d * (11.38d - 18.8d) + 18.8d;
		assertEquals(BigDecimal.valueOf(value), sar.getValue(3));
		assertEquals(BigDecimal.valueOf(0.08d * (11d - value) + value), sar.getValue(4));
		assertEquals(BigDecimal.valueOf(11d), sar.getValue(5));
		
	}
}
