/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j;

import java.util.ArrayList;
import java.util.List;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of a {@link TimeSeries}.
 * <p>
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
    /** List of ticks */
    private final List<Tick> ticks;
    /** Maximum number of ticks for the time series */
    private int maximumTickCount = Integer.MAX_VALUE;
    /** Number of removed ticks */
    private int removedTicksCount = 0;
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
        this(name, new ArrayList<Tick>());
    }

    /**
     * Constructor of an unnamed series.
     * @param ticks the list of ticks of the series
     */
    public BaseTimeSeries(List<Tick> ticks) {
        this(UNNAMED_SERIES_NAME, ticks);
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     */
    public BaseTimeSeries(String name, List<Tick> ticks) {
        this(name, ticks, 0, ticks.size() - 1, false);
    }

    /**
     * Constructor.
     * <p>
     * Constructs a constrained time series from an original one.
     * @param defaultSeries the original time series to construct a constrained series from
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     */
    public BaseTimeSeries(TimeSeries defaultSeries, int seriesBeginIndex, int seriesEndIndex) {
        this(defaultSeries.getName(), defaultSeries.getTickData(), seriesBeginIndex, seriesEndIndex, true);
        if (defaultSeries.getTickData() == null || defaultSeries.getTickData().isEmpty()) {
            throw new IllegalArgumentException("Cannot create a constrained series from a time series with a null/empty list of ticks");
        }
        if (defaultSeries.getMaximumTickCount() != Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot create a constrained series from a time series for which a maximum tick count has been set");
        }
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     * @param constrained true to constrain the time series (i.e. indexes cannot change), false otherwise
     */
    private BaseTimeSeries(String name, List<Tick> ticks, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
        this.name = name;
        this.ticks = ticks == null ? new ArrayList<>() : ticks;
        if (ticks.isEmpty()) {
        	// Tick list empty
            this.seriesBeginIndex = -1;
            this.seriesEndIndex = -1;
            this.constrained = false;
            return;
        }
        // Tick list not empty: checking indexes
        if (seriesEndIndex < seriesBeginIndex - 1) {
            throw new IllegalArgumentException("End index must be >= to begin index - 1");
        }
        if (seriesEndIndex >= ticks.size()) {
        	throw new IllegalArgumentException("End index must be < to the tick list size");
        }
        this.seriesBeginIndex = seriesBeginIndex;
        this.seriesEndIndex = seriesEndIndex;
        this.constrained = constrained;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Tick getTick(int i) {
        int innerIndex = i - removedTicksCount;
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th tick if i < 0
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
            }
            log.trace("Time series `{}` ({} ticks): tick {} already removed, use {}-th instead", name, ticks.size(), i, removedTicksCount);
            if (ticks.isEmpty()) {
                throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, removedTicksCount));
            }
            innerIndex = 0;
        } else if (innerIndex >= ticks.size()) {
            // Cannot return the n-th tick if n >= ticks.size()
            throw new IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i));
        }
        return ticks.get(innerIndex);
    }

    @Override
    public int getTickCount() {
        if (seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedTicksCount, seriesBeginIndex);
        return seriesEndIndex - startIndex + 1;
    }
    
    @Override
    public List<Tick> getTickData() {
    	return ticks;
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
    public void setMaximumTickCount(int maximumTickCount) {
        if (constrained) {
            throw new IllegalStateException("Cannot set a maximum tick count on a constrained time series");
        }
        if (maximumTickCount <= 0) {
            throw new IllegalArgumentException("Maximum tick count must be strictly positive");
        }
        this.maximumTickCount = maximumTickCount;
        removeExceedingTicks();
    }

    @Override
    public int getMaximumTickCount() {
        return maximumTickCount;
    }

    @Override
    public int getRemovedTicksCount() {
        return removedTicksCount;
    }

    @Override
    public void addTick(Tick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("Cannot add null tick");
        }
        final int lastTickIndex = ticks.size() - 1;
        if (!ticks.isEmpty()) {
            ZonedDateTime seriesEndTime = ticks.get(lastTickIndex).getEndTime();
            if (!tick.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException("Cannot add a tick with end time <= to series end time");
            }
        }

        ticks.add(tick);
        if (seriesBeginIndex == -1) {
            // Begin index set to 0 only if if wasn't initialized
            seriesBeginIndex = 0;
        }
        seriesEndIndex++;
        removeExceedingTicks();
    }

    /**
     * Removes the N first ticks which exceed the maximum tick count.
     */
    private void removeExceedingTicks() {
        int tickCount = ticks.size();
        if (tickCount > maximumTickCount) {
            // Removing old ticks
            int nbTicksToRemove = tickCount - maximumTickCount;
            for (int i = 0; i < nbTicksToRemove; i++) {
                ticks.remove(0);
            }
            // Updating removed ticks count
            removedTicksCount += nbTicksToRemove;
        }
    }

    /**
     * @param series a time series
     * @param index an out of bounds tick index
     * @return a message for an OutOfBoundsException
     */
    private static String buildOutOfBoundsMessage(BaseTimeSeries series, int index) {
        return "Size of series: " + series.ticks.size() + " ticks, "
                + series.removedTicksCount + " ticks removed, index = " + index;
    }
}
