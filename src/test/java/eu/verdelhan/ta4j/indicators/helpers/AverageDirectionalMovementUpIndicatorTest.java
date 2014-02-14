package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.AverageDirectionalMovementUpIndicator;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class AverageDirectionalMovementUpIndicatorTest {
	
	@Test
	public void testAverageDirectionalMovement()
	{
		
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 0, 10, 2));
		ticks.add(new MockTick(0, 0, 12, 2));
		ticks.add(new MockTick(0, 0, 15, 2));
		ticks.add(new MockTick(0, 0, 11, 2));
		ticks.add(new MockTick(0, 0, 13, 7));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		AverageDirectionalMovementUpIndicator admup = new AverageDirectionalMovementUpIndicator(series, 3);
		assertThat(admup.getValue(0)).isEqualTo((double) 1d);
		assertThat(admup.getValue(1)).isEqualTo((double) 2d / 3 + 2d/3 );
		assertThat(admup.getValue(2)).isEqualTo((double) (2d / 3 + 2d/3) * 2d/3 + 1);
		assertThat(admup.getValue(3)).isEqualTo((double) ((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 + 1d/3 * 0);
		assertThat(admup.getValue(4)).isEqualTo((double) ((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 * 2d/3  + 2 * 1d / 3);
	}
}
