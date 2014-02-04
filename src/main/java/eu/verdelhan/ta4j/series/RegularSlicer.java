package eu.verdelhan.ta4j.series;

import org.joda.time.DateTime;
import org.joda.time.Period;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;

public class RegularSlicer extends PartialMemorizedSlicer {

    public RegularSlicer(TimeSeries series, Period period, DateTime begin) {
        super(series, period, begin, 1);
    }

    public RegularSlicer(TimeSeries series, Period period) {
        this(series, period, series.getTick(0).getEndTime());
    }

    @Override
    public TimeSeriesSlicer applyForSeries(TimeSeries series) {
        return new RegularSlicer(series, period);
    }
}
