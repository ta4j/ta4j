/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.StochasticIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SMMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void smmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(SMMAIndicatorTest.class, "SMMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        SMMAIndicator ma = new SMMAIndicator(new ClosePriceIndicator(barSeries), 10);

        for (int i = 0; i < barSeries.getBarCount(); i++) {

            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);

            assertNumEquals(expected.doubleValue(), value);

        }
    }

    @Test
    public void headAdvanceRebuildsChainedAverageFromRetainedHead() {
        // SMMA chains every value from its predecessor, so when the chain is
        // severed by a head advance the recursion must re-anchor at the first
        // retained bar instead of backtracking into removed history. The
        // stochastic source drops its whole cache on the advance (zero range
        // at the retained head), which propagates to the SMMA and forces the
        // rebuild that the base case must then seed correctly.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();
        series.barBuilder().openPrice(50).closePrice(50).highPrice(50).lowPrice(0).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();
        series.barBuilder().openPrice(50).closePrice(50).highPrice(50).lowPrice(0).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();
        series.barBuilder().openPrice(50).closePrice(50).highPrice(50).lowPrice(0).add();
        StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        SMMAIndicator smma = new SMMAIndicator(stochastic, 2);
        smma.getValue(5);

        series.setMaximumBarCount(3);
        assertEquals(3, series.getBeginIndex());

        // The re-anchored chain seeds at the first retained source value (0,
        // the removed bars leave a zero stochastic range) and recurses forward:
        // (0 * 1 + 0) / 2 = 0, then (0 * 1 + 100) / 2 = 50. The pre-fix chain
        // would keep serving the stale NaN values computed before the advance.
        assertNumEquals(0, smma.getValue(3));
        assertNumEquals(0, smma.getValue(4));
        assertNumEquals(50, smma.getValue(5));
    }

    @Test
    public void headAdvanceReanchorsChainFromRetainedHeadWithStableSource() {
        // When the source does not itself rebaseline on a head advance, the
        // recursive cache keeps a suffix that mixes pre- and post-advance inputs
        // (e.g. 130.0006 at bar 14); the retained-head seed must instead discard
        // the whole cache and rebuild from the first retained bar, matching a
        // fresh calculation over the retained window (140 at bar 14, doubling
        // every two bars via the 0.5 smoothing).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i <= 19; i++) {
            series.barBuilder().openPrice(i * 10).closePrice(i * 10).highPrice(i * 10).lowPrice(i * 10).add();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMMAIndicator smma = new SMMAIndicator(close, 2);
        smma.getValue(19);

        series.setMaximumBarCount(6);
        assertEquals(14, series.getBeginIndex());

        SMMAIndicator fresh = new SMMAIndicator(close, 2);
        for (int i = 14; i <= 19; i++) {
            assertNumEquals(fresh.getValue(i), smma.getValue(i));
        }
    }

}
