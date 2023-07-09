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
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

/**
 * Aggregates a list of {@link BaseBar bars} into another one by
 * {@link BaseBar#timePeriod duration}.
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
    public DurationBarAggregator(Duration timePeriod) {
        this(timePeriod, true);
    }

    /**
     * Duration based bar aggregator.
     *
     * @param timePeriod    the target time period that aggregated bars should have
     * @param onlyFinalBars if true, only bars with elapsed time (final bars) will
     *                      be created, otherwise also pending bars
     */
    public DurationBarAggregator(Duration timePeriod, boolean onlyFinalBars) {
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
    public List<Bar> aggregate(List<Bar> bars) {
        final List<Bar> aggregated = new ArrayList<>();
        if (bars.isEmpty()) {
            return aggregated;
        }
        final Bar firstBar = bars.get(0);
        // get the actual time period
        final Duration actualDur = firstBar.getTimePeriod();
        // check if new timePeriod is a multiplication of actual time period
        final boolean isMultiplication = timePeriod.getSeconds() % actualDur.getSeconds() == 0;
        if (!isMultiplication) {
            throw new IllegalArgumentException(
                    "Cannot aggregate bars: the new timePeriod must be a multiplication of the actual timePeriod.");
        }

        int i = 0;
        final Num zero = firstBar.getOpenPrice().zero();
        while (i < bars.size()) {
            Bar bar = bars.get(i);
            final ZonedDateTime beginTime = bar.getBeginTime();
            final Num open = bar.getOpenPrice();
            Num high = bar.getHighPrice();
            Num low = bar.getLowPrice();

            Num close = null;
            Num volume = zero;
            Num amount = zero;
            long trades = 0;
            Duration sumDur = Duration.ZERO;

            while (isInDuration(sumDur)) {
                if (i < bars.size()) {
                    if (!beginTimesInDuration(beginTime, bars.get(i).getBeginTime())) {
                        break;
                    }
                    bar = bars.get(i);
                    if (high == null || bar.getHighPrice().isGreaterThan(high)) {
                        high = bar.getHighPrice();
                    }
                    if (low == null || bar.getLowPrice().isLessThan(low)) {
                        low = bar.getLowPrice();
                    }
                    close = bar.getClosePrice();

                    if (bar.getVolume() != null) {
                        volume = volume.plus(bar.getVolume());
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

            if (!onlyFinalBars || i <= bars.size()) {
                final Bar aggregatedBar = new BaseBar(timePeriod, beginTime.plus(timePeriod), open, high, low, close,
                        volume, amount, trades);
                aggregated.add(aggregatedBar);
            }
        }

        return aggregated;
    }

    private boolean beginTimesInDuration(ZonedDateTime startTime, ZonedDateTime endTime) {
        return Duration.between(startTime, endTime).compareTo(timePeriod) < 0;
    }

    private boolean isInDuration(Duration duration) {
        return duration.compareTo(timePeriod) < 0;
    }
}
