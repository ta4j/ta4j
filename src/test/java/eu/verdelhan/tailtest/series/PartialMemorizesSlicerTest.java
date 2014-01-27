package eu.verdelhan.tailtest.series;

import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class PartialMemorizesSlicerTest {

	private SampleTimeSeries series;

	private DateTime date;
	
	private TimeSeriesSlicer slicer; 

	@Before
	public void setUp() throws Exception {
		this.date = new DateTime(0);
	}

	@Test
	public void testApllyForRegularSlicer() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		slicer = new PartialMemorizedSlicer(series, period, 1);

		assertEquals(0, slicer.getSlice(0).getBegin());
		assertEquals(1, slicer.getSlice(1).getBegin());
		assertEquals(2, slicer.getSlice(2).getBegin());
		assertEquals(3, slicer.getSlice(3).getBegin());
		assertEquals(4, slicer.getSlice(4).getBegin());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPeriodsPerSliceGreaterThan1() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		slicer = new PartialMemorizedSlicer(series, new Period().withYears(1), 0);
	}

	@Test
	public void testStartDateBeforeTimeSeriesDate() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		slicer = new PartialMemorizedSlicer(series, period, date.withYear(1980), 1);

		assertEquals(0, slicer.getSlice(0).getBegin());
		assertEquals(1, slicer.getSlice(1).getBegin());
		assertEquals(2, slicer.getSlice(2).getBegin());
		assertEquals(3, slicer.getSlice(3).getBegin());
		assertEquals(4, slicer.getSlice(4).getBegin());
	}

	@Test
	public void testApllyForPartialMemorizedSlicer() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		slicer = new PartialMemorizedSlicer(series, period, 3);

		assertEquals(0, slicer.getSlice(0).getBegin());
		assertEquals(0, slicer.getSlice(0).getEnd());

		assertEquals(0, slicer.getSlice(1).getBegin());
		assertEquals(1, slicer.getSlice(1).getEnd());

		assertEquals(0, slicer.getSlice(2).getBegin());
		assertEquals(2, slicer.getSlice(2).getEnd());

		assertEquals(1, slicer.getSlice(3).getBegin());
		assertEquals(3, slicer.getSlice(3).getEnd());

		assertEquals(2, slicer.getSlice(4).getBegin());
		assertEquals(4, slicer.getSlice(4).getEnd());
	}

	@Test
	public void testApllyForFullMemorizedSlicer() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		slicer = new PartialMemorizedSlicer(series, period, series.getSize());

		assertEquals(0, slicer.getSlice(0).getBegin());
		assertEquals(0, slicer.getSlice(0).getEnd());

		assertEquals(0, slicer.getSlice(1).getBegin());
		assertEquals(1, slicer.getSlice(1).getEnd());

		assertEquals(0, slicer.getSlice(2).getBegin());
		assertEquals(2, slicer.getSlice(2).getEnd());

		assertEquals(0, slicer.getSlice(3).getBegin());
		assertEquals(3, slicer.getSlice(3).getEnd());

		assertEquals(0, slicer.getSlice(4).getBegin());
		assertEquals(4, slicer.getSlice(4).getEnd());
	}

	@Test
	public void testApllyForSeries() {
		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		TimeSeriesSlicer slicer = new PartialMemorizedSlicer(series, period, 3);

		TimeSeriesSlicer newSlicer = slicer.applyForSeries(series);

		assertEquals(slicer, newSlicer);

		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
				.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2003));

		newSlicer = slicer.applyForSeries(series);

		assertEquals(4, newSlicer.getNumberOfSlices());

		assertEquals(0, newSlicer.getSlice(0).getBegin());
		assertEquals(2, newSlicer.getSlice(0).getEnd());

		assertEquals(0, newSlicer.getSlice(1).getBegin());
		assertEquals(5, newSlicer.getSlice(1).getEnd());

		assertEquals(0, newSlicer.getSlice(2).getBegin());
		assertEquals(9, newSlicer.getSlice(2).getEnd());

		assertEquals(3, newSlicer.getSlice(3).getBegin());
		assertEquals(10, newSlicer.getSlice(3).getEnd());
	}

	@Test
	public void testSplitByYearOneDatePerYear() {

		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, 3);

		assertEquals(5, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(0, split.getSlice(0).getEnd());

		assertEquals(0, split.getSlice(1).getBegin());
		assertEquals(1, split.getSlice(1).getEnd());

		assertEquals(0, split.getSlice(2).getBegin());
		assertEquals(2, split.getSlice(2).getEnd());

		assertEquals(1, split.getSlice(3).getBegin());
		assertEquals(3, split.getSlice(3).getEnd());

		assertEquals(2, split.getSlice(4).getBegin());
		assertEquals(4, split.getSlice(4).getEnd());
	}

	@Test
	public void testSplitByYearForcingJuly() {
		Period period = new Period().withYears(1);

		series = new SampleTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 2, 1), date.withDate(2000, 3, 1),
				date.withDate(2001, 1, 1), date.withDate(2001, 2, 1), date.withDate(2001, 12, 12), date.withDate(2002,
						1, 1), date.withDate(2002, 2, 1), date.withDate(2002, 3, 1), date.withDate(2002, 5, 1), date
						.withDate(2003, 3, 1));

		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, date.withYear(2000).withMonthOfYear(7), 2);

		assertEquals(3, split.getNumberOfSlices());

		assertEquals(3, split.getSlice(0).getBegin());
		assertEquals(4, split.getSlice(0).getEnd());

		assertEquals(3, split.getSlice(1).getBegin());
		assertEquals(9, split.getSlice(1).getEnd());

		assertEquals(5, split.getSlice(2).getBegin());
		assertEquals(10, split.getSlice(2).getEnd());
	}

	@Test
	public void testSplitByYearWithHolesBetweenSlices() {

		series = new SampleTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
				.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2005), date.withYear(2005));

		Period period = new Period().withYears(1);
		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, 3);

		assertEquals(4, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(2, split.getSlice(0).getEnd());

		assertEquals(0, split.getSlice(1).getBegin());
		assertEquals(5, split.getSlice(1).getEnd());

		assertEquals(0, split.getSlice(2).getBegin());
		assertEquals(9, split.getSlice(2).getEnd());

		assertEquals(3, split.getSlice(3).getBegin());
		assertEquals(11, split.getSlice(3).getEnd());

	}

	@Test
	public void testSplitByYearBeginningInJuly() {
		Period period = new Period().withYears(1);

		series = new SampleTimeSeries(date.withDate(2000, 7, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
				date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
						1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
						.withDate(2003, 3, 3));

		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, 2);

		assertEquals(3, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(4, split.getSlice(0).getEnd());

		assertEquals(0, split.getSlice(1).getBegin());
		assertEquals(9, split.getSlice(1).getEnd());

		assertEquals(5, split.getSlice(2).getBegin());
		assertEquals(10, split.getSlice(2).getEnd());
	}

	@Test
	public void testSplitByYearBeginingInJulyOverridingPeriodBeginTo1of1of2000() {
		Period period = new Period().withYears(1);

		series = new SampleTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
				date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
						1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
						.withDate(2003, 3, 3));
		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, date.withDate(2000, 1, 1), 3);

		assertEquals(4, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(2, split.getSlice(0).getEnd());

		assertEquals(0, split.getSlice(1).getBegin());
		assertEquals(5, split.getSlice(1).getEnd());

		assertEquals(0, split.getSlice(2).getBegin());
		assertEquals(9, split.getSlice(2).getEnd());

		assertEquals(3, split.getSlice(3).getBegin());
		assertEquals(10, split.getSlice(3).getEnd());
	}

	@Test
	public void testSplitByHour() {
		Period period = new Period().withHours(1);

		DateTime openTime = new DateTime(0).withTime(10, 0, 0, 0);

		series = new SampleTimeSeries(openTime, openTime.plusMinutes(1), openTime.plusMinutes(2), openTime
				.plusMinutes(10), openTime.plusMinutes(15), openTime.plusMinutes(25), openTime.plusHours(1), openTime
				.plusHours(2), openTime.plusHours(7), openTime.plusHours(10).plusMinutes(5), openTime.plusHours(10)
				.plusMinutes(10), openTime.plusHours(10).plusMinutes(20), openTime.plusHours(10).plusMinutes(30));

		TimeSeriesSlicer split = new PartialMemorizedSlicer(series, period, 3);

		assertEquals(5, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(5, split.getSlice(0).getEnd());

		assertEquals(0, split.getSlice(1).getBegin());
		assertEquals(6, split.getSlice(1).getEnd());

		assertEquals(0, split.getSlice(2).getBegin());
		assertEquals(7, split.getSlice(2).getEnd());

		assertEquals(6, split.getSlice(3).getBegin());
		assertEquals(8, split.getSlice(3).getEnd());

		assertEquals(7, split.getSlice(4).getBegin());
		assertEquals(12, split.getSlice(4).getEnd());

	}
	
	@Test
	public void testAverageTicksPerSlice()
	{
		Period period = new Period().withYears(1);
		series = new SampleTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
				date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
						1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
						.withDate(2003, 3, 3));
		PartialMemorizedSlicer slicer = new PartialMemorizedSlicer(series, period, 3);
		assertEquals(27d/4, slicer.getAverageTicksPerSlice());
	}

}
