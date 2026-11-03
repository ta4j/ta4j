/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
    public void oneBarWindowSeedsAtTheFirstAddressableBar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator indicator = new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3));
        EWMAIndicator ewma = new EWMAIndicator(indicator, 1, 0.5);

        assertEquals(0, ewma.getCountOfUnstableBars());
        assertNumEquals(1, ewma.getValue(0));
        assertNumEquals(1.5, ewma.getValue(1));
        assertNumEquals(2.25, ewma.getValue(2));
    }

    @Test
    public void includesSourceUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator indicator = new MockIndicator(series, 2, numOf(1), numOf(3), numOf(5));
        EWMAIndicator ewma = new EWMAIndicator(indicator, 3, 0.5);

        assertEquals(4, ewma.getCountOfUnstableBars());
    }

    @Test
    public void retainedValuesSurvivePruningWhileFreshIndicatorReseedsAtRetainedHead() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 3, 5).build();
        MockIndicator indicator = new MockIndicator(series, 0, numOf(1), numOf(3), numOf(5));
        EWMAIndicator ewma = new EWMAIndicator(indicator, 2, 0.5);

        assertNumEquals(2, ewma.getValue(1));
        assertNumEquals(3.5, ewma.getValue(2));

        series.setMaximumBarCount(2);

        // Retained values are full-history values (#1666): they are not rebuilt
        // from the retained head, whatever the access order.
        assertNumEquals(3.5, ewma.getValue(2));
        assertNumEquals(2, ewma.getValue(1));

        // Without cached predecessors the chain re-seeds at the retained [3, 5],
        // warming up from that head even when the later index is read first.
        EWMAIndicator fresh = new EWMAIndicator(indicator, 2, 0.5);
        assertNumEquals(4, fresh.getValue(2));
        assertTrue(fresh.getValue(1).isNaN());
    }

    @Test
    public void seedWindowLongerThanRetainedWindowIsNaN() {
        // The seed averages barCount source values. A chain read every bar reaches
        // its seed index after the first bars were pruned; it must not average
        // values mapped from removed bars.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(3);
        EWMAIndicator ewma = new EWMAIndicator(new ClosePriceIndicator(series), 4, 0.5);
        for (int i = 0; i < 8; i++) {
            series.barBuilder().closePrice(1 + 2 * i).add();
            assertTrue(ewma.getValue(i).isNaN());
        }
    }
}
