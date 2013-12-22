package net.sf.tail.indicator.tracker;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.simple.MaxPriceIndicator;
import net.sf.tail.indicator.simple.MinPriceIndicator;
import net.sf.tail.series.DefaultTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Before;
import org.junit.Test;

public class WilliamsRIndicatorTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {

		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(44.98, 45.05, 45.17, 44.96));
		ticks.add(new DefaultTick(45.05, 45.10, 45.15, 44.99));
		ticks.add(new DefaultTick(45.11, 45.19, 45.32, 45.11));
		ticks.add(new DefaultTick(45.19, 45.14, 45.25, 45.04));
		ticks.add(new DefaultTick(45.12, 45.15, 45.20, 45.10));
		ticks.add(new DefaultTick(45.15, 45.14, 45.20, 45.10));
		ticks.add(new DefaultTick(45.13, 45.10, 45.16, 45.07));
		ticks.add(new DefaultTick(45.12, 45.15, 45.22, 45.10));
		ticks.add(new DefaultTick(45.15, 45.22, 45.27, 45.14));
		ticks.add(new DefaultTick(45.24, 45.43, 45.45, 45.20));
		ticks.add(new DefaultTick(45.43, 45.44, 45.50, 45.39));
		ticks.add(new DefaultTick(45.43, 45.55, 45.60, 45.35));
		ticks.add(new DefaultTick(45.58, 45.55, 45.61, 45.39));

		data = new DefaultTimeSeries(ticks);

	}

	@Test
	public void testWilliamsRUsingTimeFrame5UsingClosePrice() throws Exception {
		WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 5, new MaxPriceIndicator(data),
				new MinPriceIndicator(data));

		assertEquals(-47.22, wr.getValue(4), 0.01);
		assertEquals(-54.55, wr.getValue(5), 0.01);
		assertEquals(-78.57, wr.getValue(6), 0.01);
		assertEquals(-47.62, wr.getValue(7), 0.01);
		assertEquals(-25.00, wr.getValue(8), 0.01);
		assertEquals(-5.26, wr.getValue(9), 0.01);
		assertEquals(-13.95, wr.getValue(10), 0.01);

	}

	@Test
	public void testWilliamsRShouldWorkJumpingIndexes() {
		WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 5, new MaxPriceIndicator(data),
				new MinPriceIndicator(data));
		assertEquals(-13.95, wr.getValue(10), 0.01);
		assertEquals(-47.22, wr.getValue(4), 0.01);
	}

	@Test
	public void testWilliamsRUsingTimeFrame10UsingClosePrice() {
		WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 10, new MaxPriceIndicator(data),
				new MinPriceIndicator(data));

		assertEquals(-4.08, wr.getValue(9), 0.01);
		assertEquals(-11.77, wr.getValue(10), 0.01);
		assertEquals(-8.93, wr.getValue(11), 0.01);
		assertEquals(-10.53, wr.getValue(12), 0.01);

	}

	@Test
	public void testValueLessThenTimeFrame() {
		WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 100, new MaxPriceIndicator(data),
				new MinPriceIndicator(data));

		assertEquals(-100d * (0.12 / 0.21), wr.getValue(0), 0.01);
		assertEquals(-100d * (0.07 / 0.21), wr.getValue(1), 0.01);
		assertEquals(-100d * (0.13 / 0.36), wr.getValue(2), 0.01);
		assertEquals(-100d * (0.18 / 0.36), wr.getValue(3), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 100, new MaxPriceIndicator(data),
				new MinPriceIndicator(data));
		wr.getValue(13);
	}
}
