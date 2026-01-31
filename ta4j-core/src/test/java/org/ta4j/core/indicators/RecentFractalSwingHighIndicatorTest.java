/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class RecentFractalSwingHighIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public RecentFractalSwingHighIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = createSeriesFromHighs(10, 12, 15, 13, 11, 17, 16, 14, 18, 16, 13, 19, 17, 16);
    }

    @Test
    public void shouldReturnMostRecentSwingHigh() {
        final var indicator = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 2, 2, 0);

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(3).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(3)).isEqualTo(-1);

        assertThat(indicator.getValue(4)).isEqualByComparingTo(numOf(15));
        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(2);

        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(15));
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(2);

        assertThat(indicator.getValue(7)).isEqualByComparingTo(numOf(17));
        assertThat(indicator.getLatestSwingIndex(7)).isEqualTo(5);

        assertThat(indicator.getValue(10)).isEqualByComparingTo(numOf(18));
        assertThat(indicator.getLatestSwingIndex(10)).isEqualTo(8);

        assertThat(indicator.getValue(13)).isEqualByComparingTo(numOf(19));
        assertThat(indicator.getLatestSwingIndex(13)).isEqualTo(11);
    }

    @Test
    public void shouldDelayConfirmationUntilFollowingBarsAvailable() {
        final var indicator = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 2, 2, 0);

        assertThat(indicator.getLatestSwingIndex(5)).isEqualTo(2);
        assertThat(indicator.getValue(5)).isEqualByComparingTo(numOf(15));

        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(2);
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(15));

        assertThat(indicator.getLatestSwingIndex(7)).isEqualTo(5);
        assertThat(indicator.getValue(7)).isEqualByComparingTo(numOf(17));
    }

    @Test
    public void shouldAllowFlatTopsWhenEqualBarsPermitted() {
        final var plateauSeries = createSeriesFromHighs(9, 11, 10, 12, 12, 9, 8, 7);
        final var noEquals = new RecentFractalSwingHighIndicator(new HighPriceIndicator(plateauSeries), 2, 2, 0);
        final var withEquals = new RecentFractalSwingHighIndicator(new HighPriceIndicator(plateauSeries), 2, 2, 1);

        assertThat(noEquals.getValue(6).isNaN()).isTrue();
        assertThat(noEquals.getLatestSwingIndex(6)).isEqualTo(-1);

        assertThat(withEquals.getValue(6)).isEqualByComparingTo(numOf(12));
        assertThat(withEquals.getLatestSwingIndex(6)).isEqualTo(4);
    }

    @Test
    public void shouldRejectFlatTopsThatExceedEqualAllowance() {
        final var plateauSeries = createSeriesFromHighs(8, 12, 12, 12, 12, 10, 8, 7);
        final var indicator = new RecentFractalSwingHighIndicator(new HighPriceIndicator(plateauSeries), 1, 2, 1);

        assertThat(indicator.getValue(6).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(-1);
    }

    @Test
    public void shouldRemoveStaleSwingPointsWhenFlatTopExceedsAllowance() {
        final var plateauSeries = createSeriesFromHighs(9, 10, 10, 10, 10, 8, 7);
        final var indicator = new RecentFractalSwingHighIndicator(new HighPriceIndicator(plateauSeries), 1, 0, 1);

        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(1, 2);
        assertThat(indicator.getLatestSwingIndex(2)).isEqualTo(2);

        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(-1);
        assertThat(indicator.getSwingPointIndexesUpTo(4)).isEmpty();
        assertThat(indicator.getSwingPointIndexes()).isEmpty();
    }

    @Test
    public void shouldPropagateNaNFromUnderlyingIndicator() {
        final var baseSeries = createSeriesFromHighs(10, 12, 15, 13, 11, 17, 16, 14, 18, 16, 13);
        final var highIndicator = new HighPriceIndicator(baseSeries);
        final var indicatorWithNaNFollowing = new RecentFractalSwingHighIndicator(indicatorWithNaN(highIndicator, 6), 2,
                2, 0);

        assertThat(indicatorWithNaNFollowing.getValue(7)).isEqualByComparingTo(numOf(15));
        assertThat(indicatorWithNaNFollowing.getLatestSwingIndex(7)).isEqualTo(2);
        assertThat(indicatorWithNaNFollowing.getValue(10)).isEqualByComparingTo(numOf(15));

        final var shortSeries = createSeriesFromHighs(10, 12, 15, 13, 11, 9, 8);
        final var shortIndicator = new HighPriceIndicator(shortSeries);
        final var indicator = new RecentFractalSwingHighIndicator(indicatorWithNaN(shortIndicator, 3), 2, 2, 0);

        assertThat(indicator.getValue(5).isNaN()).isTrue();
        assertThat(indicator.getLatestSwingIndex(5)).isEqualTo(-1);
    }

    @Test
    public void shouldExposeSwingPointIndexes() {
        final var indicator = new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 2, 2, 0);

        assertThat(indicator.getSwingPointIndexesUpTo(6)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(7)).containsExactly(2, 5);
        assertThat(indicator.getSwingPointIndexes()).containsExactly(2, 5, 8, 11);
    }

    private BarSeries createSeriesFromHighs(double... highs) {
        final var barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double high : highs) {
            final double low = high - 2d;
            barSeries.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).add();
        }
        return barSeries;
    }

    private Indicator<Num> indicatorWithNaN(Indicator<Num> delegate, int... indices) {
        final Set<Integer> invalidIndices = Arrays.stream(indices).boxed().collect(Collectors.toSet());
        return new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return invalidIndices.contains(index) ? NaN : delegate.getValue(index);
            }

            @Override
            public BarSeries getBarSeries() {
                return delegate.getBarSeries();
            }

            @Override
            public int getCountOfUnstableBars() {
                return delegate.getCountOfUnstableBars();
            }
        };
    }
}
