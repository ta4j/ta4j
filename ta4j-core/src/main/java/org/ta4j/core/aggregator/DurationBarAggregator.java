/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.num.Num;

/**
 * Aggregates a list of {@link BacktestBar bars} into another one by
 * {@link BacktestBar#timePeriod duration}.
 */
public class DurationBarAggregator implements BarAggregator {

    /** The target time period that aggregated bars should have. */
    private final Duration timePeriod;

    private final boolean onlyFinalBars;

    /**
     * Duration based bar aggregator. Only bars with elapsed time (final bars) will
     * be created.
     *
     * @param timePeriod the target time period that aggregated bars should have
     */
    public DurationBarAggregator(final Duration timePeriod) {
        this(timePeriod, true);
    }

    /**
     * Duration based bar aggregator.
     *
     * @param timePeriod    the target time period that aggregated bars should have
     * @param onlyFinalBars if true, only bars with elapsed time (final bars) will
     *                      be created, otherwise also pending bars
     */
    public DurationBarAggregator(final Duration timePeriod, final boolean onlyFinalBars) {
        this.timePeriod = timePeriod;
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates the {@code bars} into another one by {@link #timePeriod}.
     *
     * @param bars the actual bars with actual {@code timePeriod}
     * @return the aggregated bars with new {@link #timePeriod}
     * @throws IllegalArgumentException if {@link #timePeriod} is not a
     *                                  multiplication of actual {@code timePeriod}
     */
    @Override
    public List<Bar> aggregate(final List<Bar> bars) {
        final List<Bar> aggregated = new ArrayList<>();
        if (bars.isEmpty()) {
            return aggregated;
        }
        final Bar firstBar = bars.get(0);
        // get the actual time period
        final Duration actualDur = firstBar.timePeriod();
        // check if new timePeriod is a multiplication of actual time period
        final boolean isMultiplication = this.timePeriod.getSeconds() % actualDur.getSeconds() == 0;
        if (!isMultiplication) {
            throw new IllegalArgumentException(
                    "Cannot aggregate bars: the new timePeriod must be a multiplication of the actual timePeriod.");
        }

        int i = 0;
        final Num zero = firstBar.openPrice().getNumFactory().zero();
        while (i < bars.size()) {
            BacktestBar bar = (BacktestBar) bars.get(i);
            final ZonedDateTime beginTime = bar.beginTime();
            final Num open = bar.openPrice();
            Num high = bar.highPrice();
            Num low = bar.lowPrice();

            Num close = null;
            Num volume = zero;
            Num amount = zero;
            long trades = 0;
            Duration sumDur = Duration.ZERO;

            while (isInDuration(sumDur)) {
                if (i < bars.size()) {
                    if (!beginTimesInDuration(beginTime, bars.get(i).beginTime())) {
                        break;
                    }
                    bar = (BacktestBar) bars.get(i);
                    if (high == null || bar.highPrice().isGreaterThan(high)) {
                        high = bar.highPrice();
                    }
                    if (low == null || bar.lowPrice().isLessThan(low)) {
                        low = bar.lowPrice();
                    }
                    close = bar.closePrice();

                    if (bar.volume() != null) {
                        volume = volume.plus(bar.volume());
                    }
                    if (bar.getAmount() != null) {
                        amount = amount.plus(bar.getAmount());
                    }
                    if (bar.getTrades() != 0) {
                        trades = trades + bar.getTrades();
                    }
                }

                sumDur = sumDur.plus(actualDur);
                i++;
            }

            if (!this.onlyFinalBars || i <= bars.size()) {
// FIXME how to aggregate bars without accessmto series?               final Bar aggregatedBar = new BacktestBarBuilder(new MockBarSeriesBuilder().build()).timePeriod(this.timePeriod)
//                        .endTime(beginTime.plus(this.timePeriod))
//                        .openPrice(open)
//                        .highPrice(high)
//                        .lowPrice(low)
//                        .closePrice(close)
//                        .volume(volume)
//                        .amount(amount)
//                        .trades(trades)
//                        .build();
//                aggregated.add(aggregatedBar);
            }
        }

        return aggregated;
    }

    private boolean beginTimesInDuration(final ZonedDateTime startTime, final ZonedDateTime endTime) {
        return Duration.between(startTime, endTime).compareTo(this.timePeriod) < 0;
    }

    private boolean isInDuration(final Duration duration) {
        return duration.compareTo(this.timePeriod) < 0;
    }
}
