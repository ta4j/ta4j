/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EWMAIndicatorTest extends AbstractIndicatorTest<MockIndicator, Num> {

    public EWMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void initializesFromSimpleAverageThenAppliesDecay() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        MockIndicator indicator = new MockIndicator(series, 0, numOf(1), numOf(3), numOf(5), numOf(7));
        EWMAIndicator ewma = new EWMAIndicator(indicator, 3, 0.5);

        assertTrue(ewma.getValue(1).isNaN());
        assertNumEquals(3, ewma.getValue(2));
        assertNumEquals(5, ewma.getValue(3));
    }

    @Test
    public void includesSourceUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator indicator = new MockIndicator(series, 2, numOf(1), numOf(3), numOf(5));
        EWMAIndicator ewma = new EWMAIndicator(indicator, 3, 0.5);

        assertEquals(4, ewma.getCountOfUnstableBars());
    }
}
