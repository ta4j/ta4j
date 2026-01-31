/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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
     * <ul>
     * <li>If {@link #timePeriod} is not provided, it will be calculated as
     * {@link #endTime} - {@link #beginTime}.
     * <li>If {@link #beginTime} is not provided, it will be calculated as
     * {@link #endTime} - {@link #timePeriod}.
     * <li>If {@link #endTime} is not provided, it will be calculated as
     * {@link #beginTime} + {@link #timePeriod}.
     * </ul>
     *
     * @param timePeriod the time period (optional if beginTime and endTime is
     *                   given)
     * @param beginTime  the begin time of the bar period (in UTC) (optional if
     *                   endTime is given)
     * @param endTime    the end time of the bar period (in UTC) (optional if
     *                   beginTime is given)
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the total traded volume of the bar period
     * @param amount     the total traded amount of the bar period
     * @param trades     the number of trades of the bar period
     * @throws NullPointerException     if given or calculated {@link #timePeriod},
     *                                  {@link #beginTime} or {@link #endTime}
     *                                  values are {@code null}
     * @throws IllegalArgumentException If the calculated timePeriod between the
     *                                  provided beginTime and endTime does not
     *                                  match the provided timePeriod
     */
    public BaseBar(Duration timePeriod, Instant beginTime, Instant endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount, long trades) {

        // set timePeriod
        if (timePeriod != null) {
            if (beginTime != null && endTime != null
                    && timePeriod.compareTo(Duration.between(beginTime, endTime)) != 0) {
                throw new IllegalArgumentException(
                        "The calculated timePeriod between beginTime and endTime does not match the given timePeriod.");
            }
            this.timePeriod = timePeriod;
        } else {
            this.timePeriod = beginTime != null && endTime != null ? Duration.between(beginTime, endTime)
                    : Objects.requireNonNull(timePeriod, "Time period cannot be null");
        }

        // set beginTime
        if (beginTime == null && endTime != null) {
            this.beginTime = endTime.minus(timePeriod);
        } else {
            this.beginTime = Objects.requireNonNull(beginTime, "Begin time cannot be null");
        }

        // set endTime
        if (beginTime != null && endTime == null) {
            this.endTime = beginTime.plus(timePeriod);
        } else {
            this.endTime = Objects.requireNonNull(endTime, "End time cannot be null");
        }

        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.trades = trades;
    }

    @Override
    public Duration getTimePeriod() {
        return timePeriod;
    }

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
