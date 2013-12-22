package net.sf.tail.series;

import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class FullyMemorizedSlicer extends PartialMemorizedSlicer {


	public FullyMemorizedSlicer(TimeSeries series, Period period, DateTime begin) {
		super(series, period, begin, series.getSize());
	}

	public FullyMemorizedSlicer(TimeSeries series, Period period) {
		this(series, period, series.getTick(0).getDate());
	}
	@Override
	public TimeSeriesSlicer applyForSeries(TimeSeries series) {
		return new FullyMemorizedSlicer(series, this.period);
	}
}
