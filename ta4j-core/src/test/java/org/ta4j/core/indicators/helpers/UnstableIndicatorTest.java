/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TestUtils;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class UnstableIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private int unstableBars;
    private UnstableIndicator unstableIndicator;

    public UnstableIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        unstableBars = 5;
        unstableIndicator = new UnstableIndicator(new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build()), unstableBars);
    }

    @Test
    public void warmUpMaskDoesNotDelayItsRecursiveSource() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(2);
        UnstableIndicator masked = new UnstableIndicator(new EMAIndicator(new ClosePriceIndicator(series), 3), 5);
        TraceTestLogger capture = new TraceTestLogger();
        capture.open();
        try {
            for (int index = 0; index < 20; index++) {
                series.barBuilder().closePrice(10).add();
                Num value = masked.getValue(index);
                if (index < 5) {
                    assertEquals(NaN.NaN, value);
                } else {
                    TestUtils.assertNumEquals(10, value);
                }
            }
        } finally {
            capture.close();
        }
    }

    @Test
    public void indicatorReturnsNanBeforeUnstableBars() {
        for (int i = 0; i < unstableBars; i++) {
            assertEquals(unstableIndicator.getValue(i), NaN.NaN);
        }
    }

    @Test
    public void indicatorNotReturnsNanAfterUnstableBars() {
        for (int i = unstableBars; i < 10; i++) {
            assertNotEquals(unstableIndicator.getValue(i), NaN.NaN);
        }
    }

}
