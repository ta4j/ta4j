/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sequence of {@link Bar bars} separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * </p>
 * Notably, a {@link TimeSeries time series} can be:
 * <ul>
 *     <li>the base of {@link Indicator indicator} calculations
 *     <li>constrained between begin and end indexes (e.g. for some backtesting cases)
 *     <li>limited to a fixed number of bars (e.g. for actual trading)
 * </ul>
 */
public interface TimeSeries extends Serializable {

    /**
     * @return the name of the series
     */
    String getName();

    /**
     * @param i an index
     * @return the bar at the i-th position
     */
    Bar getBar(int i);

    /**
     * @return the first bar of the series
     */
    default Bar getFirstBar() {
        return getBar(getBeginIndex());
    }

    /**
     * @return the last bar of the series
     */
    default Bar getLastBar() {
        return getBar(getEndIndex());
    }

    /**
     * @return the number of bars in the series
     */
    int getBarCount();

    /**
     * @return true if the series is empty, false otherwise
     */
    default boolean isEmpty() {
    	return getBarCount() == 0;
    }

    /**
     * Warning: should be used carefully!
     * <p>
     * Returns the raw bar data.
     * It means that it returns the current List object used internally to store the {@link Bar bars}.
     * It may be:
     *   - a shortened bar list if a maximum bar count has been set
     *   - a extended bar list if it is a constrained time series
     * @return the raw bar data
     */
    List<Bar> getBarData();

    /**
     * @return the begin index of the series
     */
    int getBeginIndex();

    /**
     * @return the end index of the series
     */
    int getEndIndex();

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     */
    default String getSeriesPeriodDescription() {
        StringBuilder sb = new StringBuilder();
        if (!getBarData().isEmpty()) {
            Bar firstBar = getFirstBar();
            Bar lastBar = getLastBar();
            sb.append(firstBar.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME))
                    .append(" - ")
                    .append(lastBar.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME));
        }
        return sb.toString();
    }

    /**
     * Sets the maximum number of bars that will be retained in the series.
     * <p>
     * If a new bar is added to the series such that the number of bars will exceed the maximum bar count,
     * then the FIRST bar in the series is automatically removed, ensuring that the maximum bar count is not exceeded.
     * @param maximumBarCount the maximum bar count
     */
    void setMaximumBarCount(int maximumBarCount);

    /**
     * @return the maximum number of bars
     */
    int getMaximumBarCount();

    /**
     * @return the number of removed bars
     */
    int getRemovedBarsCount();

    /**
     * Adds a bar at the end of the series.
     * <p>
     * Begin index set to 0 if if wasn't initialized.<br>
     * End index set to 0 if if wasn't initialized, or incremented if it matches the end of the series.<br>
     * Exceeding bars are removed.
     * @param bar the bar to be added
     * @see TimeSeries#setMaximumBarCount(int)
     */
    void addBar(Bar bar);

    /**
     * Returns a new TimeSeries implementation that is a subset of this TimeSeries implementation.
     * It holds a copy of all {@link Bar bars} between <tt>startIndex</tt> (inclusive) and <tt>endIndex</tt> (exclusive)
     * of this TimeSeries.
     * The indices of this TimeSeries and the new subset TimeSeries can be different. I. e. index 0 of the new TimeSeries will
     * be index <tt>startIndex</tt> of this TimeSeries.
     * If <tt>startIndex</tt> < this.seriesBeginIndex the new TimeSeries will start with the first available Bar of this TimeSeries.
     * If <tt>endIndex</tt> > this.seriesEndIndex the new TimeSeries will end at the last available Bar of this TimeSeries
     * @param startIndex the startIndex
     * @param endIndex the endIndex
     * @return a new BaseTimeSeries with Bars from startIndex to endIndex-1
     * @throws IllegalArgumentException e.g. if endIndex < startIndex
     */
    TimeSeries getSubSeries(int startIndex, int endIndex);
}
