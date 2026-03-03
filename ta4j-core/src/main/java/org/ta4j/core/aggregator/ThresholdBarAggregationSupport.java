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
            Predicate<WindowView> completionPredicate, Function<WindowView, Bar> barBuilder) {
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
            if (completionPredicate.test(mutableWindow)) {
                aggregated.add(barBuilder.apply(mutableWindow));
                mutableWindow.reset();
            }
        }

        if (!onlyFinalBars && !mutableWindow.isEmpty()) {
            aggregated.add(barBuilder.apply(mutableWindow));
        }

        return aggregated;
    }

    static Bar buildTimeBar(NumFactory numFactory, WindowView window) {
        Duration aggregatedPeriod = Duration.between(window.beginTime(), window.endTime());
        return new TimeBarBuilder(numFactory).timePeriod(aggregatedPeriod)
                .endTime(window.endTime())
                .openPrice(window.openPrice())
                .highPrice(window.highPrice())
                .lowPrice(window.lowPrice())
                .closePrice(window.closePrice())
                .volume(window.volume())
                .amount(window.amount())
                .trades(window.trades())
                .build();
    }

    interface WindowView {

        Instant beginTime();

        Instant endTime();

        Num openPrice();

        Num highPrice();

        Num lowPrice();

        Num closePrice();

        Num volume();

        Num amount();

        long trades();
    }

    private static final class MutableWindow implements WindowView {

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

        @Override
        public Instant beginTime() {
            return beginTime;
        }

        @Override
        public Instant endTime() {
            return endTime;
        }

        @Override
        public Num openPrice() {
            return openPrice;
        }

        @Override
        public Num highPrice() {
            return highPrice;
        }

        @Override
        public Num lowPrice() {
            return lowPrice;
        }

        @Override
        public Num closePrice() {
            return closePrice;
        }

        @Override
        public Num volume() {
            return volume;
        }

        @Override
        public Num amount() {
            return amount;
        }

        @Override
        public long trades() {
            return trades;
        }
    }
}
