/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
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

public class SGMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SGMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void sgmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        SGMAIndicator ma = new SGMAIndicator(new ClosePriceIndicator(barSeries), 9, 2);

        int unstableBars = ma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(ma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);
            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void evenBarCountThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 10, 2);

        fail("Should have thrown an exception");
    }

    @Test(expected = IllegalArgumentException.class)
    public void barCountShouldBeGreaterThanPolynomialOrderThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 3, 5);

        fail("Should have thrown an exception");
    }

    @Test
    public void headAdvancePropagatesSourceRebasePolicy() {
        // SGMA must register its source indicator, not just the series: the
        // stochastic source discards its whole cache on a head advance (its
        // zero-range branch is history-dependent beyond the default eviction
        // band), and that policy has to propagate so SGMA entries inside its
        // own default band are recomputed too. The flat retained window turns
        // every retained stochastic value from 100 into 0, so a kept SGMA entry
        // serves a stale 100 unless the source policy propagates.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : new double[] { 10, 20, 30, 40, 40, 40, 40, 40 }) {
            series.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        SGMAIndicator sgma = new SGMAIndicator(stochastic, 3, 0);
        sgma.getValue(7); // (100 + 100 + 100) / 3 = 100 before the advance

        series.setMaximumBarCount(5);
        assertEquals(3, series.getBeginIndex());

        // The re-anchored stochastic seeds at zero (the removed bars leave a
        // zero stochastic range) and stays zero over the flat retained window:
        // (0 + 0 + 0) / 3 = 0. The pre-fix SGMA kept its cached 100 because
        // index 7 sits inside the default unstable band, which the source
        // rebase policy never reached it.
        assertNumEquals(0, sgma.getValue(7));
    }
}
