package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.indicators.trackers.DirectionalMovementIndicator;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class DirectionalMovementIndicatorTest {

	@Test
	public void testGetValue()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		
		ticks.add(new MockTick(0, 0, 10, 2));
		ticks.add(new MockTick(0, 0, 12, 2));
		ticks.add(new MockTick(0, 0, 15, 2));
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalMovementIndicator dm = new DirectionalMovementIndicator(series, 3);
		assertThat(dm.getValue(0)).isEqualTo(0d);
		double dup = (2d / 3 + 2d/3) / (2d/3 + 12d/3);
		double ddown = (2d/3) /(2d/3 + 12d/3);
		assertThat(dm.getValue(1)).isEqualTo( (dup - ddown) /(dup + ddown) * 100d );
		dup = ((2d / 3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3);
		ddown = (4d/9) /((2d/3 + 12d/3) * 2d/3 + 15d/3);
		assertThat(dm.getValue(2)).isEqualTo( (dup - ddown) /(dup + ddown) * 100d );
	}
}
