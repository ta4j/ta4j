package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTick;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class DefaultTimeSeriesTest {

	private TimeSeries defaultSeries;

	private List<Tick> ticks;

	private String defaultName;

	@Before
	public void setUp() {
		ticks = new LinkedList<Tick>();
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 9), 3d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 10), 4d));

		defaultName = "Series Name";

		defaultSeries = new DefaultTimeSeries(defaultName, ticks);
	}

	@Test
	public void getEndSizeBegin() {
		assertThat(defaultSeries.getBegin()).isEqualTo(0);
		assertEquals(ticks.size() - 1, defaultSeries.getEnd());
		assertThat(defaultSeries.getSize()).isEqualTo(ticks.size());
	}

	@Test
	public void getPeriodName() {
		assertTrue(defaultSeries.getPeriodName().endsWith(
				ticks.get(defaultSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy")));
		assertTrue(defaultSeries.getPeriodName().startsWith(
				ticks.get(defaultSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy")));
	}

	@Test
	public void getName() {
		assertThat(defaultSeries.getName().equals(defaultName)).isTrue();
	}

	@Test
	public void getPeriodTest() {
		assertEquals(new Period(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis()), defaultSeries
				.getPeriod());
	}
}
