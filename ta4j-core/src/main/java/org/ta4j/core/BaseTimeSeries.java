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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base implementation of a {@link TimeSeries}.
 * <p></p>
 */
public class BaseTimeSeries implements TimeSeries {

    private static final long serialVersionUID = -1878027009398790126L;
    /** Name for unnamed series */
    private static final String UNNAMED_SERIES_NAME = "unamed_series";
    /** The logger */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Name of the series */
    private final String name;
    /** Begin index of the time series */
    private int seriesBeginIndex = -1;
    /** End index of the time series */
    private int seriesEndIndex = -1;
    /** List of bars */
    private final List<Bar> bars;
    /** Maximum number of bars for the time series */
    private int maximumBarCount = Integer.MAX_VALUE;
    /** Number of removed bars */
    private int removedBarsCount = 0;
    /** True if the current series is constrained (i.e. its indexes cannot change), false otherwise */
    private boolean constrained = false;

    /**
     * Constructor of an unnamed series.
     */
    public BaseTimeSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     * @param name the name of the series
     */
    public BaseTimeSeries(String name) {
        this(name, new ArrayList<Bar>());
    }

    /**
     * Constructor of an unnamed series.
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(List<Bar> bars) {
        this(UNNAMED_SERIES_NAME, bars);
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param bars the list of bars of the series
     */
    public BaseTimeSeries(String name, List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false);
    }

    /**
     * Constructor.
     * <p>
     * Constructs a constrained time series from an original one.
     * @param defaultSeries the original time series to construct a constrained series from
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     *
     * @deprecated use {@link #getSubSeries(int, int) getSubSeries(startIndex, endIndex)} to satisfy correct behaviour of
     * the new sub series in further calculations
     */
    @Deprecated
    public BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex) {
        this(defaultSeries.getName(), defaultSeries.getBarData(), seriesBeginIndex, seriesEndIndex, true);
        if (defaultSeries.getBarData() == null || defaultSeries.getBarData().isEmpty()) {
            throw new IllegalArgumentException("Cannot create a constrained series from a time series with a null/empty list of bars");
        }
        if (defaultSeries.getMaximumBarCount() != Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot create a constrained series from a time series for which a maximum bar count has been set");
        }
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param bars the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     * @param constrained true to constrain the time series (i.e. indexes cannot change), false otherwise
     */
    private BaseTimeSeries(String name, List<Bar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        Objects.requireNonNull(bars);
        this.bars = bars;
        this.name = name;
        if (bars.isEmpty()) {
        	// Bar list empty
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            return;
        }
        // Bar list not empty: checking indexes
        if (seriesEndIndex < seriesBeginIndex - 1) {
            throw new IllegalArgumentException("End index must be >= to begin index - 1");
        }
        if (seriesEndIndex >= bars.size()) {
        	throw new IllegalArgumentException("End index must be < to the bar list size");
        }
        this.seriesBeginIndex = seriesBeginIndex;
        this.seriesEndIndex = seriesEndIndex;
        this.constrained = constrained;
    }

    /**
     * Returns a new BaseTimeSeries that is a subset of this BaseTimeSeries.
     * The new series holds a copy of all {@link Bar bars} between <tt>startIndex</tt> (inclusive) and <tt>endIndex</tt> (exclusive)
     * of this TimeSeries.
     * The indices of this TimeSeries and the new subset TimeSeries can be different. I. e. index 0 of the new TimeSeries will
     * be index <tt>startIndex</tt> of this TimeSeries.
     * If <tt>startIndex</tt> < this.seriesBeginIndex the new TimeSeries will start with the first available Bar of this TimeSeries.
     * If <tt>endIndex</tt> > this.seriesEndIndex+1 the new TimeSeries will end at the last available Bar of this TimeSeries
     * @param startIndex the startIndex
     * @param endIndex the endIndex (exclusive)
     * @return a new BaseTimeSeries with Bars from <tt>startIndex</tt> to <tt>endIndex</tt>-1
     * @throws IllegalArgumentException if <tt>endIndex</tt> < <tt>startIndex</tt>
     */
    @Override
    public TimeSeries getSubSeries(int startIndex, int endIndex){
        if(startIndex > endIndex){
            throw new IllegalArgumentException
                    (String.format("the endIndex: %s must be bigger than startIndex: %s", endIndex, startIndex));
        }
        if(!bars.isEmpty()) {
            int start = Math.max(startIndex, this.seriesBeginIndex);
            int end = Math.min(endIndex, this.seriesEndIndex + 1);
            return new BaseTimeSeries(getName(), cut(bars, start, end));
        }
        return new BaseTimeSeries(name);

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Bar getBar(int i) {
        int innerIndex = i - removedBarsCount;
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th bar if i < 0
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
            }
            log.trace("Time series `{}` ({} bars): bar {} already removed, use {}-th instead", name, bars.size(), i, removedBarsCount);
            if (bars.isEmpty()) {
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, removedBarsCount));
            }
            innerIndex = 0;
        } else if (innerIndex >= bars.size()) {
            // Cannot return the n-th bar if n >= bars.size()
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
        }
        return bars.get(innerIndex);
    }

    @Override
    public int getBarCount() {
        if (seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedBarsCount, seriesBeginIndex);
        return seriesEndIndex - startIndex + 1;
    }

    @Override
    public List<Bar> getBarData() {
    	return bars;
    }

    @Override
    public int getBeginIndex() {
        return seriesBeginIndex;
    }

    @Override
    public int getEndIndex() {
        return seriesEndIndex;
    }

    @Override
    public void setMaximumBarCount(int maximumBarCount) {
        if (constrained) {
            throw new IllegalStateException("Cannot set a maximum bar count on a constrained time series");
        }
        if (maximumBarCount <= 0) {
            throw new IllegalArgumentException("Maximum bar count must be strictly positive");
        }
        this.maximumBarCount = maximumBarCount;
        removeExceedingBars();
    }

    @Override
    public int getMaximumBarCount() {
        return maximumBarCount;
    }

    @Override
    public int getRemovedBarsCount() {
        return removedBarsCount;
    }

    @Override
    public void addBar(Bar bar) {
        if (bar == null) {
            throw new IllegalArgumentException("Cannot add null bar");
        }

        if (!bars.isEmpty()) {
            final int lastBarIndex = bars.size() - 1;
            ZonedDateTime seriesEndTime = bars.get(lastBarIndex).getEndTime();
            if (!bar.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException("Cannot add a bar with end time <= to series end time");
            }
        }

        bars.add(bar);
        if (seriesBeginIndex == -1) {
            // Begin index set to 0 only if if wasn't initialized
            seriesBeginIndex = 0;
        }
        seriesEndIndex++;
        removeExceedingBars();
    }

    /**
     * Removes the N first bars which exceed the maximum bar count.
     */
    private void removeExceedingBars() {
        int barCount = bars.size();
        if (barCount > maximumBarCount) {
            // Removing old bars
            int nbBarsToRemove = barCount - maximumBarCount;
            for (int i = 0; i < nbBarsToRemove; i++) {
                bars.remove(0);
            }
            // Updating removed bars count
            removedBarsCount += nbBarsToRemove;
        }
    }

    /**
     * Cuts a list of bars into a new list of bars that is a subset of it
     * @param bars the list of {@link Bar bars}
     * @param startIndex start index of the subset
     * @param endIndex end index of the subset
     * @return a new list of bars with tick from startIndex (inclusive) to endIndex (exclusive)
     */
    private static List<Bar> cut(List<Bar> bars, final int startIndex, final int endIndex){
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * @param series a time series
     * @param index an out of bounds bar index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(BaseTimeSeries series, int index) {
        return "Size of series: " + series.bars.size() + " bars, "
                + series.removedBarsCount + " bars removed, index = " + index;
    }
}
