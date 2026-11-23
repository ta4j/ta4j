/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TrendStrengthIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendStrengthIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void transientSourceValueIsNotPublishedToTheCheckpoint() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4)
                .withMaxBarCount(10)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        AtomicBoolean sourceFailing = new AtomicBoolean(true);
        CachedIndicator<Num> flaky = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                if (index == 1 && sourceFailing.get()) {
                    markResultTransient();
                    return NaN.NaN;
                }
                return close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        TrendStrengthIndicator trend = new TrendStrengthIndicator(flaky, 3, true);

        trend.getValue(3);
        sourceFailing.set(false);
        series.barBuilder().closePrice(5).add();

        assertEquals(3, trend.getValue(4));
    }
}
