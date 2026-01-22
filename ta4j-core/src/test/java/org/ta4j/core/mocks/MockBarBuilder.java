/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.mocks;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.NumFactory;

public class MockBarBuilder extends TimeBarBuilder {

    private final Instant beginTime = Instant.EPOCH;
    private boolean periodSet;
    private boolean endTimeSet;

    private static long countOfProducedBars;
    private Duration timePeriod;

    public MockBarBuilder(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    public BarBuilder endTime(final Instant endTime) {
        endTimeSet = true;
        return super.endTime(endTime);
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        periodSet = true;
        this.timePeriod = timePeriod;
        return super.timePeriod(this.timePeriod);
    }

    @Override
    public Bar build() {
        if (!periodSet) {
            timePeriod(Duration.ofDays(1));
        }

        if (!endTimeSet) {
            endTime(beginTime.plus(timePeriod.multipliedBy(++countOfProducedBars)));
        }
        return super.build();
    }

}
