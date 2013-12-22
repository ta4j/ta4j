package net.sf.tail;

import org.joda.time.Period;

public class ConstrainedTimeSeries implements TimeSeries {

	private TimeSeries series;

	private int begin;

	private int end;

	public ConstrainedTimeSeries(TimeSeries series, int begin, int end) {
		if (end < begin - 1) {
			throw new IllegalArgumentException("end cannot be < than begin - 1");
		}
		this.series = series;
		this.begin = begin;
		this.end = end;
	}

	public int getSize() {
		return (end - begin) + 1;
	}

	public int getBegin() {
		return begin;
	}

	public Tick getTick(int i) {
		return series.getTick(i);
	}

	public int getEnd() {
		return end;
	}

	public String getName() {
		return series.getName();
	}

	public String getPeriodName() {
		return series.getTick(begin).getDate().toString("hh:mm dd/MM/yyyy - ")
				+ series.getTick(end).getDate().toString("hh:mm dd/MM/yyyy");
	}

	public Period getPeriod() {
		return new Period(Math.min(series.getTick(series.getBegin() + 1).getDate().getMillis() - series.getTick(series.getBegin()).getDate().getMillis(), 
				series.getTick(series.getBegin() + 2).getDate().getMillis() - series.getTick(series.getBegin() + 1).getDate().getMillis()));
	}
}
