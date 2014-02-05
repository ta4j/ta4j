package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ConstrainedTimeSeriesTest {

	private static TimeSeries series;

	private ConstrainedTimeSeries constrained;

	private List<Tick> ticks;

	@Before
	public void setUp() {
		ticks = new LinkedList<Tick>();
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 9), 3d));
		ticks.add(new MockTick(new DateTime().withDate(2007, 6, 10), 4d));
		
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
		assertThat(constrained.getBegin()).isEqualTo(series.getBegin());
		assertThat(constrained.getEnd()).isEqualTo(series.getEnd());
		assertThat(constrained.getSize()).isEqualTo(series.getSize());
	}

	@Test
	public void getPeriodName() {
		assertThat(constrained.getPeriodName().endsWith(series.getTick(series.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"))).isTrue();
		assertThat(constrained.getPeriodName().startsWith(series.getTick(series.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"))).isTrue();
	}
	
	@Test
	public void getName() {
		assertThat(constrained.getName().endsWith(series.getName())).isTrue();
	}
	
	@Test
	public void getPeriodTest() {
		assertEquals(new Period(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis()), constrained.getPeriod());
	}
}
