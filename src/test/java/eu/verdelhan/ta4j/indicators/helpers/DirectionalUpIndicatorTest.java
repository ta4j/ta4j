package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.DirectionalUpIndicator;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class DirectionalUpIndicatorTest {
	
	@Test
	public void testGetValue()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		
		ticks.add(new MockTick(0, 0, 10, 2));
		ticks.add(new MockTick(0, 0, 12, 2));
		ticks.add(new MockTick(0, 0, 15, 2));
		ticks.add(new MockTick(0, 0, 11, 2));
		ticks.add(new MockTick(0, 0, 13, 7));
		
		MockTimeSeries series = new MockTimeSeries(ticks);
		DirectionalUpIndicator dup = new DirectionalUpIndicator(series, 3);
		assertThat(dup.getValue(0)).isEqualTo(1d);
		assertThat(dup.getValue(1)).isEqualTo((2d / 3 + 2d/3) / (2d/3 + 12d/3) );
		assertThat(dup.getValue(2)).isEqualTo(((2d / 3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3));
		assertThat(dup.getValue(3)).isEqualTo((((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 + 1d/3 * 0) / ((((2d/3 + 12d/3) * 2d/3 + 15d/3) * 2d/3) + 11d/3));
		assertThat(dup.getValue(4)).isEqualTo((((2d / 3 + 2d/3) * 2d/3 + 1) * 2d / 3 * 2d/3  + 2 * 1d / 3) / ((((((2d/3 + 12d/3) * 2d/3 + 15d/3) * 2d/3) + 11d/3) * 2d/3) + 13d/3));
	}
}
