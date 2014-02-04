package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.series.RegularSlicer;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class RegularSlicerTest {

	private MockTimeSeries series;

	private DateTime date;

	@Before
	public void setUp() throws Exception {
		this.date = new DateTime(0);
	}
	
	@Test
	public void testApllyForSeries(){
		series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		TimeSeriesSlicer slicer = new RegularSlicer(series, period);

		TimeSeriesSlicer newSlicer = slicer.applyForSeries(series);
		
		assertEquals(slicer, newSlicer);
		
		

		series = new MockTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
				.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2003));


		newSlicer = slicer.applyForSeries(series);

		assertEquals(4, newSlicer.getNumberOfSlices());

		assertEquals(0, newSlicer.getSlice(0).getBegin());
		assertEquals(2, newSlicer.getSlice(0).getEnd());

		assertEquals(3, newSlicer.getSlice(1).getBegin());
		assertEquals(5, newSlicer.getSlice(1).getEnd());

		assertEquals(6, newSlicer.getSlice(2).getBegin());
		assertEquals(9, newSlicer.getSlice(2).getEnd());

		assertEquals(10, newSlicer.getSlice(3).getBegin());
		assertEquals(10, newSlicer.getSlice(3).getEnd());
	}

	@Test
	public void testSplitByYearOneDatePerYear() {

		series = new MockTimeSeries(date.withYear(2000), date.withYear(2001), date.withYear(2002), date
				.withYear(2003), date.withYear(2004));
		Period period = new Period().withYears(1);

		TimeSeriesSlicer split = new RegularSlicer(series, period);

		assertEquals(5, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(0, split.getSlice(0).getEnd());
		assertEquals(1, split.getSlice(1).getBegin());
		assertEquals(1, split.getSlice(1).getEnd());
		assertEquals(2, split.getSlice(2).getBegin());
		assertEquals(2, split.getSlice(2).getEnd());
		assertEquals(3, split.getSlice(3).getBegin());
		assertEquals(3, split.getSlice(3).getEnd());
		assertEquals(4, split.getSlice(4).getBegin());
		assertEquals(4, split.getSlice(4).getEnd());
	}

	@Test
	public void testSplitByYear() {

		series = new MockTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
				.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2003));

		Period period = new Period().withYears(1);

		TimeSeriesSlicer split = new RegularSlicer(series, period);

		assertEquals(4, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(2, split.getSlice(0).getEnd());

		assertEquals(3, split.getSlice(1).getBegin());
		assertEquals(5, split.getSlice(1).getEnd());

		assertEquals(6, split.getSlice(2).getBegin());
		assertEquals(9, split.getSlice(2).getEnd());

		assertEquals(10, split.getSlice(3).getBegin());
		assertEquals(10, split.getSlice(3).getEnd());
	}

	@Test
	public void testSplitByYearForcingJuly() {
		Period period = new Period().withYears(1);

		series = new MockTimeSeries(date.withDate(2000, 1, 1), date.withDate(2000, 2, 1), date.withDate(2000, 3, 1),
				date.withDate(2001, 1, 1), date.withDate(2001, 2, 1), date.withDate(2001, 12, 12), date.withDate(2002,
						1, 1), date.withDate(2002, 2, 1), date.withDate(2002, 3, 1), date.withDate(2002, 5, 1), date
						.withDate(2003, 3, 1));

		TimeSeriesSlicer split = new RegularSlicer(series, period, date.withYear(2000).withMonthOfYear(7));

		assertEquals(3, split.getNumberOfSlices());

		assertEquals(3, split.getSlice(0).getBegin());
		assertEquals(4, split.getSlice(0).getEnd());

		assertEquals(5, split.getSlice(1).getBegin());
		assertEquals(9, split.getSlice(1).getEnd());

		assertEquals(10, split.getSlice(2).getBegin());
		assertEquals(10, split.getSlice(2).getEnd());
	}

	@Test
	public void testSplitByYearWithHolesBetweenSlices() {

		series = new MockTimeSeries(date.withYear(2000), date.withYear(2000), date.withYear(2000), date
				.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2005), date.withYear(2005));

		Period period = new Period().withYears(1);
		TimeSeriesSlicer split = new RegularSlicer(series, period);

		assertEquals(4, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(2, split.getSlice(0).getEnd());

		assertEquals(3, split.getSlice(1).getBegin());
		assertEquals(5, split.getSlice(1).getEnd());

		assertEquals(6, split.getSlice(2).getBegin());
		assertEquals(9, split.getSlice(2).getEnd());

		assertEquals(10, split.getSlice(3).getBegin());
		assertEquals(11, split.getSlice(3).getEnd());

	}


	@Test
	public void testSplitByYearBeginningInJuly() {
		Period period = new Period().withYears(1);

		series = new MockTimeSeries(date.withDate(2000, 7, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
				date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
						1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
						.withDate(2003, 3, 3));
		TimeSeriesSlicer split = new RegularSlicer(series, period);

		assertEquals(3, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(4, split.getSlice(0).getEnd());

		assertEquals(5, split.getSlice(1).getBegin());
		assertEquals(9, split.getSlice(1).getEnd());

		assertEquals(10, split.getSlice(2).getBegin());
		assertEquals(10, split.getSlice(2).getEnd());
	}

	@Test
	public void testSplitByYearBeginingInJulyOverridingPeriodBeginTo1of1of2000() {
		Period period = new Period().withYears(1);

		series = new MockTimeSeries(date.withDate(2000, 7, 1), date.withDate(2000, 8, 1), date.withDate(2000, 9, 15),
				date.withDate(2001, 1, 1), date.withDate(2001, 1, 3), date.withDate(2001, 12, 31), date.withDate(2002,
						1, 1), date.withDate(2002, 1, 2), date.withDate(2002, 1, 3), date.withDate(2002, 5, 5), date
						.withDate(2003, 3, 3));
		TimeSeriesSlicer split = new RegularSlicer(series, period, date.withDate(2000, 1, 1));

		assertEquals(3, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(4, split.getSlice(0).getEnd());

		assertEquals(5, split.getSlice(1).getBegin());
		assertEquals(9, split.getSlice(1).getEnd());

		assertEquals(10, split.getSlice(2).getBegin());
		assertEquals(10, split.getSlice(2).getEnd());
	}

	@Test
	public void testSplitByHour() {
		Period period = new Period().withHours(1);

		DateTime openTime = new DateTime(0).withTime(10, 0, 0, 0);

		series = new MockTimeSeries(openTime, openTime.plusMinutes(1), openTime.plusMinutes(2), openTime
				.plusMinutes(10), openTime.plusMinutes(15), openTime.plusMinutes(25), openTime.plusHours(1), openTime
				.plusHours(2), openTime.plusHours(7), openTime.plusHours(10).plusMinutes(5), openTime.plusHours(10)
				.plusMinutes(10), openTime.plusHours(10).plusMinutes(20), openTime.plusHours(10).plusMinutes(30));

		TimeSeriesSlicer split = new RegularSlicer(series, period);

		assertEquals(5, split.getNumberOfSlices());

		assertEquals(0, split.getSlice(0).getBegin());
		assertEquals(5, split.getSlice(0).getEnd());

		assertEquals(6, split.getSlice(1).getBegin());
		assertEquals(6, split.getSlice(1).getEnd());

		assertEquals(7, split.getSlice(2).getBegin());
		assertEquals(7, split.getSlice(2).getEnd());

		assertEquals(8, split.getSlice(3).getBegin());
		assertEquals(8, split.getSlice(3).getEnd());

		assertEquals(9, split.getSlice(4).getBegin());
		assertEquals(12, split.getSlice(4).getEnd());

	}

}
