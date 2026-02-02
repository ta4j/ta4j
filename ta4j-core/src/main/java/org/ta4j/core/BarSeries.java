/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
     * @return factory that generates numbers usable in this BarSeries
     */
    NumFactory numFactory();

    /**
     * @return builder that generates compatible bars
     */
    BarBuilder barBuilder();

    /**
     * @return the name of the series
     */
    String getName();

    /**
     * Gets the bar from {@link #getBarData()} with index {@code i}.
     *
     * <p>
     * The given {@code i} can return the same bar within the first range of indices
     * due to {@link #setMaximumBarCount(int)}, for example: If you fill a BarSeries
     * with 30 bars and then apply a {@code maximumBarCount} of 10 bars, the first
     * 20 bars will be removed from the BarSeries. The indices going further from 0
     * to 29 remain but return the same bar from 0 to 20. The remaining 9 bars are
     * returned from index 21.
     *
     * @param i the index
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
     * @return the description of the series period (e.g. "from 2014-01-21T12:00:00Z
     *         to 2014-01-21T12:15:00Z"); times are in UTC.
     */
    default String getSeriesPeriodDescription() {
        StringBuilder sb = new StringBuilder();
        if (!getBarData().isEmpty()) {
            var endTimeFirstBar = getFirstBar().getEndTime();
            var endTimeLastBar = getLastBar().getEndTime();
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
            sb.append(formatter.format(endTimeFirstBar)).append(" - ").append(formatter.format(endTimeLastBar));
        }
        return sb.toString();
    }

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to
     *         12:15 21/01/2014"); times are in system's default time zone.
     */
    default String getSeriesPeriodDescriptionInSystemTimeZone() {
        StringBuilder sb = new StringBuilder();
        if (!getBarData().isEmpty()) {
            var endTimeFirstBar = getFirstBar().getSystemZonedEndTime();
            var endTimeLastBar = getLastBar().getSystemZonedEndTime();
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            sb.append(formatter.format(endTimeFirstBar)).append(" - ").append(formatter.format(endTimeLastBar));
        }
        return sb.toString();
    }

    /**
     * @return the maximum number of bars
     */
    int getMaximumBarCount();

    /**
     * Sets the maximum number of bars that will be retained in the series.
     * <p>
     * If a new bar is added to the series such that the number of bars will exceed
     * the maximum bar count, then the FIRST bar in the series is automatically
     * removed, ensuring that the maximum bar count is not exceeded. The indices of
     * the bar series do not change.
     *
     * @param maximumBarCount the maximum bar count
     */
    void setMaximumBarCount(int maximumBarCount);

    /**
     * @return the number of removed bars
     */
    int getRemovedBarsCount();

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
     * @see BarSeries#setMaximumBarCount(int)
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
     * @see BarSeries#setMaximumBarCount(int)
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

    /**
     * Returns a new {@link BarSeries} instance (= "subseries") that is a subset of
     * {@code this} BarSeries instance. It contains a copy of all {@link Bar bars}
     * between {@code startIndex} (inclusive) and {@code endIndex} (exclusive) of
     * {@code this} instance. The indices of {@code this} and its subseries can be
     * different, i. e. index 0 of the subseries will be the {@code startIndex} of
     * {@code this}. If {@code startIndex} {@literal <} this.seriesBeginIndex, then
     * the subseries will start with the first available bar of {@code this}. If
     * {@code endIndex} {@literal >} this.seriesEndIndex, then the subseries will
     * end at the last available bar of {@code this}.
     *
     * @param startIndex the startIndex (inclusive)
     * @param endIndex   the endIndex (exclusive)
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * @throws IllegalArgumentException if endIndex {@literal <=} startIndex or
     *                                  startIndex {@literal <} 0
     */
    BarSeries getSubSeries(int startIndex, int endIndex);

}
