package org.ta4j.core.mocks;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarConvertibleBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * @author Lukáš Kvídera
 */
public class MockBarBuilder extends BaseBarConvertibleBuilder {

    private boolean periodSet;
    private boolean endTimeSet;

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
        return super.timePeriod(timePeriod);
    }

    @Override
    public BaseBar build() {
        if (!periodSet) {
            timePeriod(Duration.ofDays(1));
        }

        if (!endTimeSet) {
            endTime(ZonedDateTime.now());
        }
        return super.build();
    }
}
