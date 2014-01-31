package eu.verdelhan.tailtest.indicator.oscillator;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.AverageHighLow;
import eu.verdelhan.tailtest.mocks.MockTick;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AwesomeOscillatorTest {
	private TimeSeries series;

	@Before
	public void setUp() throws Exception {

		List<Tick> ticks = new ArrayList<Tick>();

		ticks.add(new MockTick(0, 0, 16, 8));//12
		ticks.add(new MockTick(0, 0, 12, 6));//9
		ticks.add(new MockTick(0, 0, 18, 14));//16
		ticks.add(new MockTick(0, 0, 10, 6));//8
		ticks.add(new MockTick(0, 0, 8, 4));//6

		this.series = new MockTimeSeries(ticks);
	}

	@Test
	public void testCalculateWithSma2AndSma3() throws Exception {
		AwesomeOscillator awesome = new AwesomeOscillator(new AverageHighLow(series), 2, 3);

		assertEquals(0d, (double) awesome.getValue(0));
		assertEquals(0d, (double) awesome.getValue(1));
		assertEquals(0.1666666d, awesome.getValue(2), 0.001);
		assertEquals(1d, awesome.getValue(3), 0.001);
		assertEquals(-3d, (double) awesome.getValue(4));
	}

	@Test
	public void testWithSma1AndSma2() throws Exception {
		AwesomeOscillator awesome = new AwesomeOscillator(new AverageHighLow(series), 1, 2);

		assertEquals(0d, (double) awesome.getValue(0));
		assertEquals(-1.5d, (double) awesome.getValue(1));
		assertEquals(3.5d, (double) awesome.getValue(2));
		assertEquals(-4d, (double) awesome.getValue(3));
		assertEquals(-1d, (double) awesome.getValue(4));
	}

	@Test
	public void testWithSmaDefault() throws Exception {
		AwesomeOscillator awesome = new AwesomeOscillator(new AverageHighLow(series));

		assertEquals(0d, (double) awesome.getValue(0));
		assertEquals(0d, (double) awesome.getValue(1));
		assertEquals(0d, (double) awesome.getValue(2));
		assertEquals(0d, (double) awesome.getValue(3));
		assertEquals(0d, (double) awesome.getValue(4));
	}

}
