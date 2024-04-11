package org.ta4j.core.mocks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarConvertibleBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * @author Lukáš Kvídera
 */
public class MockBarBuilder extends BaseBarConvertibleBuilder {

    private Clock clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault());
    private boolean periodSet;
    private boolean endTimeSet;

    private static long countOfProducedBars;
    private Duration timePeriod;

    public MockBarBuilder(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    public BaseBarConvertibleBuilder endTime(final ZonedDateTime endTime) {
        endTimeSet = true;
        return super.endTime(endTime);
    }

    @Override
    public BaseBarConvertibleBuilder timePeriod(final Duration timePeriod) {
        periodSet = true;
        this.timePeriod = timePeriod;
        return super.timePeriod(this.timePeriod);
    }

    @Override
    public BaseBar build() {
        if (!periodSet) {
            timePeriod(Duration.ofDays(1));
        }

        if (!endTimeSet) {
            endTime(ZonedDateTime.now(Clock.offset(clock, timePeriod.multipliedBy(++countOfProducedBars))));
        }
        return super.build();
    }
}
