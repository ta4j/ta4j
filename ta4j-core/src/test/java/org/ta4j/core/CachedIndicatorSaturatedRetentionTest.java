/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Guards the {@link CachedIndicator} head-advance reconciliation contract for
 * saturated series whose retained window legitimately reaches
 * {@link Integer#MAX_VALUE}, where the sentinel value "discard every cached
 * entry" collides with a real bar index.
 */
class CachedIndicatorSaturatedRetentionTest {

    @Test
    void fullDiscardAfterSaturatedHeadAdvanceRecomputesLastBarValue() {
        BaseBarSeries seeded = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10, 20)
                .build();
        List<Bar> bars = seeded.getBarData();
        // Bar A occupies Integer.MAX_VALUE - 1, bar B Integer.MAX_VALUE. Every
        // index in the retained window is therefore >= MAX_VALUE - 1, which is
        // exactly where the discard-everything sentinel lives.
        BaseBarSeries series = new BaseBarSeries("saturated", bars, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1, false, DoubleNumFactory.getInstance(), new MockBarBuilderFactory());
        CountingFullDiscardIndicator indicator = new CountingFullDiscardIndicator(series);

        Num first = indicator.getValue(series.getEndIndex());
        assertNumEquals(20, first);
        assertEquals(1, indicator.calculations());

        // Evicting bar A moves the head to Integer.MAX_VALUE without touching
        // bar B, so no bar content changed and the last-bar cache would be
        // served as-is unless the sentinel is recognized as a discard signal.
        series.setMaximumBarCount(1);
        Num afterAdvance = indicator.getValue(series.getEndIndex());

        assertNumEquals(20, afterAdvance);
        assertEquals(2, indicator.calculations());
    }

    private static final class CountingFullDiscardIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        CountingFullDiscardIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
            return true;
        }

        int calculations() {
            return calculations.get();
        }
    }
}
