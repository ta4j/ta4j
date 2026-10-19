/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Close-price source that appends bars to its bounded series the first time a
 * chosen index is read, so the series head advances in the middle of a
 * dependent recursive average's computation.
 */
final class HeadAdvancingSource extends AbstractIndicator<Num> {

    private final BarSeries series;
    private final ClosePriceIndicator closePrice;
    private final int triggerIndex;
    private final int[] appendedCloses;
    private boolean triggered;

    private HeadAdvancingSource(BarSeries series, int triggerIndex, int... appendedCloses) {
        super(series);
        this.series = series;
        this.closePrice = new ClosePriceIndicator(series);
        this.triggerIndex = triggerIndex;
        this.appendedCloses = appendedCloses;
    }

    @Override
    public Num getValue(int index) {
        if (index == triggerIndex && !triggered) {
            triggered = true;
            for (int close : appendedCloses) {
                series.barBuilder().closePrice(close).add();
            }
        }
        return closePrice.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * Reads an average over a five-bar window holding bars 6 to 10 while its source
     * appends three bars when bar 8 is read, moving the head to bar 9 mid-read. Bar
     * 8 is removed before its value is computed, so no continuation through it
     * exists: the read and every retained value must match a fresh average seeded
     * at the new head, with no value derived from a removed bar.
     *
     * @param numFactory the number factory
     * @param average    creates the average under test from its source
     */
    static void assertReadRecomputesAfterMidReadHeadAdvance(NumFactory numFactory,
            Function<Indicator<Num>, Indicator<Num>> average) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(5);
        for (int i = 0; i <= 10; i++) {
            series.barBuilder().closePrice(10 + (i * 7) % 5).add();
        }
        Indicator<Num> raced = average.apply(new HeadAdvancingSource(series, 8, 30, 12, 25));

        Num racedRead = raced.getValue(10);

        Indicator<Num> fresh = average.apply(new ClosePriceIndicator(series));
        assertEquals(9, series.getBeginIndex());
        assertNumEquals(fresh.getValue(10), racedRead);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), raced.getValue(i));
        }
    }
}
