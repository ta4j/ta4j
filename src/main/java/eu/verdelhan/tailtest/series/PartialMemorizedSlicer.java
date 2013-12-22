package net.sf.tail.series;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.ConstrainedTimeSeries;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

public class PartialMemorizedSlicer implements TimeSeriesSlicer {
	protected transient TimeSeries series;

	protected Period period;

	protected transient List<TimeSeries> splittedSeries;

	protected DateTime periodBegin;

	protected int periodsPerSlice;

	private transient static Logger LOG = Logger.getLogger(PartialMemorizedSlicer.class);

	public PartialMemorizedSlicer(TimeSeries series, Period period, DateTime periodBegin, int periodsPerSlice) {
		if(period == null)
			throw new NullPointerException("Period cannot be null");
		if(periodsPerSlice < 1)
			throw new IllegalArgumentException("Periods per slice must be greater than 1");
		
		int index = series.getBegin();

		DateTime inicialSeriesDate = series.getTick(index).getDate();
		if (periodBegin.isBefore(inicialSeriesDate) && !periodBegin.equals(inicialSeriesDate))
			periodBegin = series.getTick(series.getBegin()).getDate();

		Interval interval = new Interval(periodBegin, periodBegin.plus(period));

		while (series.getTick(index).getDate().isBefore(interval.getStart()))
			index++;

		this.series = new ConstrainedTimeSeries(series, index, series.getEnd());
		this.period = period;
		this.splittedSeries = new ArrayList<TimeSeries>();
		this.periodBegin = periodBegin;
		this.periodsPerSlice = periodsPerSlice;
		split();
	}

	public PartialMemorizedSlicer(TimeSeries series, Period period, int periodsPerSlice) {
		this(series, period, series.getTick(series.getBegin()).getDate(), periodsPerSlice);
	}

	private void split() {
		LOG.debug(String.format("Spliting %s  ", series));

		DateTime begin = periodBegin;
		DateTime end = begin.plus(period);

		Interval interval = new Interval(begin, end);
		int index = series.getBegin();

		int sliceBeginIndex = index;

		List<Integer> begins = new ArrayList<Integer>();
		begins.add(index);
		while (index <= series.getEnd()) {

			if (interval.contains(series.getTick(index).getDate())) {
				index++;
			} else if (end.plus(period).isAfter(series.getTick(index).getDate())) {
				createSlice(begins.get(Math.max(begins.size() - periodsPerSlice, 0)), index - 1);

				LOG.debug(String.format("Interval %s before  %s ", interval, series.getTick(index).getDate()));

				sliceBeginIndex = index;
				begins.add(sliceBeginIndex);
				begin = end;
				end = begin.plus(period);
				interval = new Interval(begin, end);
				index++;
			} else {
				begin = end;
				end = begin.plus(period);
				interval = new Interval(begin, end);
			}
		}
		createSlice(begins.get(Math.max(begins.size() - periodsPerSlice, 0)), series.getEnd());
	}

	private void createSlice(int begin, int end) {
		LOG.debug(String.format("New series from %d to %d ", begin, end));
		ConstrainedTimeSeries slice = new ConstrainedTimeSeries(series, begin, end);
		splittedSeries.add(slice);
	}

	public TimeSeriesSlicer applyForSeries(TimeSeries series, DateTime periodBegin) {
		return new PartialMemorizedSlicer(series, this.period, periodBegin, this.periodsPerSlice);
	}

	public TimeSeriesSlicer applyForSeries(TimeSeries series) {
		return applyForSeries(series, this.periodBegin);
	}

	public String getName() {
		String sPeriod = "";
		sPeriod = periodToString(sPeriod);
		return this.getClass().getSimpleName() + " Period: " + sPeriod;
	}

	public Period getPeriod() {
		return period;
	}

	public String getPeriodName() {
		return this.periodBegin.toString("hh:mm dd/MM/yyyy - ")
				+ series.getTick(series.getEnd()).getDate().toString("hh:mm dd/MM/yyyy");
	}

	public TimeSeries getSeries() {
		return series;
	}

	public TimeSeries getSlice(int position) {
		return splittedSeries.get(position);
	}

	public int getSlices() {
		return splittedSeries.size();
	}

	protected String periodToString(String sPeriod) {
		if (period.getYears() > 0)
			sPeriod += period.getYears() + " year(s) ,";
		if (period.getMonths() > 0)
			sPeriod += period.getMonths() + " month(s) ,";
		if (period.getDays() > 0)
			sPeriod += period.getDays() + " day(s) ,";
		if (period.getHours() > 0)
			sPeriod += period.getHours() + " day(s) ,";
		if (period.getMinutes() > 0)
			sPeriod += period.getMinutes() + " day(s) ,";
		return sPeriod.substring(0, sPeriod.length() - 2);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((period == null) ? 0 : period.hashCode());
		result = prime * result + ((periodBegin == null) ? 0 : periodBegin.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof PartialMemorizedSlicer))
			return false;
		final PartialMemorizedSlicer other = (PartialMemorizedSlicer) obj;
		if (period == null) {
			if (other.period != null)
				return false;
		} else if (!period.equals(other.period))
			return false;
		if (periodBegin == null) {
			if (other.periodBegin != null)
				return false;
		} else if (!periodBegin.equals(other.periodBegin)) {
			System.out.println(periodBegin.toString());
			System.out.println(other.periodBegin.toString());
			return false;
		}
		return true;
	}

	public DateTime getDateBegin() {
		return periodBegin;
	}

	public int getNumberOfSlices() {
		return splittedSeries.size();
	}

	public double getAverageTicksPerSlice() {
		double sum = 0;
		for (TimeSeries series : splittedSeries) {
			sum += series.getSize();
		}
		return sum / this.getNumberOfSlices();
	}

}
