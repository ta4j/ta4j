package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.indicators.trackers.ParabolicSar;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
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
		
		assertThat(sar.getValue(0)).isEqualByComparingTo("10");
		assertThat(sar.getValue(1)).isEqualByComparingTo("8");
		assertThat(sar.getValue(2)).isEqualByComparingTo("11");
		assertThat(sar.getValue(3)).isEqualByComparingTo("4");
		assertThat(sar.getValue(4)).isEqualByComparingTo("4");
		
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
		
		assertThat(sar.getValue(0)).isEqualByComparingTo("10");
		assertThat(sar.getValue(1)).isEqualByComparingTo("10");
		assertThat(sar.getValue(2)).isEqualByComparingTo(0.04 * (18d - 10) + 10d + "");
		assertThat(sar.getValue(3)).isEqualByComparingTo("18");
		assertThat(sar.getValue(3)).isEqualByComparingTo("18");
		assertThat(sar.getValue(4)).isEqualByComparingTo("18");
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
		
		assertThat(sar.getValue(0)).isEqualByComparingTo("10");
		assertThat(sar.getValue(1)).isEqualByComparingTo("17");
		assertThat(sar.getValue(2)).isEqualByComparingTo("11.38");
		assertThat(sar.getValue(3)).isEqualByComparingTo("11.38");
		assertThat(sar.getValue(4)).isEqualByComparingTo("18");
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
		
		assertThat(sar.getValue(0)).isEqualByComparingTo("20");
		assertThat(sar.getValue(1)).isEqualByComparingTo("19");
		assertThat(sar.getValue(2)).isEqualByComparingTo(0.04d * (14d - 19d) + 19d + "");
		double value = 0.06d * (11.38d - 18.8d) + 18.8d;
		assertThat(sar.getValue(3)).isEqualByComparingTo(value + "");
		assertThat(sar.getValue(4)).isEqualByComparingTo(0.08d * (11d - value) + value + "");
		assertThat(sar.getValue(5)).isEqualByComparingTo("11");
		
	}
}