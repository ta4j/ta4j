package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.DirectionalDown;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class DirectionalDownTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 0, 13, 7));
		ticks.add(new MockTick(0, 0, 11, 5));
		ticks.add(new MockTick(0, 0, 15, 3));
		ticks.add(new MockTick(0, 0, 14, 2));
		ticks.add(new MockTick(0, 0, 13, 0.2));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalDown ddown = new DirectionalDown(series, 3);
		assertThat(ddown.getValue(0)).isEqualTo((double) 1d);
		assertThat(ddown.getValue(1)).isEqualTo((double) (1d * 2d/3 +2d / 3) / (2d/3 + 11d/3));
		assertThat(ddown.getValue(2)).isEqualTo((double) ((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) / (((2d/3 + 11d/3) * 2d/3) + 15d/3));
		assertThat(ddown.getValue(3)).isEqualTo((double) (((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) / (((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3));
		assertThat(ddown.getValue(4)).isEqualTo((double) ((((1d * 2d/3 + 2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3) / (((((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3) * 2d/3) + 13d/3));
	}
}
