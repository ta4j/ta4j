package eu.verdelhan.tailtest.indicator.volume;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.mocks.MockTick;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;

public class OnBalanceVolumeTest {
	@Test
	public void testGetValue()
	{
		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(null, 0, 10, 0, 0, 0, 0, 4, 0));
		ticks.add(new MockTick(null, 0, 5, 0, 0, 0, 0, 2, 0));
		ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 0, 3, 0));
		ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 0, 8, 0));
		ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 0, 6, 0));
		ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 0, 10, 0));
		OnBalanceVolume onBalance = new OnBalanceVolume(new MockTimeSeries(ticks));
		
		assertEquals(0d, onBalance.getValue(0));
		assertEquals(-2d, onBalance.getValue(1));
		assertEquals(1d, onBalance.getValue(2));
		assertEquals(9d, onBalance.getValue(3));
		assertEquals(9d, onBalance.getValue(4));
		assertEquals(-1d, onBalance.getValue(5));

	}
}
