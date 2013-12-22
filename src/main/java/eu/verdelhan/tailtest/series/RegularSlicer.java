package net.sf.tail.series;

import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class RegularSlicer extends PartialMemorizedSlicer {


	public RegularSlicer(TimeSeries series, Period period, DateTime begin) {
		super(series, period, begin, 1);
	}

	public RegularSlicer(TimeSeries series, Period period) {
		this(series, period, series.getTick(0).getDate());
	}
	@Override
	public TimeSeriesSlicer applyForSeries(TimeSeries series) {
		return new RegularSlicer(series, this.period);
	}
}
