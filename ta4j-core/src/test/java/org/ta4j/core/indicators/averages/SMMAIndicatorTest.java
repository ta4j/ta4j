/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.*;

import org.junit.jupiter.api.Test;
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
    public void headAdvanceKeepsRetainedChainWhileFreshIndicatorReseedsAtRetainedHead() {
        // Retained SMMA values are full-history values (#1666), so a head advance
        // keeps them; only an indicator without cached predecessors seeds at the
        // first retained bar (140 at bar 14).
        BarSeries series = rampSeries();
        BarSeries unbounded = rampSeries();
        SMMAIndicator smma = new SMMAIndicator(new ClosePriceIndicator(series), 2);
        smma.getValue(19);

        series.setMaximumBarCount(6);
        assertEquals(14, series.getBeginIndex());

        SMMAIndicator reference = new SMMAIndicator(new ClosePriceIndicator(unbounded), 2);
        for (int i = 14; i <= 19; i++) {
            assertNumEquals(reference.getValue(i), smma.getValue(i));
        }
        // A removed index maps to the first available bar's retained value.
        assertNumEquals(smma.getValue(14), smma.getValue(13));

        SMMAIndicator fresh = new SMMAIndicator(new ClosePriceIndicator(series), 2);
        assertNumEquals(140, fresh.getValue(14));
        assertNumEquals(145, fresh.getValue(15));
    }

    @Test
    public void emptySeriesReturnsNaNWithoutRecursing() {
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        assertThat(new SMMAIndicator(new ClosePriceIndicator(empty), 3).getValue(0).isNaN()).isTrue();
    }

    @Test
    public void readRecomputesWhenHeadAdvancesMidRead() {
        HeadAdvancingSource.assertReadRecomputesAfterMidReadHeadAdvance(numFactory,
                source -> new SMMAIndicator(source, 3));
    }

    private BarSeries rampSeries() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i <= 19; i++) {
            series.barBuilder().openPrice(i * 10).closePrice(i * 10).highPrice(i * 10).lowPrice(i * 10).add();
        }
        return series;
    }

}
