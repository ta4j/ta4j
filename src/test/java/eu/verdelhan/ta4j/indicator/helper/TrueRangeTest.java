package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.indicator.helper.TrueRange;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
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
		
		assertEquals(7d, (double) tr.getValue(0));
		assertEquals(6d, (double) tr.getValue(1));
		assertEquals(9d, (double) tr.getValue(2));
		assertEquals(3d, (double) tr.getValue(3));
		assertEquals(15d, (double) tr.getValue(4));
		
	}

}
