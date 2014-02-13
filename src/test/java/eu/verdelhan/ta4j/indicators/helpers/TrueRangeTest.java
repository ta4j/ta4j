package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.TrueRange;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class TrueRangeTest {

	@Test
	public void testGetValue() {
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(0, 12, 15, 8));
		ticks.add(new MockTick(0, 8, 11, 6));
		ticks.add(new MockTick(0, 15, 17, 14));
		ticks.add(new MockTick(0, 15, 17, 14));
		ticks.add(new MockTick(0, 0, 0, 2));
		TrueRange tr = new TrueRange(new MockTimeSeries(ticks));
		
		assertThat(tr.getValue(0)).isEqualTo(7d);
		assertThat(tr.getValue(1)).isEqualTo(6d);
		assertThat(tr.getValue(2)).isEqualTo(9d);
		assertThat(tr.getValue(3)).isEqualTo(3d);
		assertThat(tr.getValue(4)).isEqualTo(15d);
		
	}

}
