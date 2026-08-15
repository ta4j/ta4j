/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class RenkoCounterTest {

    /**
     * After eviction the counter must rebuild from the retained begin index instead
     * of scanning the evicted region back to index 0. Reads of evicted bars observe
     * clamped first-bar prices and turn every append on a full bounded series into
     * a rebuild over the whole stream history.
     */
    @Test
    public void rebuildAfterEvictionStaysWithinRetainedWindow() {
        var series = new MockBarSeriesBuilder().withData(100, 100.4, 100.9, 101.6, 101.2, 101.8).build();
        var counting = new CountingClosePriceIndicator(series);
        var counter = new RenkoCounter(counting, series.numFactory().numOf(0.5));

        for (int i = 0; i <= series.getEndIndex(); i++) {
            counter.stateAt(i);
        }

        series.setMaximumBarCount(3);
        counting.requestedIndices.clear();
        counter.stateAt(series.getEndIndex());

        int beginIndex = series.getBeginIndex();
        assertTrue("evicted indices were read: " + counting.requestedIndices,
                counting.requestedIndices.stream().allMatch(i -> i >= beginIndex));

        var fresh = new RenkoCounter(new ClosePriceIndicator(series), series.numFactory().numOf(0.5));
        for (int i = beginIndex; i <= series.getEndIndex(); i++) {
            var expected = fresh.stateAt(i);
            var actual = counter.stateAt(i);
            assertEquals("consecutive up bricks at index " + i, expected.getConsecutiveUp(), actual.getConsecutiveUp());
            assertEquals("consecutive down bricks at index " + i, expected.getConsecutiveDown(),
                    actual.getConsecutiveDown());
        }
    }

    private static final class CountingClosePriceIndicator extends ClosePriceIndicator {

        private final List<Integer> requestedIndices = new ArrayList<>();

        private CountingClosePriceIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public Num getValue(int index) {
            requestedIndices.add(index);
            return super.getValue(index);
        }
    }
}
