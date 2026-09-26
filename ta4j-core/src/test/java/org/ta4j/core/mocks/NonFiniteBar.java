/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.mocks;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

/**
 * Minimal {@link Bar} for boundary tests that need prices which
 * {@link org.ta4j.core.BaseBar} validation rejects, such as non-finite values
 * from a {@link org.ta4j.core.num.DoubleNumFactory}. The production bar builder
 * enforces open-high-low-close relationships, so a raw implementation is the
 * only way to feed undefined prices into indicator pipelines.
 */
public final class NonFiniteBar implements Bar {

    private static final long serialVersionUID = 1L;

    private final Instant beginTime;

    private final Duration timePeriod;

    private final Num open;

    private final Num high;

    private final Num low;

    private final Num close;

    /**
     * Constructor.
     *
     * @param beginTime the bar begin time; the resulting end time must be strictly
     *                  after the target series' last end time
     * @param open      the open price; may be non-finite
     * @param high      the high price; may be non-finite
     * @param low       the low price; may be non-finite
     * @param close     the close price; may be non-finite
     */
    public NonFiniteBar(Instant beginTime, Num open, Num high, Num low, Num close) {
        this.beginTime = beginTime;
        this.timePeriod = Duration.ofDays(1);
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
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
        return beginTime.plus(timePeriod);
    }

    @Override
    public Num getOpenPrice() {
        return open;
    }

    @Override
    public Num getHighPrice() {
        return high;
    }

    @Override
    public Num getLowPrice() {
        return low;
    }

    @Override
    public Num getClosePrice() {
        return close;
    }

    @Override
    public Num getVolume() {
        return open.getNumFactory().zero();
    }

    @Override
    public Num getAmount() {
        return open.getNumFactory().zero();
    }

    @Override
    public long getTrades() {
        return 0;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        // No aggregation needed for boundary fixtures.
    }

    @Override
    public void addPrice(Num price) {
        // No aggregation needed for boundary fixtures.
    }
}
