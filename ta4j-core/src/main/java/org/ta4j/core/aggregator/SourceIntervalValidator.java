/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;

/**
 * Validates source bars used by interval-sensitive aggregators.
 */
final class SourceIntervalValidator {

    private SourceIntervalValidator() {
    }

    static Duration requireEvenIntervals(List<Bar> bars, String aggregatorName) {
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(aggregatorName, "aggregatorName");
        if (bars.isEmpty()) {
            return Duration.ZERO;
        }

        Bar firstBar = bars.getFirst();
        Duration expectedTimePeriod = requireConsistentPeriod(firstBar, aggregatorName, 0);
        Bar previousBar = firstBar;

        for (int i = 1; i < bars.size(); i++) {
            Bar currentBar = bars.get(i);
            Duration currentTimePeriod = requireConsistentPeriod(currentBar, aggregatorName, i);
            if (!expectedTimePeriod.equals(currentTimePeriod)) {
                throw new IllegalArgumentException(
                        String.format("%s requires even source intervals: bar %d has period %s but expected %s.",
                                aggregatorName, i, currentTimePeriod, expectedTimePeriod));
            }
            if (!currentBar.getBeginTime().equals(previousBar.getEndTime())) {
                throw new IllegalArgumentException(String.format(
                        "%s requires contiguous source intervals: bar %d begins at %s but previous bar ended at %s.",
                        aggregatorName, i, currentBar.getBeginTime(), previousBar.getEndTime()));
            }
            previousBar = currentBar;
        }

        return expectedTimePeriod;
    }

    private static Duration requireConsistentPeriod(Bar bar, String aggregatorName, int index) {
        Duration timePeriod = bar.getTimePeriod();
        if (timePeriod == null || timePeriod.isNegative() || timePeriod.isZero()) {
            throw new IllegalArgumentException(
                    String.format("%s requires positive source intervals: bar %d has invalid period %s.",
                            aggregatorName, index, timePeriod));
        }
        Duration measuredPeriod = Duration.between(bar.getBeginTime(), bar.getEndTime());
        if (!measuredPeriod.equals(timePeriod)) {
            throw new IllegalArgumentException(
                    String.format("%s requires consistent source intervals: bar %d spans %s but period is %s.",
                            aggregatorName, index, measuredPeriod, timePeriod));
        }
        return timePeriod;
    }
}
