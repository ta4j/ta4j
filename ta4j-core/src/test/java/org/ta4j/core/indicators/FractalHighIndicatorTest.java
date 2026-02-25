/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
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
}
