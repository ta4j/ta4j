package eu.verdelhan.tailtest;

import org.joda.time.DateTime;
import org.joda.time.Period;

public interface TimeSeriesSlicer {

    TimeSeries getSlice(int position);

    int getSlices();

    TimeSeries getSeries();

    String getName();

    String getPeriodName();

    Period getPeriod();

    DateTime getDateBegin();

    int getNumberOfSlices();

    double getAverageTicksPerSlice();

    TimeSeriesSlicer applyForSeries(TimeSeries series);
}