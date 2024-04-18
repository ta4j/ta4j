package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

public class LiveIndicatorTest extends AbstractIndicatorTest {

    public LiveIndicatorTest(final Function<Number, Num> function) {
        super(function);
    }

    @Test
    public void getValue() {
        final var liveIndicator = new LiveIndicator(new BaseBarSeries() {
            @Override
            public Num numOf(final Number number) {
                return LiveIndicatorTest.super.numOf(number);
            }
        }, () -> 37.5);

        final var indicatorValue = liveIndicator.getValue(4534534);
        assertEquals(numOf(37.5), indicatorValue);
    }
}
