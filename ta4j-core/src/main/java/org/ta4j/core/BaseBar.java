/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link Bar}.
 */
public class BaseBar implements Bar {
    private static final long serialVersionUID = 8038383777467488147L;

    /**
     * Monotonic signal for mutations of bars currently retained by a
     * {@link BaseBarSeries}. The series revision synchronizer uses this signal to
     * invalidate cached consumers without scanning every retained bar.
     */
    private static final AtomicLong RETAINED_BAR_MUTATION_EPOCH = new AtomicLong();

    /**
     * Atomic updater for {@link #mutationTrackingUsers}: the same {@code BaseBar}
     * can be retained by several series (for example through
     * {@link BaseBarSeries#getSubSeries}), and those series synchronize on
     * independent locks, so the attachment count must be maintained atomically at
     * the bar level or concurrent attachments can lose increments.
     */
    private static final AtomicIntegerFieldUpdater<BaseBar> MUTATION_TRACKING_USERS = AtomicIntegerFieldUpdater
            .newUpdater(BaseBar.class, "mutationTrackingUsers");

    /**
     * Number of series retaining this bar. Package-private attachment methods keep
     * construction-time builder mutations out of the retained-bar signal. Volatile
     * so lock-free reads (see {@link #publishRetainedBarMutation()}) observe the
     * latest count.
     */
    private volatile transient int mutationTrackingUsers;

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
     *                                  match the provided timePeriod, if the high
     *                                  price is below the low price, if volume or
     *                                  amount is negative, or if the number of
     *                                  trades is negative
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of bar data is a documented constructor contract: invalid bars "
            + "are rejected before any partially initialized instance can escape")
    public BaseBar(Duration timePeriod, Instant beginTime, Instant endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount, long trades) {

        this(resolvedTimes(timePeriod, beginTime, endTime), openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of bar data is a documented constructor contract: invalid bars "
            + "are rejected before any partially initialized instance can escape")
    private BaseBar(ResolvedTimes times, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount, long trades) {
        if (highPrice != null && lowPrice != null && highPrice.isLessThan(lowPrice)) {
            throw new IllegalArgumentException(
                    "High price must be greater than or equal to low price, but was " + highPrice + " < " + lowPrice);
        }
        if (volume != null && volume.isNegative()) {
            throw new IllegalArgumentException("Volume cannot be negative, but was " + volume);
        }
        if (amount != null && amount.isNegative()) {
            throw new IllegalArgumentException("Amount cannot be negative, but was " + amount);
        }
        if (trades < 0) {
            throw new IllegalArgumentException("Number of trades cannot be negative, but was " + trades);
        }
        this.timePeriod = times.timePeriod();
        this.beginTime = times.beginTime();
        this.endTime = times.endTime();
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.trades = trades;
    }

    private static ResolvedTimes resolvedTimes(Duration timePeriod, Instant beginTime, Instant endTime) {
        final Duration resolvedTimePeriod;
        if (timePeriod != null) {
            if (beginTime != null && endTime != null
                    && timePeriod.compareTo(Duration.between(beginTime, endTime)) != 0) {
                throw new IllegalArgumentException(
                        "The calculated timePeriod between beginTime and endTime does not match the given timePeriod.");
            }
            resolvedTimePeriod = timePeriod;
        } else if (beginTime != null && endTime != null) {
            resolvedTimePeriod = Duration.between(beginTime, endTime);
        } else {
            throw new NullPointerException("Time period cannot be null");
        }

        final Instant resolvedBeginTime;
        if (beginTime == null && endTime != null) {
            resolvedBeginTime = endTime.minus(resolvedTimePeriod);
        } else if (beginTime != null) {
            resolvedBeginTime = beginTime;
        } else {
            throw new NullPointerException("Begin time cannot be null");
        }

        final Instant resolvedEndTime;
        if (beginTime != null && endTime == null) {
            resolvedEndTime = beginTime.plus(resolvedTimePeriod);
        } else if (endTime != null) {
            resolvedEndTime = endTime;
        } else {
            throw new NullPointerException("End time cannot be null");
        }

        return new ResolvedTimes(resolvedTimePeriod, resolvedBeginTime, resolvedEndTime);
    }

    private record ResolvedTimes(Duration timePeriod, Instant beginTime, Instant endTime) {
    }

    static long retainedBarMutationEpoch() {
        return RETAINED_BAR_MUTATION_EPOCH.get();
    }

    @Serial
    private void readObject(final ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        mutationTrackingUsers = 0;
    }

    void attachToBarSeries() {
        MUTATION_TRACKING_USERS.incrementAndGet(this);
    }

    void detachFromBarSeries() {
        int users;
        do {
            users = MUTATION_TRACKING_USERS.get(this);
            if (users <= 0) {
                throw new IllegalStateException("Bar is not attached to a bar series");
            }
        } while (!MUTATION_TRACKING_USERS.compareAndSet(this, users, users - 1));
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

    @SuppressFBWarnings(value = "AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE", justification = "BaseBar mutators are intentionally mutable; concurrent callers must synchronize at the series boundary.")
    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        applyTradePrice(tradePrice);

        volume = volume.plus(tradeVolume);
        amount = amount.plus(tradeVolume.multipliedBy(tradePrice));
        trades++;

        // Publish exactly one mutation signal, and only after open/high/low/close,
        // volume, amount, and trades are all updated, so revision-aware consumers
        // invalidated by the signal never observe a half-applied trade.
        publishRetainedBarMutation();
    }

    @Override
    public void addPrice(Num price) {
        applyTradePrice(price);
        publishRetainedBarMutation();
    }

    private void applyTradePrice(Num price) {
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

    private void publishRetainedBarMutation() {
        if (mutationTrackingUsers > 0) {
            RETAINED_BAR_MUTATION_EPOCH.incrementAndGet();
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
        if (obj == null || getClass() != obj.getClass())
            return false;
        final BaseBar other = (BaseBar) obj;
        return Objects.equals(beginTime, other.beginTime) && Objects.equals(endTime, other.endTime)
                && Objects.equals(timePeriod, other.timePeriod) && Objects.equals(openPrice, other.openPrice)
                && Objects.equals(highPrice, other.highPrice) && Objects.equals(lowPrice, other.lowPrice)
                && Objects.equals(closePrice, other.closePrice) && Objects.equals(volume, other.volume)
                && Objects.equals(amount, other.amount) && trades == other.trades;
    }
}
