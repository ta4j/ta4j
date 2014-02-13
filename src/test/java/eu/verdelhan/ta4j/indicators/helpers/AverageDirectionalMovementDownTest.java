package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.AverageDirectionalMovementDown;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class AverageDirectionalMovementDownTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		MockTick tick1 = new MockTick(0, 0, 13, 7);
		MockTick tick2 = new MockTick(0, 0, 11, 5);
		MockTick tick3 = new MockTick(0, 0, 15, 3);
		MockTick tick4 = new MockTick(0, 0, 14, 2);
		MockTick tick5 = new MockTick(0, 0, 13, 0.2);
		
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(tick1);
		ticks.add(tick2);
		ticks.add(tick3);
		ticks.add(tick4);
		ticks.add(tick5);
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		AverageDirectionalMovementDown admdown = new AverageDirectionalMovementDown(series, 3);
		assertThat(admdown.getValue(0)).isEqualTo((double) 1d);
		assertThat(admdown.getValue(1)).isEqualTo((double) 1d * 2d/3 +2d / 3);
		assertThat(admdown.getValue(2)).isEqualTo((double) (1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0);
		assertThat(admdown.getValue(3)).isEqualTo((double) ((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3);
		assertThat(admdown.getValue(4)).isEqualTo((double) (((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3);
	}
}
