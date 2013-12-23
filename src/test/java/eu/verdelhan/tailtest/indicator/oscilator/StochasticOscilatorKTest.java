package eu.verdelhan.tailtest.indicator.oscilator;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.series.DefaultTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Before;
import org.junit.Test;

public class StochasticOscilatorKTest {

	private TimeSeries data;

	@Before
	public void setUp() {

		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(44.98, 119.13, 119.50, 116.00));
		ticks.add(new DefaultTick(45.05, 116.75, 119.94, 116.00));
		ticks.add(new DefaultTick(45.11, 113.50, 118.44, 111.63));
		ticks.add(new DefaultTick(45.19, 111.56, 114.19, 110.06));
		ticks.add(new DefaultTick(45.12, 112.25, 112.81, 109.63));
		ticks.add(new DefaultTick(45.15, 110.00, 113.44, 109.13));
		ticks.add(new DefaultTick(45.13, 113.50, 115.81, 110.38));
		ticks.add(new DefaultTick(45.12, 117.13, 117.50, 114.06));
		ticks.add(new DefaultTick(45.15, 115.63, 118.44, 114.81));
		ticks.add(new DefaultTick(45.24, 114.13, 116.88, 113.13));
		ticks.add(new DefaultTick(45.43, 118.81, 119.00, 116.19));
		ticks.add(new DefaultTick(45.43, 117.38, 119.75, 117.00));
		ticks.add(new DefaultTick(45.58, 119.13, 119.13, 116.88));
		ticks.add(new DefaultTick(45.58, 115.38, 119.44, 114.56));

		data = new DefaultTimeSeries(ticks);
	}

	@Test
	public void testStochasticOscilatorKParam14() {

		StochasticOscilatorK sof = new StochasticOscilatorK(data, 14);

		assertEquals(313d / 3.50, sof.getValue(0), 0.01);
		assertEquals(1000d / 10.81, sof.getValue(12), 0.01);
		assertEquals(57.81, sof.getValue(13), 0.01);
	}

	@Test
	public void testStochasticOscilatorKShouldWorkJumpingIndexes() {

		StochasticOscilatorK sof = new StochasticOscilatorK(data, 14);
		assertEquals(57.81, sof.getValue(13), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		StochasticOscilatorK sof = new StochasticOscilatorK(data, 14);
		sof.getValue(1300);
	}

	@Test
	public void testGetName() {
		StochasticOscilatorK sof = new StochasticOscilatorK(data, 14);
		assertEquals("StochasticOscilatorK timeFrame: 14", sof.getName());
	}
}
