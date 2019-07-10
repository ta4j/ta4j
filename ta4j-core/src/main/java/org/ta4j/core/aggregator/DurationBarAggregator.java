package org.ta4j.core.aggregator;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bar aggregator basing on duration.
 */
public class DurationBarAggregator implements BarAggregator {

    /**
     * Target time period to aggregate
     */
    private final Duration timePeriod;
    private final boolean onlyFinalBars;

    /**
     * Duration basing bar aggregator. Only bars with elapsed time (final bars) will be created.
     *
     * @param timePeriod time period to aggregate
     */
    public DurationBarAggregator(Duration timePeriod) {
        this(timePeriod, true);
    }

    /**
     * Duration basing bar aggregator
     *
     * @param timePeriod    time period to aggregate
     * @param onlyFinalBars if true only bars with elapsed time (final bars) will be created, otherwise also pending bars
     */
    public DurationBarAggregator(Duration timePeriod, boolean onlyFinalBars) {
        this.timePeriod = timePeriod;
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates a list of bars by <code>timePeriod</code>.The new <code>timePeriod</code> must be a multiplication of the actual time
     * period.
     *
     * @param bars the actual bars
     * @return the aggregated bars with new <code>timePeriod</code>
     */
    @Override
    public List<Bar> aggregate(List<Bar> bars) {
        final List<Bar> aggregated = new ArrayList<>();
        if (bars.isEmpty()) {
            return aggregated;
        }
        // get the actual time period
        final Duration actualDur = bars.iterator().next().getTimePeriod();
        // check if new timePeriod is a multiplication of actual time period
        final boolean isMultiplication = timePeriod.getSeconds() % actualDur.getSeconds() == 0;
        if (!isMultiplication) {
            throw new IllegalArgumentException(
                    "Cannot aggregate bars: the new timePeriod must be a multiplication of the actual timePeriod.");
        }

        int i = 0;
        final Num zero = bars.iterator().next().getOpenPrice().numOf(0);
        while (i < bars.size()) {
            Bar bar = bars.get(i);
            final ZonedDateTime beginTime = bar.getBeginTime();
            final Num open = bar.getOpenPrice();
            Num high = bar.getHighPrice();
            Num low = bar.getLowPrice();

            Num close = null;
            Num volume = zero;
            Num amount = zero;
            Duration sumDur = Duration.ZERO;

            while (sumDur.compareTo(timePeriod) < 0) {
                if (i < bars.size()) {
                    bar = bars.get(i);

                    if (high == null || bar.getHighPrice().isGreaterThan(high)) {
                        high = bar.getHighPrice();
                    }
                    if (low == null || bar.getLowPrice().isLessThan(low)) {
                        low = bar.getLowPrice();
                    }
                    close = bar.getClosePrice();
                    volume = volume.plus(bar.getVolume());
                    amount = amount.plus(bar.getAmount());
                }
                sumDur = sumDur.plus(actualDur);
                i++;
            }

            if (!onlyFinalBars || i <= bars.size()) {
                final Bar aggregatedBar = new BaseBar(timePeriod, beginTime.plus(timePeriod), open, high, low, close, volume, amount);
                aggregated.add(aggregatedBar);
            }
        }

        return aggregated;
    }
}
