/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Mutable OHLCV window used while aggregating bars.
 */
final class AggregatedBarWindow {

    private final NumFactory numFactory;
    private final Num zero;
    private Instant beginTime;
    private Instant endTime;
    private Num openPrice;
    private Num highPrice;
    private Num lowPrice;
    private Num closePrice;
    private Num volume;
    private Num amount;
    private long trades;

    AggregatedBarWindow(NumFactory numFactory) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.zero = numFactory.zero();
        reset();
    }

    boolean isEmpty() {
        return beginTime == null;
    }

    void add(Bar bar) {
        Objects.requireNonNull(bar, "bar");

        if (isEmpty()) {
            beginTime = bar.getBeginTime();
            openPrice = bar.getOpenPrice();
            highPrice = bar.getHighPrice();
            lowPrice = bar.getLowPrice();
        } else {
            if (highPrice == null || (bar.getHighPrice() != null && bar.getHighPrice().isGreaterThan(highPrice))) {
                highPrice = bar.getHighPrice();
            }
            if (lowPrice == null || (bar.getLowPrice() != null && bar.getLowPrice().isLessThan(lowPrice))) {
                lowPrice = bar.getLowPrice();
            }
        }

        endTime = bar.getEndTime();
        closePrice = bar.getClosePrice();
        if (bar.getVolume() != null) {
            volume = volume.plus(bar.getVolume());
        }
        if (bar.getAmount() != null) {
            amount = amount.plus(bar.getAmount());
        }
        trades += bar.getTrades();
    }

    Num priceRange() {
        if (isEmpty() || highPrice == null || lowPrice == null) {
            return zero;
        }
        return highPrice.minus(lowPrice);
    }

    Num volume() {
        return volume;
    }

    Bar build() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot build an aggregated bar from an empty window.");
        }

        Duration aggregatedPeriod = Duration.between(beginTime, endTime);
        return new TimeBarBuilder(numFactory).timePeriod(aggregatedPeriod)
                .endTime(endTime)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .closePrice(closePrice)
                .volume(volume)
                .amount(amount)
                .trades(trades)
                .build();
    }

    void reset() {
        beginTime = null;
        endTime = null;
        openPrice = null;
        highPrice = null;
        lowPrice = null;
        closePrice = null;
        volume = zero;
        amount = zero;
        trades = 0L;
    }
}
