package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.mocks.MockTick;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AccelerationDecelerationTest {

	private TimeSeries series;

	@Before
	public void setUp() throws Exception {

		List<Tick> ticks = new ArrayList<Tick>();

		ticks.add(new MockTick(0, 0, 16, 8));
		ticks.add(new MockTick(0, 0, 12, 6));
		ticks.add(new MockTick(0, 0, 18, 14));
		ticks.add(new MockTick(0, 0, 10, 6));
		ticks.add(new MockTick(0, 0, 8, 4));

		series = new MockTimeSeries(ticks);
	}

	@Test
	public void testCalculateWithSma2AndSma3() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series, 2, 3);

		assertEquals(BigDecimal.ZERO, acceleration.getValue(0));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(1));
		assertEquals(BigDecimal.valueOf(0.1666666d - 0.08333333d), acceleration.getValue(2));
		assertEquals(BigDecimal.valueOf(1d - 0.5833333), acceleration.getValue(3));
		assertEquals(BigDecimal.valueOf(-3d + 1d), acceleration.getValue(4));
	}

	@Test
	public void testWithSma1AndSma2() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series, 1, 2);

		assertEquals(BigDecimal.ZERO, acceleration.getValue(0));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(1));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(2));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(3));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(4));
	}

	@Test
	public void testWithSmaDefault() throws Exception {
		AccelerationDeceleration acceleration = new AccelerationDeceleration(series);

		assertEquals(BigDecimal.ZERO, acceleration.getValue(0));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(1));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(2));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(3));
		assertEquals(BigDecimal.ZERO, acceleration.getValue(4));
	}
}
