package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AccelerationDecelerationTest {

	private TimeSeries series;

	@Before
	public void setUp() throws Exception {

		List<DefaultTick> ticks = new ArrayList<DefaultTick>();

		ticks.add(new DefaultTick(0, 0, 16, 8));
		ticks.add(new DefaultTick(0, 0, 12, 6));
		ticks.add(new DefaultTick(0, 0, 18, 14));
		ticks.add(new DefaultTick(0, 0, 10, 6));
		ticks.add(new DefaultTick(0, 0, 8, 4));

		this.series = new SampleTimeSeries(ticks);
	}

	@Test
	public void testCalculateWithSma2AndSma3() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series, 2, 3);

		assertEquals(0d, (double) acceleration.getValue(0));
		assertEquals(0d, (double) acceleration.getValue(1));
		assertEquals(0.1666666d - 0.08333333d, acceleration.getValue(2), 0.001);
		assertEquals(1d - 0.5833333, acceleration.getValue(3), 0.001);
		assertEquals(-3d + 1d, (double) acceleration.getValue(4));
	}

	@Test
	public void testWithSma1AndSma2() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series, 1, 2);

		assertEquals(0d, (double) acceleration.getValue(0));
		assertEquals(0d, (double) acceleration.getValue(1));
		assertEquals(0d, (double) acceleration.getValue(2));
		assertEquals(0d, (double) acceleration.getValue(3));
		assertEquals(0d, (double) acceleration.getValue(4));
	}

	@Test
	public void testWithSmaDefault() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series);

		assertEquals(0d, (double) acceleration.getValue(0));
		assertEquals(0d, (double) acceleration.getValue(1));
		assertEquals(0d, (double) acceleration.getValue(2));
		assertEquals(0d, (double) acceleration.getValue(3));
		assertEquals(0d, (double) acceleration.getValue(4));
	}
}
