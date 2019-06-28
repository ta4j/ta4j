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

    public DurationBarAggregator(Duration timePeriod) {
        this.timePeriod = timePeriod;
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
        final List<Bar> sumBars = new ArrayList<>();
        if (bars.isEmpty()) {
            return sumBars;
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
            Bar b1 = bars.get(i);
            ZonedDateTime beginTime = b1.getBeginTime();
            Num open = b1.getOpenPrice();
            Num max = b1.getHighPrice();
            Num min = b1.getLowPrice();

            // set to ZERO
            Num close = zero;
            Num volume = zero;
            Num amount = zero;
            Duration sumDur = Duration.ZERO;

            while (sumDur.compareTo(timePeriod) < 0) {
                if (i < bars.size()) {
                    Bar b2 = bars.get(i);

                    if (b2.getHighPrice().isGreaterThan(max)) {
                        max = b2.getHighPrice();
                    }
                    if (b2.getLowPrice().isLessThan(min)) {
                        min = b2.getLowPrice();
                    }
                    close = b2.getClosePrice();
                    volume = volume.plus(b2.getVolume());
                    amount = amount.plus(b2.getAmount());
                }
                sumDur = sumDur.plus(actualDur);
                i++;
            }

            // add only bars with elapsed timePeriod
            if (i <= bars.size()) {
                final Bar b = new BaseBar(timePeriod, beginTime.plus(timePeriod), open, max, min, close, volume, amount);
                sumBars.add(b);
            }
        }

        return sumBars;
    }
}
