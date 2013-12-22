package net.sf.tail.indicator.oscilator;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.simple.AverageHighLowIndicator;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Before;
import org.junit.Test;

public class AwesomeOscillatorIndicatorTest {
	private TimeSeries series;

	@Before
	public void setUp() throws Exception {

		List<DefaultTick> ticks = new ArrayList<DefaultTick>();

		ticks.add(new DefaultTick(0, 0, 16, 8));//12
		ticks.add(new DefaultTick(0, 0, 12, 6));//9
		ticks.add(new DefaultTick(0, 0, 18, 14));//16
		ticks.add(new DefaultTick(0, 0, 10, 6));//8
		ticks.add(new DefaultTick(0, 0, 8, 4));//6

		this.series = new SampleTimeSeries(ticks);
	}

	@Test
	public void testCalculateWithSma2AndSma3() throws Exception {
		AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new AverageHighLowIndicator(series), 2, 3);

		assertEquals(0d, awesome.getValue(0));
		assertEquals(0d, awesome.getValue(1));
		assertEquals(0.1666666d, awesome.getValue(2), 0.001);
		assertEquals(1d, awesome.getValue(3), 0.001);
		assertEquals(-3d, awesome.getValue(4));
	}

	@Test
	public void testWithSma1AndSma2() throws Exception {
		AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new AverageHighLowIndicator(series), 1, 2);

		assertEquals(0d, awesome.getValue(0));
		assertEquals(-1.5d, awesome.getValue(1));
		assertEquals(3.5d, awesome.getValue(2));
		assertEquals(-4d, awesome.getValue(3));
		assertEquals(-1d, awesome.getValue(4));
	}

	@Test
	public void testWithSmaDefault() throws Exception {
		AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new AverageHighLowIndicator(series));

		assertEquals(0d, awesome.getValue(0));
		assertEquals(0d, awesome.getValue(1));
		assertEquals(0d, awesome.getValue(2));
		assertEquals(0d, awesome.getValue(3));
		assertEquals(0d, awesome.getValue(4));
	}

}
