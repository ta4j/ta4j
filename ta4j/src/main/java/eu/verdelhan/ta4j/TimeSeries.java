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
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sequence of {@link Tick ticks} separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * <p>
 * Notably, a {@link TimeSeries time series} can be:
 * <ul>
 * <li>splitted into sub-series
 * <li>the base of {@link Indicator indicator} calculations
 * <li>limited to a fixed number of ticks (e.g. for actual trading)
 * <li>used to run {@link Strategy trading strategies}
 * </ul>
 */
public class TimeSeries implements Serializable {

	private static final long serialVersionUID = -1878027009398790126L;
	/** The logger */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Name of the series */
    private final String name;
    /** Begin index of the time series */
    private int beginIndex = -1;
    /** End index of the time series */
    private int endIndex = -1;
    /** List of ticks */
    private final List<Tick> ticks;
    /** Maximum number of ticks for the time series */
    private int maximumTickCount = Integer.MAX_VALUE;
    /** Number of removed ticks */
    private int removedTicksCount = 0;
    /** True if the current series is a sub-series, false otherwise */
    private boolean subSeries = false;

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(String name, List<Tick> ticks) {
        this(name, ticks, 0, ticks.size() - 1, false);
    }

    /**
     * Constructor of an unnamed series.
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(List<Tick> ticks) {
        this("unnamed", ticks);
    }

    /**
     * Constructor.
     * @param name the name of the series
     */
    public TimeSeries(String name) {
        this.name = name;
        this.ticks = new ArrayList<Tick>();
    }

    /**
     * Constructor of an unnamed series.
     */
    public TimeSeries() {
        this("unamed");
    }

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     * @param beginIndex the begin index (inclusive) of the time series
     * @param endIndex the end index (inclusive) of the time series
     * @param subSeries true if the current series is a sub-series, false otherwise
     */
    private TimeSeries(String name, List<Tick> ticks, int beginIndex, int endIndex, boolean subSeries) {
        // TODO: add null checks and out of bounds checks
        if (endIndex < beginIndex - 1) {
            throw new IllegalArgumentException("end cannot be < than begin - 1");
        }
        this.name = name;
        this.ticks = ticks;
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
        this.subSeries = subSeries;
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
        return getTick(beginIndex);
    }

    /**
     * @return the last tick of the series
     */
    public Tick getLastTick() {
        return getTick(endIndex);
    }

    /**
     * @return the number of ticks in the series
     */
    public int getTickCount() {
        if (endIndex < 0) {
            return 0;
        }
        final int startIndex = Math.max(removedTicksCount, beginIndex);
        return endIndex - startIndex + 1;
    }

    /**
     * @return the begin index of the series
     */
    public int getBegin() {
        return beginIndex;
    }

    /**
     * @return the end index of the series
     */
    public int getEnd() {
        return endIndex;
    }

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     */
    public String getSeriesPeriodDescription() {
        StringBuilder sb = new StringBuilder();
        if (!ticks.isEmpty()) {
            final String timeFormat = "hh:mm dd/MM/yyyy";
            Tick firstTick = getFirstTick();
            Tick lastTick = getLastTick();
            sb.append(firstTick.getEndTime().toString(timeFormat))
                    .append(" - ")
                    .append(lastTick.getEndTime().toString(timeFormat));
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
        if (subSeries) {
            throw new IllegalStateException("Cannot set a maximum tick count on a sub-series");
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
            DateTime seriesEndTime = ticks.get(lastTickIndex).getEndTime();
            if (!tick.getEndTime().isAfter(seriesEndTime)) {
                throw new IllegalArgumentException("Cannot add a tick with end time <= to series end time");
            }
        }

        ticks.add(tick);
        if (beginIndex == -1) {
            // Begin index set to 0 only if if wasn't initialized
            beginIndex = 0;
        }
        endIndex++;
        removeExceedingTicks();
    }

    /**
     * Returns a new time series which is a view of a subset of the current series.
     * <p>
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * The tick of the series are shared between the original time series and the returned one (i.e. no copy).
     * @param beginIndex the begin index (inclusive) of the time series
     * @param endIndex the end index (inclusive) of the time series
     * @return a constrained {@link TimeSeries time series} which is a sub-set of the current series
     */
    public TimeSeries subseries(int beginIndex, int endIndex) {
        if (maximumTickCount != Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot create a sub-series from a time series for which a maximum tick count has been set");
        }
        return new TimeSeries(name, ticks, beginIndex, endIndex, true);
    }

    /**
     * Returns a new time series which is a view of a subset of the current series.
     * <p>
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * The tick of the series are shared between the original time series and the returned one (i.e. no copy).
     * @param beginIndex the begin index (inclusive) of the time series
     * @param duration the duration of the time series
     * @return a constrained {@link TimeSeries time series} which is a sub-set of the current series
     */
    public TimeSeries subseries(int beginIndex, Period duration) {

        // Calculating the sub-series interval
        DateTime beginInterval = getTick(beginIndex).getEndTime();
        DateTime endInterval = beginInterval.plus(duration);
        Interval subseriesInterval = new Interval(beginInterval, endInterval);

        // Checking ticks belonging to the sub-series (starting at the provided index)
        int subseriesNbTicks = 0;
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            DateTime tickTime = getTick(i).getEndTime();
            if (!subseriesInterval.contains(tickTime)) {
                // Tick out of the interval
                break;
            }
            // Tick in the interval
            // --> Incrementing the number of ticks in the subseries
            subseriesNbTicks++;
        }

        return subseries(beginIndex, beginIndex + subseriesNbTicks - 1);
    }

    /**
     * Splits the time series into sub-series containing nbTicks ticks each.<br>
     * The current time series is splitted every nbTicks ticks.<br>
     * The last sub-series may have less ticks than nbTicks.
     * @param nbTicks the number of ticks of each sub-series
     * @return a list of sub-series
     */
    public List<TimeSeries> split(int nbTicks) {
        ArrayList<TimeSeries> subseries = new ArrayList<TimeSeries>();
        for (int i = beginIndex; i <= endIndex; i += nbTicks) {
            // For each nbTicks ticks
            int subseriesBegin = i;
            int subseriesEnd = Math.min(subseriesBegin + nbTicks - 1, endIndex);
            subseries.add(subseries(subseriesBegin, subseriesEnd));
        }
        return subseries;
    }

    /**
     * Splits the time series into sub-series lasting sliceDuration.<br>
     * The current time series is splitted every splitDuration.<br>
     * The last sub-series may last less than sliceDuration.
     * @param splitDuration the duration between 2 splits
     * @param sliceDuration the duration of each sub-series
     * @return a list of sub-series
     */
    public List<TimeSeries> split(Period splitDuration, Period sliceDuration) {
        ArrayList<TimeSeries> subseries = new ArrayList<TimeSeries>();
        if (splitDuration != null && !splitDuration.equals(Period.ZERO)
                && sliceDuration != null && !sliceDuration.equals(Period.ZERO)) {

            List<Integer> beginIndexes = getSplitBeginIndexes(splitDuration);
            for (Integer subseriesBegin : beginIndexes) {
                subseries.add(subseries(subseriesBegin, sliceDuration));
            }
        }
        return subseries;
    }

    /**
     * Splits the time series into sub-series lasting duration.<br>
     * The current time series is splitted every duration.<br>
     * The last sub-series may last less than duration.
     * @param duration the duration between 2 splits (and of each sub-series)
     * @return a list of sub-series
     */
    public List<TimeSeries> split(Period duration) {
        return split(duration, duration);
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
     * Runs the strategy over the series.
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, OrderType orderType, Decimal amount) {

        log.trace("Running strategy: {} (starting with {})", strategy, orderType);
        TradingRecord tradingRecord = new TradingRecord(orderType);
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick in the sub-series...       
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, ticks.get(i).getClosePrice(), amount);
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last trade is still opened, we search out of the end index.
            // May works if the current series is a sub-series (but not the last sub-series).
            for (int i = endIndex + 1; i < ticks.size(); i++) {
                // For each tick out of sub-series bound...
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
     * Builds a list of split indexes from splitDuration.
     * @param splitDuration the duration between 2 splits
     * @return a list of begin indexes after split
     */
    private List<Integer> getSplitBeginIndexes(Period splitDuration) {
        ArrayList<Integer> beginIndexes = new ArrayList<Integer>();

        // Adding the first begin index
        beginIndexes.add(beginIndex);

        // Building the first interval before next split
        DateTime beginInterval = getTick(beginIndex).getEndTime();
        DateTime endInterval = beginInterval.plus(splitDuration);
        Interval splitInterval = new Interval(beginInterval, endInterval);

        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            DateTime tickTime = getTick(i).getEndTime();
            if (!splitInterval.contains(tickTime)) {
                // Tick out of the interval
                if (!endInterval.isAfter(tickTime)) {
                    // Tick after the interval
                    // --> Adding a new begin index
                    beginIndexes.add(i);
                }

                // Building the new interval before next split
                beginInterval = endInterval.isBefore(tickTime) ? tickTime : endInterval;
                endInterval = beginInterval.plus(splitDuration);
                splitInterval = new Interval(beginInterval, endInterval);
            }
        }
        return beginIndexes;
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
