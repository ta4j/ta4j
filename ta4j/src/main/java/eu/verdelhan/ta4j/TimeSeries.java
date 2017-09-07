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

import java.io.Serializable;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * Sequence of {@link Tick ticks} separated by a predefined period (e.g. 15 minutes, 1 day, etc.)
 * <p>
 * Notably, a {@link TimeSeries time series} can be:
 * <ul>
 * <li>the base of {@link Indicator indicator} calculations
 * <li>constrained between begin and end indexes (e.g. for some backtesting cases)
 * <li>limited to a fixed number of ticks (e.g. for actual trading)
 * </ul>
 */
public interface TimeSeries extends Serializable {

    /**
     * @return the name of the series
     */
    String getName();

    /**
     * @param i an index
     * @return the tick at the i-th position
     */
    Tick getTick(int i);

    /**
     * @return the first tick of the series
     */
    default Tick getFirstTick() {
        return getTick(getBeginIndex());
    }

    /**
     * @return the last tick of the series
     */
    default Tick getLastTick() {
        return getTick(getEndIndex());
    }

    /**
     * @return the number of ticks in the series
     */
    int getTickCount();

    /**
     * @return true if the series is empty, false otherwise
     */
    default boolean isEmpty() {
    	return getTickCount() == 0;
    }
    
    /**
     * Warning: should be used carefully!
     * <p>
     * Returns the raw tick data.
     * It means that it returns the current List object used internally to store the {@link Tick ticks}.
     * It may be:
     *   - a shortened tick list if a maximum tick count has been set
     *   - a extended tick list if it is a constrained time series
     * @return the raw tick data
     */
    List<Tick> getTickData();
    
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
        if (!getTickData().isEmpty()) {
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
    void setMaximumTickCount(int maximumTickCount);

    /**
     * @return the maximum number of ticks
     */
    int getMaximumTickCount();

    /**
     * @return the number of removed ticks
     */
    int getRemovedTicksCount();
    
    /**
     * Adds a tick at the end of the series.
     * <p>
     * Begin index set to 0 if if wasn't initialized.<br>
     * End index set to 0 if if wasn't initialized, or incremented if it matches the end of the series.<br>
     * Exceeding ticks are removed.
     * @param tick the tick to be added
     * @see TimeSeries#setMaximumTickCount(int)
     */
    void addTick(Tick tick);
}
