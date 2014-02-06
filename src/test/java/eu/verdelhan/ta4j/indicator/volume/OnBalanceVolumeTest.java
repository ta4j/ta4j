package eu.verdelhan.ta4j.indicator.volume;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class OnBalanceVolumeTest {
	@Test
	public void testGetValue()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(null, 0, 10, 0, 0, 0, 4, 0));
		ticks.add(new MockTick(null, 0, 5, 0, 0, 0, 2, 0));
		ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 3, 0));
		ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 8, 0));
		ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 6, 0));
		ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 10, 0));
		OnBalanceVolume onBalance = new OnBalanceVolume(new MockTimeSeries(ticks));
		
		assertThat(onBalance.getValue(0)).isEqualTo(0d);
		assertThat(onBalance.getValue(1)).isEqualTo(-2d);
		assertThat(onBalance.getValue(2)).isEqualTo(1d);
		assertThat(onBalance.getValue(3)).isEqualTo(9d);
		assertThat(onBalance.getValue(4)).isEqualTo(9d);
		assertThat(onBalance.getValue(5)).isEqualTo(-1d);

	}
}
