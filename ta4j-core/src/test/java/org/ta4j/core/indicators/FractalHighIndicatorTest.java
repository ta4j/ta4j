/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class FractalHighIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    private BarSeries series;

    public FractalHighIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldDelayConfirmationUntilFollowingBarsClose() {
        // Confirmed pivots: 2 at index 4, and 5 at index 7 (preceding=2, following=2).
        series = createSeriesFromHighs(10, 12, 15, 13, 11, 16, 14, 12, 11);
        final var indicator = new FractalHighIndicator(new HighPriceIndicator(series), 2, 2);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(4);
        assertThat(indicator.getValue(3)).isFalse();
        assertThat(indicator.getValue(4)).isTrue();
        assertThat(indicator.getConfirmedFractalIndex(4)).isEqualTo(2);

        assertThat(indicator.getValue(5)).isFalse();
        assertThat(indicator.getValue(6)).isFalse();
        assertThat(indicator.getValue(7)).isTrue();
        assertThat(indicator.getConfirmedFractalIndex(7)).isEqualTo(5);
    }

    @Test
    public void shouldSupportOverlappingSignalWindows() {
        // With preceding=1/following=1, confirmations happen on 2, 4, 6.
        series = createSeriesFromHighs(9, 14, 10, 13, 9, 15, 11);
        final var indicator = new FractalHighIndicator(new HighPriceIndicator(series), 1, 1);

        assertThat(indicator.getValue(2)).isTrue();
        assertThat(indicator.getConfirmedFractalIndex(2)).isEqualTo(1);

        assertThat(indicator.getValue(4)).isTrue();
        assertThat(indicator.getConfirmedFractalIndex(4)).isEqualTo(3);

        assertThat(indicator.getValue(6)).isTrue();
        assertThat(indicator.getConfirmedFractalIndex(6)).isEqualTo(5);
    }

    @Test
    public void shouldRejectFlatPriceSequences() {
        series = createSeriesFromHighs(10, 10, 10, 10, 10, 10, 10, 10);
        final var indicator = new FractalHighIndicator(series);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(indicator.getValue(i)).isFalse();
            assertThat(indicator.getConfirmedFractalIndex(i)).isEqualTo(-1);
        }
    }

    @Test
    public void shouldRejectNaNValuesInCandidateAndNeighborWindows() {
        final var baseSeries = createSeriesFromHighs(10, 12, 15, 11, 9, 8, 7);
        final var baseIndicator = new HighPriceIndicator(baseSeries);

        final var candidateNaNIndicator = new FractalHighIndicator(withNaNAtIndex(baseIndicator, 2), 2, 2);
        assertThat(candidateNaNIndicator.getValue(4)).isFalse();
        assertThat(candidateNaNIndicator.getConfirmedFractalIndex(4)).isEqualTo(-1);

        final var leftNeighborNaNIndicator = new FractalHighIndicator(withNaNAtIndex(baseIndicator, 1), 2, 2);
        assertThat(leftNeighborNaNIndicator.getValue(4)).isFalse();
        assertThat(leftNeighborNaNIndicator.getConfirmedFractalIndex(4)).isEqualTo(-1);

        final var rightNeighborNaNIndicator = new FractalHighIndicator(withNaNAtIndex(baseIndicator, 3), 2, 2);
        assertThat(rightNeighborNaNIndicator.getValue(4)).isFalse();
        assertThat(rightNeighborNaNIndicator.getConfirmedFractalIndex(4)).isEqualTo(-1);
    }

    @Test
    public void shouldRejectInvalidWindowLengths() {
        series = createSeriesFromHighs(10, 12, 11, 13, 12);
        final var highPrice = new HighPriceIndicator(series);

        assertThrows(IllegalArgumentException.class, () -> new FractalHighIndicator((Indicator<Num>) null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FractalHighIndicator(highPrice, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new FractalHighIndicator(highPrice, 2, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializationRoundTrip() {
        series = createSeriesFromHighs(10, 12, 15, 13, 11, 16, 14, 12);
        final var original = new FractalHighIndicator(series, 2, 2);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<Boolean> restored = (Indicator<Boolean>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(FractalHighIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    private BarSeries createSeriesFromHighs(double... highs) {
        final var barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double high : highs) {
            final double low = high - 2d;
            barSeries.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).volume(10).add();
        }
        return barSeries;
    }

    private Indicator<Num> withNaNAtIndex(Indicator<Num> delegate, int nanIndex) {
        return new Indicator<>() {
            @Override
            public Num getValue(int index) {
                return index == nanIndex ? NaN.NaN : delegate.getValue(index);
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
