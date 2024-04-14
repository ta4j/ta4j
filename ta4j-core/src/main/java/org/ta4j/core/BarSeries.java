/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A {@code BarSeries} is a sequence of {@link Bar bars} separated by a
 * predefined period (e.g. 15 minutes, 1 day, etc.).
 *
 * Notably, it can be:
 *
 * <ul>
 * <li>the base of {@link Indicator indicator} calculations
 * <li>constrained between beginning and ending indices (e.g. for some
 * backtesting cases)
 * <li>limited to a fixed number of bars (e.g. for actual trading)
 * </ul>
 */
public interface BarSeries extends Serializable {
    /**
     * Advances time to next bar.
     *
     * Notifies strategies to refresh their state
     *
     * @return true if advanced to next bar
     */
    boolean advance();

    /**
     * @return factory that generates numbers usable in this BarSeries
     */
    NumFactory numFactory();

    /**
     * @return builder that generates compatible bars
     */
    BacktestBarConvertibleBuilder barBuilder();

    /**
     * @return the name of the series
     */
    String getName();

    /**
     * Gets the bar from {@link #getBarData()}.
     *
     * @return the bar at the current position
     */
    Bar getBar();

    /**
     * @return the first bar of the series
     */
    // FIXME if needed
    default Bar getFirstBar() {
        return getBar();
    }

    /**
     * @return the last bar of the series
     */
    // FIXME if needed
    default Bar getLastBar() {
        return getBar();
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
     * Returns the raw bar data, i.e. it returns the current list object, which is
     * used internally to store the {@link Bar bars}. It may be:
     *
     * <ul>
     * <li>a shortened bar list if a {@code maximumBarCount} has been set.
     * <li>an extended bar list if it is a constrained bar series.
     * </ul>
     *
     * <p>
     * <b>Warning:</b> This method should be used carefully!
     *
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
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to
     *         12:15 21/01/2014")
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
     * Adds the {@code bar} at the end of the series.
     *
     * <p>
     * The {@code beginIndex} is set to {@code 0} if not already initialized.<br>
     * The {@code endIndex} is set to {@code 0} if not already initialized, or
     * incremented if it matches the end of the series.<br>
     * Exceeding bars are removed.
     *
     * @param bar the bar to be added
     */
    default void addBar(Bar bar) {
        addBar(bar, false);
    }

    /**
     * Adds the {@code bar} at the end of the series.
     *
     * <p>
     * The {@code beginIndex} is set to {@code 0} if not already initialized.<br>
     * The {@code endIndex} is set to {@code 0} if not already initialized, or
     * incremented if it matches the end of the series.<br>
     * Exceeding bars are removed.
     *
     * @param bar     the bar to be added
     * @param replace true to replace the latest bar. Some exchanges continuously
     *                provide new bar data in the respective period, e.g. 1 second
     *                in 1 minute duration.
     */
    void addBar(Bar bar, boolean replace);

    /**
     * Adds a trade and updates the close price of the last bar.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     * @see Bar#addTrade(Num, Num)
     */
    default void addTrade(Number tradeVolume, Number tradePrice) {
        addTrade(numFactory().numOf(tradeVolume), numFactory().numOf(tradePrice));
    }

    /**
     * Adds a trade and updates the close price of the last bar.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     * @see Bar#addTrade(Num, Num)
     */
    void addTrade(Num tradeVolume, Num tradePrice);

    /**
     * Updates the close price of the last bar. The open, high and low prices are
     * also updated as needed.
     *
     * @param price the price for the bar
     * @see Bar#addPrice(Num)
     */
    void addPrice(Num price);

    /**
     * Updates the close price of the last bar. The open, high and low prices are
     * also updated as needed.
     *
     * @param price the price for the bar
     * @see Bar#addPrice(Num)
     */
    default void addPrice(Number price) {
        addPrice(numFactory().numOf(price));
    }
}
