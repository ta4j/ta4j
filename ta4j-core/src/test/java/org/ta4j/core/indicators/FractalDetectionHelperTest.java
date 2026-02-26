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
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Unit tests for {@link FractalDetectionHelper}.
 */
public class FractalDetectionHelperTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    private BarSeries highSeries;
    private BarSeries lowSeries;

    public FractalDetectionHelperTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        highSeries = createSeriesFromHighs(10, 12, 15, 13, 11, 16, 14, 12, 11);
        lowSeries = createSeriesFromLows(15, 13, 10, 12, 14, 9, 11, 13, 14);
    }

    @Test
    public void shouldFindLatestConfirmedHighFractal() {
        final var indicator = new HighPriceIndicator(highSeries);

        final int latest = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 7, 2, 2, 0,
                FractalDetectionHelper.Direction.HIGH);

        assertThat(latest).isEqualTo(5);
    }

    @Test
    public void shouldFindLatestConfirmedLowFractal() {
        final var indicator = new LowPriceIndicator(lowSeries);

        final int latest = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, lowSeries, 7, 2, 2, 0,
                FractalDetectionHelper.Direction.LOW);

        assertThat(latest).isEqualTo(5);
    }

    @Test
    public void shouldReturnMinusOneWhenNoCandidateIsConfirmable() {
        final var indicator = new HighPriceIndicator(highSeries);

        final int latest = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 3, 2, 2, 0,
                FractalDetectionHelper.Direction.HIGH);

        assertThat(latest).isEqualTo(-1);
    }

    @Test
    public void shouldReturnMinusOneWhenIndexIsOutsideSeriesBounds() {
        final var indicator = new HighPriceIndicator(highSeries);

        final int beforeBegin = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries,
                highSeries.getBeginIndex() - 1, 2, 2, 0, FractalDetectionHelper.Direction.HIGH);
        final int afterEnd = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries,
                highSeries.getEndIndex() + 1, 2, 2, 0, FractalDetectionHelper.Direction.HIGH);

        assertThat(beforeBegin).isEqualTo(-1);
        assertThat(afterEnd).isEqualTo(-1);
    }

    @Test
    public void shouldRespectAllowedEqualBarsWhenFindingLatestFractal() {
        final var plateauSeries = createSeriesFromHighs(9, 11, 10, 12, 12, 9, 8, 7);
        final var indicator = new HighPriceIndicator(plateauSeries);

        final int withoutEquals = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, plateauSeries, 6, 2,
                2, 0, FractalDetectionHelper.Direction.HIGH);
        final int withEquals = FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, plateauSeries, 6, 2, 2,
                1, FractalDetectionHelper.Direction.HIGH);

        assertThat(withoutEquals).isEqualTo(-1);
        assertThat(withEquals).isEqualTo(4);
    }

    @Test
    public void shouldReturnMinusOneForInvalidInputs() {
        final var indicator = new HighPriceIndicator(highSeries);

        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(null, highSeries, 7, 2, 2, 0,
                FractalDetectionHelper.Direction.HIGH)).isEqualTo(-1);
        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, null, 7, 2, 2, 0,
                FractalDetectionHelper.Direction.HIGH)).isEqualTo(-1);
        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 7, 2, 2, 0, null))
                .isEqualTo(-1);
        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 7, -1, 2, 0,
                FractalDetectionHelper.Direction.HIGH)).isEqualTo(-1);
        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 7, 2, -1, 0,
                FractalDetectionHelper.Direction.HIGH)).isEqualTo(-1);
        assertThat(FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, highSeries, 7, 2, 2, -1,
                FractalDetectionHelper.Direction.HIGH)).isEqualTo(-1);
    }

    private BarSeries createSeriesFromHighs(double... highs) {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double high : highs) {
            final double low = high - 2d;
            series.barBuilder().openPrice(high).closePrice(high).highPrice(high).lowPrice(low).volume(10).add();
        }
        return series;
    }

    private BarSeries createSeriesFromLows(double... lows) {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double low : lows) {
            final double high = low + 2d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).volume(10).add();
        }
        return series;
    }
}
