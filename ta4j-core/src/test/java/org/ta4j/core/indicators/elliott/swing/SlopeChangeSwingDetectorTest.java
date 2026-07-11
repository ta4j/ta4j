/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class SlopeChangeSwingDetectorTest {

    @Test
    void detectsRoundedTurnsWithoutRepaintingConfirmedPrefix() {
        BarSeries series = series(1, 2, 4, 7, 9, 10, 9, 7, 4, 2, 1, 2, 4, 7, 9);
        SlopeChangeSwingDetector detector = new SlopeChangeSwingDetector(new SlopeChangeConfig(3, 2, 3, 0.1, 0.0));

        SwingDetectorResult prefix = detector.detect(series, 10, ElliottDegree.MINOR);
        SwingDetectorResult complete = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(prefix.pivots()).isNotEmpty();
        assertThat(complete.pivots().subList(0, prefix.pivots().size())).isEqualTo(prefix.pivots());
        assertThat(complete.pivots()).extracting(SwingPivot::type).contains(SwingPivotType.HIGH, SwingPivotType.LOW);
        assertThat(complete.pivots()).allSatisfy(pivot -> assertThat(pivot.index()).isBetween(4, 11));
    }

    @Test
    void validatesConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SlopeChangeConfig(1, 1, 1, 0.0, 0.0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SlopeChangeConfig(2, 0, 1, 0.0, 0.0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SlopeChangeConfig(2, 1, 1, Double.NaN, 0.0));
    }

    private BarSeries series(double... closes) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (double close : closes) {
            series.barBuilder()
                    .openPrice(close)
                    .highPrice(close + 0.5)
                    .lowPrice(close - 0.5)
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}
