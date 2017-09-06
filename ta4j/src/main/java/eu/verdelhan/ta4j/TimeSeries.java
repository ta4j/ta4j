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

import eu.verdelhan.ta4j.Order.OrderType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sequence of {@link Tick ticks} separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * <p>
 * Notably, a {@link TimeSeries time series} can be:
 * <ul>
 * <li>the base of {@link Indicator indicator} calculations
 * <li>constrained between begin and end indexes (e.g. for some backtesting cases)
 * <li>limited to a fixed number of ticks (e.g. for actual trading)
 * <li>used to run {@link Strategy trading strategies}
 * </ul>
 */
public class TimeSeries implements Serializable {

    private static final long serialVersionUID = -1878027009398790126L;
    /** Name for unamed series */
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
    public TimeSeries() {
        this(UNNAMED_SERIES_NAME);
    }

    /**
     * Constructor.
     * @param name the name of the series
     */
    public TimeSeries(String name) {
        this(name, new ArrayList<Tick>());
    }

    /**
     * Constructor of an unnamed series.
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(List<Tick> ticks) {
        this(UNNAMED_SERIES_NAME, ticks);
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(String name, List<Tick> ticks) {
        this(name, ticks, 0, ticks.size() - 1, false);
    }

    /**
     * Constructor.
     * <p>
     * Constructs a constrained time series from an original one.
     * @param origSeries the original time series to construct a constrained series from
     * @param seriesBeginIndex the begin index (inclusive) of the time series
     * @param seriesEndIndex the end index (inclusive) of the time series
     */
    public TimeSeries(TimeSeries origSeries, int seriesBeginIndex, int seriesEndIndex) {
        this(origSeries.name, origSeries.ticks, seriesBeginIndex, seriesEndIndex, true);
        if (origSeries.ticks == null || origSeries.ticks.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a constrained series from a time series with a null/empty list of ticks");
        }
        if (origSeries.maximumTickCount != Integer.MAX_VALUE) {
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
    private TimeSeries(String name, List<Tick> ticks, int seriesBeginIndex, int seriesEndIndex, boolean constrained) {
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

    /**
     * @return the name of the series
     */
    public String getName() {
        return name;
    }

    /**
     * @param i an index
     * @return the tick at the i-th position
     */
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

    /**
     * @return the first tick of the series
     */
    public Tick getFirstTick() {
        return getTick(seriesBeginIndex);
    }

    /**
     * @return the last tick of the series
     */
    public Tick getLastTick() {
        return getTick(seriesEndIndex);
    }

    /**
     * @return the number of ticks in the series
     */
    public int getTickCount() {
        if (seriesEndIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedTicksCount, seriesBeginIndex);
        return seriesEndIndex - startIndex + 1;
    }

    /**
     * @return true if the series is empty, false otherwise
     */
    public boolean isEmpty() {
    	return getTickCount() == 0;
    }
    
    /**
     * @return the begin index of the series
     */
    public int getBeginIndex() {
        return seriesBeginIndex;
    }

    /**
     * @return the end index of the series
     */
    public int getEndIndex() {
        return seriesEndIndex;
    }

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     */
    public String getSeriesPeriodDescription() {
        StringBuilder sb = new StringBuilder();
        if (!ticks.isEmpty()) {
            Tick firstTick = getFirstTick();
            Tick lastTick = getLastTick();
            sb.append(firstTick.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME))
                    .append(" - ")
                    .append(lastTick.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME));
        }
        return sb.toString();
    }

    /**
     * Sets the maximum number of ticks that will be retained in the series.
     * <p>
     * If a new tick is added to the series such that the number of ticks will exceed the maximum tick count,
     * then the FIRST tick in the series is automatically removed, ensuring that the maximum tick count is not exceeded.
     * @param maximumTickCount the maximum tick count
     */
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

    /**
     * @return the maximum number of ticks
     */
    public int getMaximumTickCount() {
        return maximumTickCount;
    }

    /**
     * @return the number of removed ticks
     */
    public int getRemovedTicksCount() {
        return removedTicksCount;
    }

    /**
     * Adds a tick at the end of the series.
     * <p>
     * Begin index set to 0 if if wasn't initialized.<br>
     * End index set to 0 if if wasn't initialized, or incremented if it matches the end of the series.<br>
     * Exceeding ticks are removed.
     * @param tick the tick to be added
     * @see TimeSeries#setMaximumTickCount(int)
     */
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
     * Runs the strategy over the series.
     * <p>
     * Opens the trades with {@link OrderType.BUY} orders.
     * @param strategy the trading strategy
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, OrderType.BUY);
    }

    /**
     * Runs the strategy over the series (from startIndex to finishIndex).
     * <p>
     * Opens the trades with {@link OrderType.BUY} orders.
     * @param strategy the trading strategy
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, OrderType.BUY, Decimal.NaN, startIndex, finishIndex);
    }

    /**
     * Runs the strategy over the series.
     * <p>
     * Opens the trades with {@link OrderType.BUY} orders.
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType) {
        return run(strategy, orderType, Decimal.NaN);
    }

    /**
     * Runs the strategy over the series (from startIndex to finishIndex).
     * <p>
     * Opens the trades with {@link OrderType.BUY} orders.
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, int startIndex, int finishIndex) {
        return run(strategy, orderType, Decimal.NaN, startIndex, finishIndex);
    }

    /**
     * Runs the strategy over the series.
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, Decimal amount) {
        return run(strategy, orderType, amount, seriesBeginIndex, seriesEndIndex);
    }

    /**
     * Runs the strategy over the series (from startIndex to finishIndex).
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, Decimal amount, int startIndex, int finishIndex) {

        int runBeginIndex = Math.max(startIndex, seriesBeginIndex);
        int runEndIndex = Math.min(finishIndex, seriesEndIndex);
        
        log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex, strategy, orderType);
        TradingRecord tradingRecord = new TradingRecord(orderType);
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each tick between both indexes...       
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, ticks.get(i).getClosePrice(), amount);
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last trade is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of ticks
            for (int i = runEndIndex + 1; i < ticks.size(); i++) {
                // For each tick after the end index of this run...
                // --> Trying to close the last trade
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradingRecord.operate(i, ticks.get(i).getClosePrice(), amount);
                    break;
                }
            }
        }
        return tradingRecord;
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
    private static String buildOutOfBoundsMessage(TimeSeries series, int index) {
        return "Size of series: " + series.ticks.size() + " ticks, "
                + series.removedTicksCount + " ticks removed, index = " + index;
    }
}
