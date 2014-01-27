package eu.verdelhan.tailtest.series;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import org.joda.time.DateTime;
import org.joda.time.Period;

/**
 *
 * 
 */
public class FullyMemorizedSlicer extends PartialMemorizedSlicer {

    public FullyMemorizedSlicer(TimeSeries series, Period period, DateTime begin) {
        super(series, period, begin, series.getSize());
    }

    public FullyMemorizedSlicer(TimeSeries series, Period period) {
        this(series, period, series.getTick(0).getDate());
    }

    @Override
    public TimeSeriesSlicer applyForSeries(TimeSeries series) {
        return new FullyMemorizedSlicer(series, period);
    }
}