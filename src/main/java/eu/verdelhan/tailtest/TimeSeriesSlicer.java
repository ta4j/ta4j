package eu.verdelhan.tailtest;

import org.joda.time.DateTime;
import org.joda.time.Period;

/**
 * Slicer for a time series.
 */
public interface TimeSeriesSlicer {

	/**
	 * @param position the index of the sub-series
	 * @return the sub-series
	 */
    TimeSeries getSlice(int position);

    TimeSeries getSeries();

    String getName();

    String getPeriodName();

    Period getPeriod();

    DateTime getDateBegin();

	/**
	 * @return the number of slices
	 */
    int getNumberOfSlices();

	/**
	 * @return the average number of ticks per slice
	 */
    double getAverageTicksPerSlice();

    TimeSeriesSlicer applyForSeries(TimeSeries series);
}