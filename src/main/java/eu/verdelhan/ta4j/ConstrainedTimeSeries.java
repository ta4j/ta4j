package eu.verdelhan.ta4j;

import org.joda.time.Period;

public class ConstrainedTimeSeries implements TimeSeries {

	private TimeSeries series;

	private int begin;

	private int end;

	/**
	 * @param series the original time series
	 * @param begin the begin index of the time series
	 * @param end the end index of the time series
	 */
	public ConstrainedTimeSeries(TimeSeries series, int begin, int end) {
		if (end < begin - 1) {
			throw new IllegalArgumentException("end cannot be < than begin - 1");
		}
		this.series = series;
		this.begin = begin;
		this.end = end;
	}

	@Override
	public int getSize() {
		return (end - begin) + 1;
	}

	@Override
	public Tick getTick(int i) {
		return series.getTick(i);
	}

	@Override
	public int getBegin() {
		return begin;
	}

	@Override
	public int getEnd() {
		return end;
	}

	@Override
	public String getName() {
		return series.getName();
	}

	@Override
	public String getPeriodName() {
		return series.getTick(begin).getEndTime().toString("hh:mm dd/MM/yyyy - ")
				+ series.getTick(end).getEndTime().toString("hh:mm dd/MM/yyyy");
	}

	@Override
	public Period getPeriod() {
		return new Period(Math.min(series.getTick(series.getBegin() + 1).getEndTime().getMillis() - series.getTick(series.getBegin()).getEndTime().getMillis(), 
				series.getTick(series.getBegin() + 2).getEndTime().getMillis() - series.getTick(series.getBegin() + 1).getEndTime().getMillis()));
	}
}
