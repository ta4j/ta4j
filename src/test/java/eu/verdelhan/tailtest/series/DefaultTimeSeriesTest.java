package net.sf.tail.series;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class DefaultTimeSeriesTest {

	private TimeSeries defaultSeries;

	private List<DefaultTick> ticks;

	private String defaultName;

	@Before
	public void setUp() {
		ticks = new LinkedList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 9), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 10), 4d));

		defaultName = "Series Name";

		defaultSeries = new DefaultTimeSeries(defaultName, ticks);
	}

	@Test
	public void getEndSizeBegin() {
		assertEquals(0, defaultSeries.getBegin());
		assertEquals(ticks.size() - 1, defaultSeries.getEnd());
		assertEquals(ticks.size(), defaultSeries.getSize());
	}

	@Test
	public void getPeriodName() {
		assertTrue(defaultSeries.getPeriodName().endsWith(
				ticks.get(defaultSeries.getEnd()).getDate().toString("hh:mm dd/MM/yyyy")));
		assertTrue(defaultSeries.getPeriodName().startsWith(
				ticks.get(defaultSeries.getBegin()).getDate().toString("hh:mm dd/MM/yyyy")));
	}

	@Test
	public void getName() {
		assertTrue(defaultSeries.getName().equals(defaultName));
	}

	@Test
	public void getPeriodTest() {
		assertEquals(new Period(ticks.get(1).getDate().getMillis() - ticks.get(0).getDate().getMillis()), defaultSeries
				.getPeriod());
	}
}
