/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TRIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TRIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(8).closePrice(12).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(6).closePrice(8).highPrice(11).lowPrice(6).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();

        TRIndicator tr = new TRIndicator(series);

        assertNumEquals(7, tr.getValue(0));
        assertNumEquals(6, tr.getValue(1));
        assertNumEquals(9, tr.getValue(2));
        assertNumEquals(3, tr.getValue(3));
        assertNumEquals(15, tr.getValue(4));
    }

    @Test
    public void unstableBarsStartImmediatelyWhenCloseHasNoWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(8).closePrice(12).highPrice(15).lowPrice(8).add();

        TRIndicator tr = new TRIndicator(series);

        assertEquals(0, tr.getCountOfUnstableBars());
        assertFalse(Num.isNaNOrNull(tr.getValue(0)));
    }

    @Test
    public void unstableBarsIncludeCloseIndicatorWarmupPlusLookback() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(8).closePrice(12).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(6).closePrice(8).highPrice(11).lowPrice(6).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();

        Indicator<Num> closeWithWarmup = new SMAIndicator(new ClosePriceIndicator(series), 2);
        TRIndicator tr = new TRIndicator(new HighPriceIndicator(series), new LowPriceIndicator(series),
                closeWithWarmup);

        assertEquals(closeWithWarmup.getCountOfUnstableBars() + 1, tr.getCountOfUnstableBars());
    }

    @Test
    public void calculatesFromProvidedPriceIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(8).closePrice(12).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(6).closePrice(8).highPrice(11).lowPrice(6).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();

        TRIndicator tr = new TRIndicator(new HighPriceIndicator(series), new LowPriceIndicator(series),
                new ClosePriceIndicator(series));

        assertNumEquals(7, tr.getValue(0));
        assertNumEquals(6, tr.getValue(1));
        assertNumEquals(9, tr.getValue(2));
        assertNumEquals(3, tr.getValue(3));
        assertNumEquals(15, tr.getValue(4));
    }

    @Test
    public void throwsForMismatchedSeries() {
        BarSeries firstSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        BarSeries secondSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();

        assertThrows(IllegalArgumentException.class, () -> new TRIndicator(new HighPriceIndicator(firstSeries),
                new LowPriceIndicator(firstSeries), new ClosePriceIndicator(secondSeries)));
    }

    @Test
    public void accountsForPreviousCloseLookbackInUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(8).closePrice(12).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(6).closePrice(8).highPrice(11).lowPrice(6).add();
        series.barBuilder().openPrice(14).closePrice(15).highPrice(17).lowPrice(14).add();

        Indicator<Num> delayedClose = new SMAIndicator(new ClosePriceIndicator(series), 2);
        TRIndicator tr = new TRIndicator(new HighPriceIndicator(series), new LowPriceIndicator(series), delayedClose);

        assertEquals(2, tr.getCountOfUnstableBars());
        assertTrue(Num.isNaNOrNull(tr.getValue(1)));
        assertNumEquals(7, tr.getValue(2));
    }

    @Test
    public void firstRetainedValueRecomputedAfterHeadAdvance() {
        // The true range at the first retained index was computed using the
        // previous close before the head advanced; that close is gone after
        // the advance, so the value must be recomputed as high - low only
        // and match a fresh indicator.
        // Flat open/close candles with a three-unit lower wick: each true
        // range is the 10-unit close jump rather than the 3-unit high-low, so
        // after the head advance the first retained true range must become
        // high-low = 3 instead of the stale 10.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(-3).add();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(7).add();
        series.barBuilder().openPrice(20).closePrice(20).highPrice(20).lowPrice(17).add();
        series.barBuilder().openPrice(30).closePrice(30).highPrice(30).lowPrice(27).add();
        series.barBuilder().openPrice(40).closePrice(40).highPrice(40).lowPrice(37).add();
        series.barBuilder().openPrice(50).closePrice(50).highPrice(50).lowPrice(47).add();
        TRIndicator tr = new TRIndicator(series);
        assertNumEquals(10, tr.getValue(3));

        series.setMaximumBarCount(3);
        assertEquals(3, series.getBeginIndex());

        TRIndicator fresh = new TRIndicator(series);
        for (int i = 3; i <= 5; i++) {
            assertNumEquals(fresh.getValue(i), tr.getValue(i));
        }
    }
}
