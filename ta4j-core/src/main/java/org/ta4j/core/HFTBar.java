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
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.ta4j.core.num.Num;

/**
 * High-Frequency Trading implementation of a {@link Bar}.
 * 
 * <p>
 * This implementation is thread-safe. It uses atomic references and operations
 * to ensure that multiple threads can safely read from and write to an HFTBar instance
 * without explicit synchronization. This makes it suitable for use in multi-threaded
 * environments such as high-frequency trading systems where multiple threads might be
 * updating the same bar with new trades or prices.
 */
public class HFTBar implements Bar {

    private static final long serialVersionUID = 8038383777467488147L;

    /** The time period (e.g. 1 day, 15 min, etc.) of the bar. */
    private final Duration timePeriod;

    /** The begin time of the bar period (in UTC). */
    private final Instant beginTime;

    /** The end time of the bar period (in UTC). */
    private final Instant endTime;

    /** 
     * The open price of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> openPrice;

    /** 
     * The high price of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> highPrice;

    /** 
     * The low price of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> lowPrice;

    /** 
     * The close price of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> closePrice;

    /** 
     * The total traded volume of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> volume;

    /** 
     * The total traded amount of the bar period.
     * Uses AtomicReference for thread-safe operations.
     */
    private AtomicReference<Num> amount;

    /** 
     * The number of trades of the bar period.
     * Uses AtomicLong for thread-safe operations.
     */
    private AtomicLong trades;

    /**
     * Constructor.
     *
     * <p>
     * The {@link #beginTime} will be calculated by {@link #endTime} -
     * {@link #timePeriod}.
     * 
     * <p>
     * This constructor initializes all mutable fields with thread-safe atomic 
     * references and counters. The price, volume, and amount fields are wrapped in 
     * {@link AtomicReference} instances, and the trades count is stored in an 
     * {@link AtomicLong}. This ensures that all operations on these fields can be 
     * performed safely in a multi-threaded environment without explicit synchronization.
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
     * @throws NullPointerException if {@link #endTime} or {@link #timePeriod} is
     *                              {@code null}
     */
    public HFTBar(Duration timePeriod, Instant endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice,
            Num volume, Num amount, long trades) {
        this.timePeriod = Objects.requireNonNull(timePeriod, "Time period cannot be null");
        this.endTime = Objects.requireNonNull(endTime, "End time cannot be null");
        this.beginTime = endTime.minus(timePeriod);
        this.openPrice = new AtomicReference<>(openPrice);
        this.highPrice = new AtomicReference<>(highPrice);
        this.lowPrice = new AtomicReference<>(lowPrice);
        this.closePrice = new AtomicReference<>(closePrice);
        this.volume = new AtomicReference<>(volume);
        this.amount = new AtomicReference<>(amount);
        this.trades = new AtomicLong(trades);
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

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the open price at the time of the call.
     */
    @Override
    public Num getOpenPrice() {
        return openPrice.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the high price at the time of the call.
     */
    @Override
    public Num getHighPrice() {
        return highPrice.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the low price at the time of the call.
     */
    @Override
    public Num getLowPrice() {
        return lowPrice.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the close price at the time of the call.
     */
    @Override
    public Num getClosePrice() {
        return closePrice.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the volume at the time of the call.
     */
    @Override
    public Num getVolume() {
        return volume.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic reference,
     * providing a thread-safe view of the amount at the time of the call.
     */
    @Override
    public Num getAmount() {
        return amount.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation returns the current value of the atomic counter,
     * providing a thread-safe view of the trades count at the time of the call.
     */
    @Override
    public long getTrades() {
        return trades.get();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation is thread-safe. It uses atomic operations to ensure that
     * multiple threads can safely add trades to the same bar without data corruption
     * or inconsistency. The method uses compare-and-set operations to atomically
     * update volume and amount, and atomic increment for the trades count.
     */
    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        addPrice(tradePrice);

        Num currentVolume, newVolume;
        do {
            currentVolume = volume.get();
            newVolume = currentVolume.plus(tradeVolume);
        } while (!volume.compareAndSet(currentVolume, newVolume));

        Num currentAmount, newAmount;
        do {
            currentAmount = amount.get();
            newAmount = currentAmount.plus(tradeVolume.multipliedBy(tradePrice));
        } while (!amount.compareAndSet(currentAmount, newAmount));

        trades.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation is thread-safe. It uses atomic operations to ensure that
     * multiple threads can safely update prices without data corruption or inconsistency.
     * The method uses compare-and-set operations to atomically update open, high, and low prices
     * when appropriate. Note that close price is always set to the most recent price.
     * 
     * <p>
     * The high price is updated only if the new price is higher than the current high price,
     * and the low price is updated only if the new price is lower than the current low price.
     * These updates are performed atomically to ensure consistency in a multi-threaded environment.
     */
    @Override
    public void addPrice(Num price) {
        Num currentOpenPrice = openPrice.get();
        if (currentOpenPrice == null) {
            openPrice.compareAndSet(null, price);
        }

        closePrice.set(price);

        Num currentHighPrice;
        do {
            currentHighPrice = highPrice.get();
            if (currentHighPrice != null && !currentHighPrice.isLessThan(price)) {
                break;
            }
        } while (!highPrice.compareAndSet(currentHighPrice, price));

        Num currentLowPrice;
        do {
            currentLowPrice = lowPrice.get();
            if (currentLowPrice != null && !currentLowPrice.isGreaterThan(price)) {
                break;
            }
        } while (!lowPrice.compareAndSet(currentLowPrice, price));
    }

    /**
     * Returns a string representation of this bar.
     * 
     * <p>
     * This method is thread-safe as it uses the getter methods which access
     * the atomic references in a thread-safe manner.
     * 
     * @return {end time, close price, open price, low price, high price, volume}
     */
    @Override
    public String toString() {
        return String.format(
                "{end time: %1s, close price: %2s, open price: %3s, low price: %4s high price: %5s, volume: %6s}",
                endTime, getClosePrice(), getOpenPrice(), getLowPrice(), getHighPrice(), getVolume());
    }

    /**
     * Returns a hash code value for this bar.
     * 
     * <p>
     * This method is thread-safe as it uses the getter methods which access
     * the atomic references in a thread-safe manner.
     * 
     * @return a hash code value for this bar
     */
    @Override
    public int hashCode() {
        return Objects.hash(beginTime, endTime, timePeriod, getOpenPrice(), getHighPrice(), getLowPrice(), getClosePrice(), getVolume(), getAmount(),
                getTrades());
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * 
     * <p>
     * This method is thread-safe as it uses the getter methods which access
     * the atomic references in a thread-safe manner.
     * 
     * @param obj the reference object with which to compare
     * @return true if this object is the same as the obj argument; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof HFTBar))
            return false;
        final HFTBar other = (HFTBar) obj;
        return Objects.equals(beginTime, other.beginTime) && Objects.equals(endTime, other.endTime)
                && Objects.equals(timePeriod, other.timePeriod) && Objects.equals(getOpenPrice(), other.getOpenPrice())
                && Objects.equals(getHighPrice(), other.getHighPrice()) && Objects.equals(getLowPrice(), other.getLowPrice())
                && Objects.equals(getClosePrice(), other.getClosePrice()) && Objects.equals(getVolume(), other.getVolume())
                && Objects.equals(getAmount(), other.getAmount()) && getTrades() == other.getTrades();
    }
}