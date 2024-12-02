/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.bars;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link Bar}.
 */
public class BaseBar implements Bar {

    private static final long serialVersionUID = 8038383777467488147L;

    /** The time period (e.g. 1 day, 15 min, etc.) of the bar. */
    private final Duration timePeriod;

    /** The begin time of the bar period (in UTC). */
    private final Instant beginTime;

    /** The end time of the bar period (in UTC). */
    private final Instant endTime;

    /** The open price of the bar period. */
    private Num openPrice;

    /** The high price of the bar period. */
    private Num highPrice;

    /** The low price of the bar period. */
    private Num lowPrice;

    /** The close price of the bar period. */
    private Num closePrice;

    /** The total traded volume of the bar period. */
    private Num volume;

    /** The total traded amount of the bar period. */
    private Num amount;

    /** The number of trades of the bar period. */
    private long trades;

    /**
     * Constructor.
     *
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period (in UTC)
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the total traded volume of the bar period
     * @param amount     the total traded amount of the bar period
     * @param trades     the number of trades of the bar period
     */
    BaseBar(Duration timePeriod, Instant endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice,
            Num volume, Num amount, long trades) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.trades = trades;
    }

    /**
     * @return the time period of the bar (must be the same for all bars within the
     *         same {@code BarSeries})
     */
    @Override
    public Duration getTimePeriod() {
        return timePeriod;
    }

    /**
     * @return the begin timestamp of the bar period (derived by {@link #endTime} -
     *         {@link #timePeriod})
     */
    @Override
    public Instant getBeginTime() {
        return beginTime;
    }

    @Override
    public Instant getEndTime() {
        return endTime;
    }

    @Override
    public Num getOpenPrice() {
        return openPrice;
    }

    @Override
    public Num getHighPrice() {
        return highPrice;
    }

    @Override
    public Num getLowPrice() {
        return lowPrice;
    }

    @Override
    public Num getClosePrice() {
        return closePrice;
    }

    @Override
    public Num getVolume() {
        return volume;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public long getTrades() {
        return trades;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        addPrice(tradePrice);

        volume = volume.plus(tradeVolume);
        amount = amount.plus(tradeVolume.multipliedBy(tradePrice));
        trades++;
    }

    @Override
    public void addPrice(Num price) {
        if (openPrice == null) {
            openPrice = price;
        }
        closePrice = price;
        if (highPrice == null || highPrice.isLessThan(price)) {
            highPrice = price;
        }
        if (lowPrice == null || lowPrice.isGreaterThan(price)) {
            lowPrice = price;
        }
    }

    /**
     * @return {end time, close price, open price, low price, high price, volume}
     */
    @Override
    public String toString() {
        return String.format(
                "{end time: %1s, close price: %2s, open price: %3s, low price: %4s high price: %5s, volume: %6s}",
                endTime, closePrice, openPrice, lowPrice, highPrice, volume);
    }

    /**
     * @param timePeriod the time period
     * @param endTime    the end time of the bar
     * @throws NullPointerException if one of the arguments is null
     */
    private static void checkTimeArguments(Duration timePeriod, Instant endTime) {
        Objects.requireNonNull(timePeriod, "Time period cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");
    }

    @Override
    public int hashCode() {
        return Objects.hash(beginTime, endTime, timePeriod, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof BaseBar))
            return false;
        final BaseBar other = (BaseBar) obj;
        return Objects.equals(beginTime, other.beginTime) && Objects.equals(endTime, other.endTime)
                && Objects.equals(timePeriod, other.timePeriod) && Objects.equals(openPrice, other.openPrice)
                && Objects.equals(highPrice, other.highPrice) && Objects.equals(lowPrice, other.lowPrice)
                && Objects.equals(closePrice, other.closePrice) && Objects.equals(volume, other.volume)
                && Objects.equals(amount, other.amount) && trades == other.trades;
    }
}
