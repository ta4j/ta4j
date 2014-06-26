/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
import org.joda.time.Period;

/**
 * Set of ticks separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * <p>
 */
public class TimeSeries {
    /** List of ticks */
    private final List<? extends Tick> ticks;
    /** Begin index of the time series */
    private int beginIndex;
    /** End index of the time series */
    private int endIndex;
    /** Name of the series */
    private final String name;

    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     * @param beginIndex the begin index (inclusive) of the time series
     * @param endIndex the end index (inclusive) of the time series
     */
    public TimeSeries(String name, List<? extends Tick> ticks, int beginIndex, int endIndex) {
        // TODO: add null checks and out of bounds checks
        if (endIndex < beginIndex - 1) {
            throw new IllegalArgumentException("end cannot be < than begin - 1");
        }
        this.name = name;
        this.ticks = ticks;
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
    }
    
    /**
     * Constructor.
     * @param name the name of the series
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(String name, List<? extends Tick> ticks) {
        this(name, ticks, 0, ticks.size() - 1);
    }

    /**
     * Constructor of an unnamed series.
     * @param ticks the list of ticks of the series
     */
    public TimeSeries(List<? extends Tick> ticks) {
        this("unnamed", ticks);
    }

    /**
     * @return the name of the series
     */
    public String getName() {
        return name;
    }

    /**
     * @param i an index
     * @return the tick at the i position
     */
    public Tick getTick(int i) {
        return ticks.get(i);
    }

    /**
     * @return the number of ticks in the series
     */
    public int getSize() {
        return (endIndex - beginIndex) + 1;
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
     * Returns a new time series which is a view of a subset of the current series.
     * <p>
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * The tick of the series are shared between the original time series and the returned one (i.e. no copy).
     * @param beginIndex the begin index (inclusive) of the time series
     * @param endIndex the end index (inclusive) of the time series
     * @return a constrained {@link TimeSeries time series} which is a sub-set of the current series
     */
    public TimeSeries subseries(int beginIndex, int endIndex) {
        return new TimeSeries(name, ticks, beginIndex, endIndex);
    }

    /**
     * Runs the strategy over the series.
     * Opens the trades with {@link OperationType.BUY} operations.
     * @param strategy the trading strategy
     * @return a list of trades
     */
    public List<Trade> run(Strategy strategy) {
        return run(strategy, OperationType.BUY);
    }

    /**
     * Runs the strategy over the series.
     * @param strategy the trading strategy
     * @param operationType the {@link OperationType} used to open the trades
     * @return a list of trades
     */
    public List<Trade> run(Strategy strategy, OperationType operationType) {

        List<Trade> trades = new ArrayList<Trade>();

        Trade lastTrade = new Trade(operationType);
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick in the sub-series...
            if (strategy.shouldOperate(lastTrade, i)) {
                lastTrade.operate(i);
                if (lastTrade.isClosed()) {
                    // Adding the trade when closed
                    trades.add(lastTrade);
                    lastTrade = new Trade(operationType);
                }
            }
        }

        if (lastTrade.isOpened()) {
            // If the last trade is still opened, we search out of the end index.
            // May works if the current series is a sub-series (but not the last sub-series).
            for (int i = endIndex + 1; i < ticks.size(); i++) {
                // For each tick out of sub-series bound...
                // --> Trying to close the last trade
                if (strategy.shouldOperate(lastTrade, i)) {
                    lastTrade.operate(i);
                    break;
                }
            }
            if (lastTrade.isClosed()) {
                // Last trade added only if it has been closed finally
                trades.add(lastTrade);
            }
        }
        return trades;
    }

    /**
     * @return the period name of the series (e.g. "from 12:00 21/01/2014 to 12:15 21/01/2014")
     */
    public String getPeriodName() {
        return ticks.get(beginIndex).getEndTime().toString("hh:mm dd/MM/yyyy - ")
                + ticks.get(endIndex).getEndTime().toString("hh:mm dd/MM/yyyy");
    }

    /**
     * @return the period of the series
     */
    public Period getPeriod() {
        final long firstTickPeriod = ticks.get(beginIndex + 1).getEndTime().getMillis() - ticks.get(beginIndex).getEndTime().getMillis();
        final long secondTickPeriod = ticks.get(beginIndex + 2).getEndTime().getMillis() - ticks.get(beginIndex + 1).getEndTime().getMillis();
        Period period = new Period(Math.min(firstTickPeriod, secondTickPeriod));
        assert !Period.ZERO.equals(period) : "Period should not be zero";
        return period;
    }
}