/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ProminenceSwingDetectorTest {

    @Test
    void factoryBuildsConfiguredProminenceDetector() {
        ProminenceSwingConfig config = new ProminenceSwingConfig(3, 1, 0, 1, 0.0);
        SwingDetector detector = SwingDetectors.prominence(config);

        assertThat(detector).isInstanceOf(ProminenceSwingDetector.class);
        assertThat(((ProminenceSwingDetector) detector).getConfig()).isEqualTo(config);
    }

    @Test
    void detectsAlternatingProminentHighsAndLows() {
        BarSeries series = series(10, 20, 10, 20, 10);
        SwingDetector detector = SwingDetectors.prominence(new ProminenceSwingConfig(3, 1, 0, 1, 0.0));

        SwingDetectorResult result = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(result.pivots()).extracting(SwingPivot::index).containsExactly(1, 2, 3);
        assertThat(result.pivots()).extracting(SwingPivot::type)
                .containsExactly(SwingPivotType.HIGH, SwingPivotType.LOW, SwingPivotType.HIGH);
        assertThat(detector.detectPivots(series, series.getEndIndex())).isEqualTo(result.pivots());
    }

    @Test
    void rejectsLocalExtremaBelowTheProminenceThreshold() {
        BarSeries series = series(10, 12, 10);
        SwingDetector detector = SwingDetectors.prominence(new ProminenceSwingConfig(2, 1, 0, 1, 2.0));

        SwingDetectorResult result = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(result.pivots()).isEmpty();
    }

    private BarSeries series(final double... closes) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
        return series;
    }
}
