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

import java.util.Arrays;

public class SwingPointMarkerIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SwingPointMarkerIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldReturnPriceOnlyAtSwingIndexes() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var swingIndicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        final var markerIndicator = new SwingPointMarkerIndicator(series, swingIndicator);

        assertThat(markerIndicator.getSwingPointIndexes()).containsExactlyInAnyOrder(2, 5);
        assertThat(markerIndicator.getValue(2)).isEqualByComparingTo(numOf(3));
        assertThat(markerIndicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(5)).isEqualByComparingTo(numOf(6));
        assertThat(markerIndicator.getValue(6)).isEqualByComparingTo(NaN);
    }

    @Test
    public void shouldDetectNewSwingPointsDynamically() {
        // Create series with swing points that are discovered progressively
        // Swing points: index 2 (value 3) and index 5 (value 6)
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var swingIndicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        final var markerIndicator = new SwingPointMarkerIndicator(series, swingIndicator);

        // Verify that getSwingPointIndexes() dynamically queries the swing indicator
        assertThat(markerIndicator.getSwingPointIndexes()).containsExactlyInAnyOrder(2, 5);

        // Verify that calculate() dynamically checks if an index is a swing point
        // Index 2: getLatestSwingIndex(2) == 2, so it's a swing point
        assertThat(markerIndicator.getValue(2)).isEqualByComparingTo(numOf(3));
        // Index 3: getLatestSwingIndex(3) == 2, so it's not a swing point
        assertThat(markerIndicator.getValue(3)).isEqualByComparingTo(NaN);
        // Index 5: getLatestSwingIndex(5) == 5, so it's a swing point
        assertThat(markerIndicator.getValue(5)).isEqualByComparingTo(numOf(6));
        // Index 6: getLatestSwingIndex(6) == 5, so it's not a swing point
        assertThat(markerIndicator.getValue(6)).isEqualByComparingTo(NaN);
    }

    @Test
    public void shouldReturnPriceValuesForAllSwingPointsIncludingEarlierOnes() {
        // This test verifies the fix for the bug where earlier swing points would
        // return NaN instead of their price values. The fix uses
        // getSwingPointIndexesUpTo(index).contains(index) instead of
        // getLatestSwingIndex(index) == index to correctly identify all swing points.
        //
        // Create series with multiple swing points: indexes 2, 5, and 8
        // Values: 1, 2, 3, 4, 5, 6, 7, 8, 9
        // Swing points at: index 2 (value 3), index 5 (value 6), index 8 (value 9)
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7, 8, 9);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5, 5, 8 };
        final var swingIndicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);
        final var markerIndicator = new SwingPointMarkerIndicator(series, swingIndicator);

        // Verify all swing points are identified
        assertThat(markerIndicator.getSwingPointIndexes()).containsExactlyInAnyOrder(2, 5, 8);

        // After index 8 is confirmed, verify that earlier swing points still return
        // their correct price values. This is the key test - the old implementation
        // using getLatestSwingIndex(index) == index would work for index 8, but we
        // need to ensure it works for all swing points, including earlier ones.
        assertThat(markerIndicator.getValue(2)).isEqualByComparingTo(numOf(3));
        assertThat(markerIndicator.getValue(5)).isEqualByComparingTo(numOf(6));
        assertThat(markerIndicator.getValue(8)).isEqualByComparingTo(numOf(9));

        // Verify non-swing points return NaN
        assertThat(markerIndicator.getValue(0)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(1)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(3)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(4)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(6)).isEqualByComparingTo(NaN);
        assertThat(markerIndicator.getValue(7)).isEqualByComparingTo(NaN);
    }

    @Test
    public void shouldExposeUnderlyingSwingAndPriceIndicators() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2 };
        final var priceIndicator = new ClosePriceIndicator(series);
        final var swingIndicator = new FixedSwingIndicator(priceIndicator, latestSwingIndexes);
        final var markerIndicator = new SwingPointMarkerIndicator(series, swingIndicator);

        assertThat(markerIndicator.getSwingIndicator()).isSameAs(swingIndicator);
        assertThat(markerIndicator.getPriceIndicator()).isSameAs(priceIndicator);
    }

    private BarSeries seriesFromCloses(double... closes) {
        final var seriesBuilder = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            seriesBuilder.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        return seriesBuilder;
    }

    private static final class FixedSwingIndicator extends AbstractRecentSwingIndicator {

        private final int[] latestSwingIndexes;

        private FixedSwingIndicator(Indicator<Num> priceIndicator, int[] latestSwingIndexes) {
            super(priceIndicator, 0);
            this.latestSwingIndexes = Arrays.copyOf(latestSwingIndexes, latestSwingIndexes.length);
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            if (index < 0) {
                return -1;
            }
            if (index >= latestSwingIndexes.length) {
                return latestSwingIndexes[latestSwingIndexes.length - 1];
            }
            return latestSwingIndexes[index];
        }
    }
}
