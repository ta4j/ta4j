/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
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

    @Before
    public void setUp() {
        unstableBars = 5;
        unstableIndicator = new UnstableIndicator(new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build()), unstableBars);
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
