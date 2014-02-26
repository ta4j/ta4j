/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.series;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;

/**
 * A {@link TimeSeriesSlicer time series slicer}
 *
 */
public class PartialMemorizedSlicer implements TimeSeriesSlicer {

	/** The time series */
	private TimeSeries series;

	private Period period;
	/** The list of slices (sub-series) */
	private List<TimeSeries> splittedSeries;

	private DateTime periodBegin;

	private int periodsPerSlice;

	/**
	 * Constructor.
	 * @param series the time series
	 * @param period
	 * @param periodBegin
	 * @param periodsPerSlice
	 */
	public PartialMemorizedSlicer(TimeSeries series, Period period, DateTime periodBegin, int periodsPerSlice) {
		if (period == null) {
			throw new NullPointerException("Period cannot be null");
		}
		if (periodsPerSlice < 1) {
			throw new IllegalArgumentException("Periods per slice must be greater than 1");
		}
		
		int index = series.getBegin();

		DateTime initialSeriesDate = series.getTick(index).getEndTime();
		if (periodBegin.isBefore(initialSeriesDate) && !periodBegin.equals(initialSeriesDate))
			periodBegin = series.getTick(series.getBegin()).getEndTime();

		Interval interval = new Interval(periodBegin, periodBegin.plus(period));

		while (series.getTick(index).getEndTime().isBefore(interval.getStart()))
			index++;

		this.series = new ConstrainedTimeSeries(series, index, series.getEnd());
		this.period = period;
		this.splittedSeries = new ArrayList<TimeSeries>();
		this.periodBegin = periodBegin;
		this.periodsPerSlice = periodsPerSlice;
		split();
	}

	/**
	 * @param series the time series
	 * @param period
	 * @param periodsPerSlice
	 */
	public PartialMemorizedSlicer(TimeSeries series, Period period, int periodsPerSlice) {
		this(series, period, series.getTick(series.getBegin()).getEndTime(), periodsPerSlice);
	}

	@Override
	public TimeSeriesSlicer applyForSeries(TimeSeries series) {
		return applyForSeries(series, this.periodBegin);
	}

	public TimeSeriesSlicer applyForSeries(TimeSeries series, DateTime periodBegin) {
		return new PartialMemorizedSlicer(series, this.period, periodBegin, this.periodsPerSlice);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " Period: " + periodToString();
	}

	@Override
	public Period getPeriod() {
		return period;
	}

	@Override
	public String getPeriodName() {
		return this.periodBegin.toString("hh:mm dd/MM/yyyy - ")
				+ series.getTick(series.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public TimeSeries getSeries() {
		return series;
	}

	@Override
	public TimeSeries getSlice(int position) {
		return splittedSeries.get(position);
	}

	@Override
	public DateTime getDateBegin() {
		return periodBegin;
	}

	@Override
	public int getNumberOfSlices() {
		return splittedSeries.size();
	}

	@Override
	public double getAverageTicksPerSlice() {
		double sum = 0;
		for (TimeSeries subSeries : splittedSeries) {
			sum += subSeries.getSize();
		}
		return getNumberOfSlices() > 0 ? sum / getNumberOfSlices() : 0;
	}

	@Override
	public int hashCode() {
		final int prime = 23;
		int result = 1;
		result = prime * result + ((period == null) ? 0 : period.hashCode());
		result = prime * result + ((periodBegin == null) ? 0 : periodBegin.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof PartialMemorizedSlicer)) {
			return false;
		}
		final PartialMemorizedSlicer other = (PartialMemorizedSlicer) obj;
		if (period == null) {
			if (other.period != null) {
				return false;
			}
		} else if (!period.equals(other.period)) {
			return false;
		}
		if (periodBegin == null) {
			if (other.periodBegin != null) {
				return false;
			}
		} else if (!periodBegin.equals(other.periodBegin)) {
			return false;
		}
		return true;
	}

	/**
	 * Splits the series into slices.
	 */
	private void split() {

		DateTime begin = periodBegin;
		DateTime end = begin.plus(period);

		Interval interval = new Interval(begin, end);
		int index = series.getBegin();

		List<Integer> begins = new ArrayList<Integer>();
		begins.add(index);
		while (index <= series.getEnd()) {

			if (interval.contains(series.getTick(index).getEndTime())) {
				index++;
			} else if (end.plus(period).isAfter(series.getTick(index).getEndTime())) {
				createSlice(begins.get(Math.max(begins.size() - periodsPerSlice, 0)), index - 1);

				//LOG.debug(String.format("Interval %s before  %s ", interval, series.getTick(index).getEndTime()));

				int sliceBeginIndex = index;
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

	/**
	 * Creates a slice (sub-series).
	 * @param beginIndex the begin index of the sub-series
	 * @param endIndex the end index of the sub-series
	 */
	private void createSlice(int beginIndex, int endIndex) {
		ConstrainedTimeSeries slice = new ConstrainedTimeSeries(series, beginIndex, endIndex);
		splittedSeries.add(slice);
	}

	/**
	 * @return the period as a string
	 */
	private String periodToString() {
		StringBuilder sb = new StringBuilder("");
		if (period.getYears() > 0) {
			sb.append(period.getYears()).append(" year(s) ,");
		}
		if (period.getMonths() > 0) {
			sb.append(period.getMonths()).append(" month(s) ,");
		}
		if (period.getDays() > 0) {
			sb.append(period.getDays()).append(" day(s) ,");
		}
		if (period.getHours() > 0) {
			sb.append(period.getHours()).append(" hour(s) ,");
		}
		if (period.getMinutes() > 0) {
			sb.append(period.getMinutes()).append(" minute(s)");
		}
		return sb.toString();
	}
}
