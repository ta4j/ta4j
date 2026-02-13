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

public class IndicatorSeriesUtilsTest {

    @Test
    public void shouldReturnSharedSeriesWhenIndicatorsMatch() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> volume = new VolumeIndicator(series, 1);

        BarSeries resolved = IndicatorSeriesUtils.requireSameSeries(closePrice, volume);

        assertThat(resolved).isSameAs(series);
    }

    @Test
    public void shouldRejectFirstIndicatorWithoutSeries() {
        Indicator<Num> firstWithoutSeries = new SerieslessNumIndicator();
        Indicator<Num> secondWithoutSeries = new SerieslessNumIndicator();

        assertThatThrownBy(() -> IndicatorSeriesUtils.requireSameSeries(firstWithoutSeries, secondWithoutSeries))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstIndicator must reference a bar series");
    }

    @Test
    public void shouldRejectSecondIndicatorWithoutSeries() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12).build();
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        Indicator<Num> secondWithoutSeries = new SerieslessNumIndicator();

        assertThatThrownBy(() -> IndicatorSeriesUtils.requireSameSeries(closePrice, secondWithoutSeries))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("secondIndicator must reference a bar series");
    }

    private static final class SerieslessNumIndicator implements Indicator<Num> {

        @Override
        public Num getValue(int index) {
            return null;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return null;
        }
    }
}
