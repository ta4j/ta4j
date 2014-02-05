package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.indicator.simple.MaxPrice;
import eu.verdelhan.ta4j.indicator.simple.MinPrice;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.series.DefaultTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class WilliamsRTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {

		List<Tick> ticks = new ArrayList<Tick>();
		ticks.add(new MockTick(44.98, 45.05, 45.17, 44.96));
		ticks.add(new MockTick(45.05, 45.10, 45.15, 44.99));
		ticks.add(new MockTick(45.11, 45.19, 45.32, 45.11));
		ticks.add(new MockTick(45.19, 45.14, 45.25, 45.04));
		ticks.add(new MockTick(45.12, 45.15, 45.20, 45.10));
		ticks.add(new MockTick(45.15, 45.14, 45.20, 45.10));
		ticks.add(new MockTick(45.13, 45.10, 45.16, 45.07));
		ticks.add(new MockTick(45.12, 45.15, 45.22, 45.10));
		ticks.add(new MockTick(45.15, 45.22, 45.27, 45.14));
		ticks.add(new MockTick(45.24, 45.43, 45.45, 45.20));
		ticks.add(new MockTick(45.43, 45.44, 45.50, 45.39));
		ticks.add(new MockTick(45.43, 45.55, 45.60, 45.35));
		ticks.add(new MockTick(45.58, 45.55, 45.61, 45.39));

		data = new DefaultTimeSeries(ticks);

	}

	@Test
	public void testWilliamsRUsingTimeFrame5UsingClosePrice() throws Exception {
		WilliamsR wr = new WilliamsR(new ClosePrice(data), 5, new MaxPrice(data),
				new MinPrice(data));

		assertThat(wr.getValue(4)).isEqualTo(-47.22);
		assertThat(wr.getValue(5)).isEqualTo(-54.55);
		assertThat(wr.getValue(6)).isEqualTo(-78.57);
		assertThat(wr.getValue(7)).isEqualTo(-47.62);
		assertThat(wr.getValue(8)).isEqualTo(-25.00);
		assertThat(wr.getValue(9)).isEqualTo(-5.26);
		assertThat(wr.getValue(10)).isEqualTo(-13.95);

	}

	@Test
	public void testWilliamsRShouldWorkJumpingIndexes() {
		WilliamsR wr = new WilliamsR(new ClosePrice(data), 5, new MaxPrice(data),
				new MinPrice(data));
		assertThat(wr.getValue(10)).isEqualTo(-13.95);
		assertThat(wr.getValue(4)).isEqualTo(-47.22);
	}

	@Test
	public void testWilliamsRUsingTimeFrame10UsingClosePrice() {
		WilliamsR wr = new WilliamsR(new ClosePrice(data), 10, new MaxPrice(data),
				new MinPrice(data));

		assertThat(wr.getValue(9)).isEqualTo(-4.08);
		assertThat(wr.getValue(10)).isEqualTo(-11.77);
		assertThat(wr.getValue(11)).isEqualTo(-8.93);
		assertThat(wr.getValue(12)).isEqualTo(-10.53);

	}

	@Test
	public void testValueLessThenTimeFrame() {
		WilliamsR wr = new WilliamsR(new ClosePrice(data), 100, new MaxPrice(data),
				new MinPrice(data));

		assertEquals(-100d * (0.12 / 0.21), wr.getValue(0), 0.01);
		assertEquals(-100d * (0.07 / 0.21), wr.getValue(1), 0.01);
		assertEquals(-100d * (0.13 / 0.36), wr.getValue(2), 0.01);
		assertEquals(-100d * (0.18 / 0.36), wr.getValue(3), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		WilliamsR wr = new WilliamsR(new ClosePrice(data), 100, new MaxPrice(data),
				new MinPrice(data));
		wr.getValue(13);
	}
}
