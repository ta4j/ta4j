/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

/**
 * Unit tests for {@link IndicatorSeriesUtils}.
 */
public class IndicatorSeriesUtilsTest {

    /**
     * Verifies that matching indicators return their shared series.
     */
    @Test
    public void shouldReturnSharedSeriesWhenIndicatorsMatch() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> volume = new VolumeIndicator(series, 1);

        BarSeries resolved = IndicatorSeriesUtils.requireSameSeries(closePrice, volume);

        assertThat(resolved).isSameAs(series);
    }

    /**
     * Verifies that a missing first indicator series fails fast.
     */
    @Test
    public void shouldRejectFirstIndicatorWithoutSeries() {
        Indicator<Num> firstWithoutSeries = new SerieslessNumIndicator();
        Indicator<Num> secondWithoutSeries = new SerieslessNumIndicator();

        assertThatThrownBy(() -> IndicatorSeriesUtils.requireSameSeries(firstWithoutSeries, secondWithoutSeries))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstIndicator must reference a bar series");
    }

    /**
     * Verifies that a missing second indicator series fails fast.
     */
    @Test
    public void shouldRejectSecondIndicatorWithoutSeries() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> secondWithoutSeries = new SerieslessNumIndicator();

        assertThatThrownBy(() -> IndicatorSeriesUtils.requireSameSeries(closePrice, secondWithoutSeries))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secondIndicator must reference a bar series");
    }

    /**
     * Minimal indicator implementation used to simulate null-series inputs.
     */
    private static final class SerieslessNumIndicator implements Indicator<Num> {

        /**
         * Returns no numeric output for this helper indicator.
         */
        @Override
        public Num getValue(int index) {
            return null;
        }

        /**
         * Reports zero unstable bars for this helper indicator.
         */
        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        /**
         * Intentionally returns {@code null} to emulate an invalid indicator.
         */
        @Override
        public BarSeries getBarSeries() {
            return null;
        }
    }
}
