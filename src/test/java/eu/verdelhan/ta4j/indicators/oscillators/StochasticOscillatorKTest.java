package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.indicators.oscillators.StochasticOscillatorK;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.series.DefaultTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class StochasticOscillatorKTest {

	private TimeSeries data;

	@Before
	public void setUp() {

		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(44.98, 119.13, 119.50, 116.00));
		ticks.add(new MockTick(45.05, 116.75, 119.94, 116.00));
		ticks.add(new MockTick(45.11, 113.50, 118.44, 111.63));
		ticks.add(new MockTick(45.19, 111.56, 114.19, 110.06));
		ticks.add(new MockTick(45.12, 112.25, 112.81, 109.63));
		ticks.add(new MockTick(45.15, 110.00, 113.44, 109.13));
		ticks.add(new MockTick(45.13, 113.50, 115.81, 110.38));
		ticks.add(new MockTick(45.12, 117.13, 117.50, 114.06));
		ticks.add(new MockTick(45.15, 115.63, 118.44, 114.81));
		ticks.add(new MockTick(45.24, 114.13, 116.88, 113.13));
		ticks.add(new MockTick(45.43, 118.81, 119.00, 116.19));
		ticks.add(new MockTick(45.43, 117.38, 119.75, 117.00));
		ticks.add(new MockTick(45.58, 119.13, 119.13, 116.88));
		ticks.add(new MockTick(45.58, 115.38, 119.44, 114.56));

		data = new DefaultTimeSeries(ticks);
	}

	@Test
	public void testStochasticOscilatorKParam14() {

		StochasticOscillatorK sof = new StochasticOscillatorK(data, 14);

		assertThat(sof.getValue(0)).isEqualTo(313d / 3.50);
		assertThat(sof.getValue(12)).isEqualTo(1000d / 10.81);
		assertThat(sof.getValue(13)).isEqualTo(57.81);
	}

	@Test
	public void testStochasticOscilatorKShouldWorkJumpingIndexes() {

		StochasticOscillatorK sof = new StochasticOscillatorK(data, 14);
		assertThat(sof.getValue(13)).isEqualTo(57.81);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		StochasticOscillatorK sof = new StochasticOscillatorK(data, 14);
		sof.getValue(1300);
	}
}
