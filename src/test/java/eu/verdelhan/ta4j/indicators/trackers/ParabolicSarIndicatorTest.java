package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class ParabolicSarIndicatorTest {

	@Test
	public void trendSwitchTest()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 10, 13, 8));
		ticks.add(new MockTick(0, 8, 11, 6));
		ticks.add(new MockTick(0, 6, 9, 4));
		ticks.add(new MockTick(0, 11, 15, 9));
		ticks.add(new MockTick(0, 13, 15, 9));
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);
		
		assertThat(sar.getValue(0)).isEqualTo(10d);
		assertThat(sar.getValue(1)).isEqualTo(8d);
		assertThat(sar.getValue(2)).isEqualTo(11d);
		assertThat(sar.getValue(3)).isEqualTo(4d);
		assertThat(sar.getValue(4)).isEqualTo(4d);
		
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
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);
		
		assertThat(sar.getValue(0)).isEqualTo(10d);
		assertThat(sar.getValue(1)).isEqualTo(10d);
		assertThat(sar.getValue(2)).isEqualTo(0.04 * (18d - 10) + 10d);
		assertThat(sar.getValue(3)).isEqualTo(18d);
		assertThat(sar.getValue(3)).isEqualTo(18d);
		assertThat(sar.getValue(4)).isEqualTo(18d);
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
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);
		
		assertThat(sar.getValue(0)).isEqualTo(10d);
		assertThat(sar.getValue(1)).isEqualTo(17d);
		assertThat(sar.getValue(2)).isEqualTo(11.38d);
		assertThat(sar.getValue(3)).isEqualTo(11.38d);
		assertThat(sar.getValue(4)).isEqualTo(18d);
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
		
		ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockTimeSeries(ticks), 1);
		
		assertThat(sar.getValue(0)).isEqualTo(20d);
		assertThat(sar.getValue(1)).isEqualTo(19d);
		assertThat(sar.getValue(2)).isEqualTo(0.04d * (14d - 19d) + 19d);
		double value = 0.06d * (11.38d - 18.8d) + 18.8d;
		assertThat(sar.getValue(3)).isEqualTo(value);
		assertThat(sar.getValue(4)).isEqualTo(0.08d * (11d - value) + value);
		assertThat(sar.getValue(5)).isEqualTo(11d);
		
	}
}