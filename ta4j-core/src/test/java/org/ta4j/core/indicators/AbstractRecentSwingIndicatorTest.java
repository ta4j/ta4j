/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AbstractRecentSwingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AbstractRecentSwingIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldExposeSwingIndexesAndValuesMonotonically() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        // Only swing points at or before the requested index are returned
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);

        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(2);
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(5);
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(6));
    }

    @Test
    public void shouldPurgeSwingIndexesThatFallBeforeSeriesBegin() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).containsExactly(2, 5);

        series.setMaximumBarCount(2); // beginIndex will advance to drop the swing at index 2
        final int endIndexAfterPurge = series.getEndIndex();
        assertThat(indicator.getSwingPointIndexesUpTo(endIndexAfterPurge)).containsExactly(5);
        assertThat(indicator.getLatestSwingIndex(endIndexAfterPurge)).isEqualTo(5);
        assertThat(indicator.getValue(endIndexAfterPurge)).isNotEqualTo(NaN);
    }

    @Test
    public void shouldFilterSwingPointsByIndexParameter() {
        // Test the specific regression: getSwingPointIndexesUpTo should filter by index
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        // Swing points at indices: 2, 5, 8
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5, 5, 8, 8 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // First call with larger index - discovers all swings
        assertThat(indicator.getSwingPointIndexesUpTo(9)).containsExactly(2, 5, 8);

        // Call with smaller index - should only return swings up to that index
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(7)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);

        // Verify filtering works regardless of call order
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();
    }

    @Test
    public void shouldFilterSwingPointsAtBoundaries() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Test at exact swing point boundaries
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(2, 5);
    }

    @Test
    public void shouldReturnEmptyListWhenNoSwingPointsUpToIndex() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        // First swing point is at index 2
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Before any swing points are discovered
        assertThat(indicator.getSwingPointIndexesUpTo(0)).isEmpty();
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();

        // After discovering swings, requesting before first swing should still return
        // empty
        indicator.getSwingPointIndexesUpTo(6); // Discover swings
        assertThat(indicator.getSwingPointIndexesUpTo(1)).isEmpty();
    }

    @Test
    public void shouldFilterCorrectlyWithMultipleSwingPoints() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        // Swing points at indices: 1, 4, 7, 10
        final int[] latestSwingIndexes = { -1, 1, 1, 1, 4, 4, 4, 7, 7, 7, 10, 10 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Discover all swings first
        assertThat(indicator.getSwingPointIndexesUpTo(11)).containsExactly(1, 4, 7, 10);

        // Test various filtering scenarios
        assertThat(indicator.getSwingPointIndexesUpTo(0)).isEmpty();
        assertThat(indicator.getSwingPointIndexesUpTo(1)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(1);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(1, 4);
        assertThat(indicator.getSwingPointIndexesUpTo(7)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(9)).containsExactly(1, 4, 7);
        assertThat(indicator.getSwingPointIndexesUpTo(10)).containsExactly(1, 4, 7, 10);
    }

    @Test
    public void shouldMaintainFilteringConsistencyRegardlessOfCallOrder() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5, 5, 8 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        // Call in ascending order
        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);

        // Create a new indicator and call in descending order
        final var indicator2 = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        assertThat(indicator2.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);
        assertThat(indicator2.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator2.getSwingPointIndexesUpTo(2)).containsExactly(2);

        // Create a new indicator and call in mixed order
        final var indicator3 = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        assertThat(indicator3.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        assertThat(indicator3.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator3.getSwingPointIndexesUpTo(8)).containsExactly(2, 5, 8);
        assertThat(indicator3.getSwingPointIndexesUpTo(4)).containsExactly(2);
    }

    @Test
    public void shouldReturnNaNWhenSwingPriceIsNaN() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5);
        final int[] latestSwingIndexes = { -1, 1, 1, 3, 3 };
        final var nanPriceIndicator = new NaNPriceIndicator(series, new ClosePriceIndicator(series), 1);
        final var indicator = new FixedSwingIndicator(nanPriceIndicator, latestSwingIndexes);

        assertThat(indicator.getValue(2)).isEqualByComparingTo(NaN);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(1, 3);
    }

    @Test
    public void shouldRetainSwingsWhenDetectorReportsNegativeWithoutPurge() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6);
        // Swings at 2 then 4; detector returns -1 at index 4 but should not clear
        final int[] latestSwingIndexes = { -1, -1, 2, 2, -1, 4 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 4);
    }

    private BarSeries seriesFromCloses(double... closes) {
        final var seriesBuilder = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            seriesBuilder.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        return seriesBuilder;
    }

    private static final class FixedSwingIndicator extends AbstractRecentSwingIndicator {

        private final List<Integer> latestSwingIndexes;

        private FixedSwingIndicator(Indicator<Num> priceIndicator, int[] latestSwingIndexes) {
            super(priceIndicator, 0);
            this.latestSwingIndexes = new ArrayList<>(Arrays.stream(latestSwingIndexes).boxed().toList());
        }

        private void setLatestSwingIndexes(int[] latestSwingIndexes) {
            this.latestSwingIndexes.clear();
            this.latestSwingIndexes.addAll(Arrays.stream(latestSwingIndexes).boxed().toList());
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            if (index < 0) {
                return -1;
            }
            if (index >= latestSwingIndexes.size()) {
                return latestSwingIndexes.get(latestSwingIndexes.size() - 1);
            }
            return latestSwingIndexes.get(index);
        }
    }

    private static final class NaNPriceIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> delegate;
        private final int nanIndex;

        private NaNPriceIndicator(BarSeries series, Indicator<Num> delegate, int nanIndex) {
            super(series);
            this.delegate = delegate;
            this.nanIndex = nanIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == nanIndex) {
                return NaN;
            }
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
