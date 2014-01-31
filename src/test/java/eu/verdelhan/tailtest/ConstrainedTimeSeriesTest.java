package eu.verdelhan.tailtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class ConstrainedTimeSeriesTest {

	private static TimeSeries series;

	private ConstrainedTimeSeries constrained;

	private List<DefaultTick> ticks;

	@Before
	public void setUp() {
		ticks = new LinkedList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 9), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 10), 4d));
		
		series = new MockTimeSeries(ticks);
		constrained = new ConstrainedTimeSeries(series, series.getBegin(), series.getEnd());
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructurTesteShouldThrowsException() {
		@SuppressWarnings("unused")
		ConstrainedTimeSeries constrainedException = new ConstrainedTimeSeries(series, series.getEnd(), series.getBegin());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void getTickTestWrongIndex() {
		constrained.getTick(series.getEnd() + 1);
		constrained.getTick(series.getBegin() - 1);
	}

	@Test
	public void getEndSizeBegin() {
		assertEquals(series.getBegin(), constrained.getBegin());
		assertEquals(series.getEnd(), constrained.getEnd());
		assertEquals(series.getSize(), constrained.getSize());
	}

	@Test
	public void getPeriodName() {
		assertTrue(constrained.getPeriodName().endsWith(series.getTick(series.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy")));
		assertTrue(constrained.getPeriodName().startsWith(series.getTick(series.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy")));
	}
	
	@Test
	public void getName() {
		assertTrue(constrained.getName().endsWith(series.getName()));
	}
	
	@Test
	public void getPeriodTest() {
		assertEquals(new Period(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis()), constrained.getPeriod());
	}
}
