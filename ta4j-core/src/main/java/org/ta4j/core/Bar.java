/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import org.ta4j.core.num.Num;

/**
 * A {@code Bar} is aggregated open/high/low/close/volume/etc. data over a time
 * period. It represents the "end bar" of a time period.
 */
public interface Bar extends Serializable {

    /**
     * @return the time period of the bar
     */
    Duration getTimePeriod();

    /**
     * @return the begin timestamp of the bar period (in UTC).
     */
    Instant getBeginTime();

    /**
     * @return the end timestamp of the bar period (in UTC).
     */
    Instant getEndTime();

    /**
     * @return the open price of the bar period
     */
    Num getOpenPrice();

    /**
     * @return the high price of the bar period
     */
    Num getHighPrice();

    /**
     * @return the low price of the bar period
     */
    Num getLowPrice();

    /**
     * @return the close price of the bar period
     */
    Num getClosePrice();

    /**
     * @return the total traded volume of the bar period
     */
    Num getVolume();

    /**
     * @return the total traded amount (tradePrice x tradeVolume) of the bar period
     */
    Num getAmount();

    /**
     * @return the number of trades of the bar period
     */
    long getTrades();

    /**
     * @param timestamp a timestamp
     * @return true if the provided timestamp is between the begin time and the end
     *         time of the current period, false otherwise
     */
    default boolean inPeriod(Instant timestamp) {
        return timestamp != null && !timestamp.isBefore(getBeginTime()) && timestamp.isBefore(getEndTime());
    }

    /**
     * @return the bar's begin time in UTC as {@link ZonedDateTime}
     */
    default ZonedDateTime getZonedBeginTime() {
        return getBeginTime().atZone(ZoneOffset.UTC);
    }

    /**
     * @return the bar's end time in UTC as {@link ZonedDateTime}
     */
    default ZonedDateTime getZonedEndTime() {
        return getEndTime().atZone(ZoneOffset.UTC);
    }

    /**
     * Converts the begin time of the bar to a time in the system's time zone.
     *
     * <p>
     * <b>Warning:</b> The use of {@link ZoneId#systemDefault()} may introduce
     * variability based on the system's default time zone settings. This can result
     * in inconsistencies in time calculations and comparisons, particularly due to
     * daylight saving time (DST). It is recommended to always utilize either
     * {@link #getBeginTime()} or {@link #getZonedBeginTime()} for accurate results.
     *
     * @return the bar's begin time converted to system time zone
     */
    default ZonedDateTime getSystemZonedBeginTime() {
        return getBeginTime().atZone(ZoneId.systemDefault());
    }

    /**
     * Converts the end time of the bar to a time in the system's time zone.
     *
     * <p>
     * <b>Warning:</b> The use of {@link ZoneId#systemDefault()} may introduce
     * variability based on the system's default time zone settings. This can result
     * in inconsistencies in time calculations and comparisons, particularly due to
     * daylight saving time (DST). It is recommended to always utilize either
     * {@link #getEndTime()} or {@link #getZonedEndTime()} for accurate results.
     *
     * @return the bar's end time converted to system time zone
     */
    default ZonedDateTime getSystemZonedEndTime() {
        return getEndTime().atZone(ZoneId.systemDefault());
    }

    /**
     * @return a user-friendly representation of the end timestamp in the system's
     *         time zone
     */
    default String getDateName() {
        return getSystemZonedEndTime().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    /**
     * @return an even more user-friendly representation of the end timestamp in the
     *         system's time zone
     */
    default String getSimpleDateName() {
        return getSystemZonedEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * @return true if this is a bearish bar, false otherwise
     */
    default boolean isBearish() {
        Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && closePrice.isLessThan(openPrice);
    }

    /**
     * @return true if this is a bullish bar, false otherwise
     */
    default boolean isBullish() {
        Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && openPrice.isLessThan(closePrice);
    }

    /**
     * Adds a trade and updates the close price at the end of the bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the actual price per asset
     */
    void addTrade(Num tradeVolume, Num tradePrice);

    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price       the actual price per asset
     * @param numFunction the numbers precision
     */
    default void addPrice(String price, Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(new BigDecimal(price)));
    }

    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price       the actual price per asset
     * @param numFunction the numbers precision
     */
    default void addPrice(Number price, Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(price));
    }

    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price the actual price per asset
     */
    void addPrice(Num price);
}
