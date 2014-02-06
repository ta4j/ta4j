package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class AverageDirectionalMovementTest {
	@Test
	public void testGetValue()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		
		ticks.add(new MockTick(0, 0, 10, 2));
		ticks.add(new MockTick(0, 0, 12, 2));
		ticks.add(new MockTick(0, 0, 15, 2));
		MockTimeSeries series = new MockTimeSeries(ticks);
		AverageDirectionalMovement adm = new AverageDirectionalMovement(series, 3);
		
		assertThat(adm.getValue(0)).isEqualTo(1d);
		double dup = (2d / 3 + 2d/3) / (2d/3 + 12d/3);
		double ddown = (2d/3) /(2d/3 + 12d/3);
		double firstdm = (dup - ddown) / (dup + ddown) * 100;
		assertThat(adm.getValue(1)).isEqualTo( 2d/3 + firstdm / 3);
		dup = ((2d / 3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3);
		ddown = (4d/9) /((2d/3 + 12d/3) * 2d/3 + 15d/3);
		double secondDm = (dup - ddown) / (dup + ddown) * 100;
		assertThat(adm.getValue(2)).isEqualTo( (2d/3 + firstdm / 3) * 2d/3 + secondDm / 3);

	}
}
