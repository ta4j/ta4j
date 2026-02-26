/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ta4j.core.Bar;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared accumulation workflow for threshold-based bar aggregators.
 */
final class ThresholdBarAggregationSupport {

    private ThresholdBarAggregationSupport() {
    }

    static List<Bar> aggregate(List<Bar> bars, NumFactory numFactory, boolean onlyFinalBars,
            Predicate<WindowSnapshot> completionPredicate, Function<WindowSnapshot, Bar> barBuilder) {
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(numFactory, "numFactory");
        Objects.requireNonNull(completionPredicate, "completionPredicate");
        Objects.requireNonNull(barBuilder, "barBuilder");

        List<Bar> aggregated = new ArrayList<>();
        if (bars.isEmpty()) {
            return aggregated;
        }

        MutableWindow mutableWindow = new MutableWindow(numFactory);
        for (Bar bar : bars) {
            mutableWindow.add(bar);
            WindowSnapshot snapshot = mutableWindow.snapshot();
            if (completionPredicate.test(snapshot)) {
                aggregated.add(barBuilder.apply(snapshot));
                mutableWindow.reset();
            }
        }

        if (!onlyFinalBars && !mutableWindow.isEmpty()) {
            aggregated.add(barBuilder.apply(mutableWindow.snapshot()));
        }

        return aggregated;
    }

    static Bar buildTimeBar(NumFactory numFactory, WindowSnapshot snapshot) {
        Duration aggregatedPeriod = Duration.between(snapshot.beginTime(), snapshot.endTime());
        return new TimeBarBuilder(numFactory).timePeriod(aggregatedPeriod)
                .endTime(snapshot.endTime())
                .openPrice(snapshot.openPrice())
                .highPrice(snapshot.highPrice())
                .lowPrice(snapshot.lowPrice())
                .closePrice(snapshot.closePrice())
                .volume(snapshot.volume())
                .amount(snapshot.amount())
                .trades(snapshot.trades())
                .build();
    }

    static final class WindowSnapshot {

        private final Instant beginTime;
        private final Instant endTime;
        private final Num openPrice;
        private final Num highPrice;
        private final Num lowPrice;
        private final Num closePrice;
        private final Num volume;
        private final Num amount;
        private final long trades;

        private WindowSnapshot(Instant beginTime, Instant endTime, Num openPrice, Num highPrice, Num lowPrice,
                Num closePrice, Num volume, Num amount, long trades) {
            this.beginTime = beginTime;
            this.endTime = endTime;
            this.openPrice = openPrice;
            this.highPrice = highPrice;
            this.lowPrice = lowPrice;
            this.closePrice = closePrice;
            this.volume = volume;
            this.amount = amount;
            this.trades = trades;
        }

        Instant beginTime() {
            return beginTime;
        }

        Instant endTime() {
            return endTime;
        }

        Num openPrice() {
            return openPrice;
        }

        Num highPrice() {
            return highPrice;
        }

        Num lowPrice() {
            return lowPrice;
        }

        Num closePrice() {
            return closePrice;
        }

        Num volume() {
            return volume;
        }

        Num amount() {
            return amount;
        }

        long trades() {
            return trades;
        }
    }

    private static final class MutableWindow {

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

        private MutableWindow(NumFactory numFactory) {
            this.zero = numFactory.zero();
            reset();
        }

        private void add(Bar bar) {
            if (beginTime == null) {
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

        private WindowSnapshot snapshot() {
            return new WindowSnapshot(beginTime, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                    trades);
        }

        private boolean isEmpty() {
            return beginTime == null;
        }

        private void reset() {
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
}
