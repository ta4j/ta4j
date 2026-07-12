/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

class SlopeChangeSwingDetectorTest {

    @Test
    void constructsDefaultConfigFromWindow() {
        SlopeChangeConfig defaults = SlopeChangeConfig.defaults(5);
        SlopeChangeSwingDetector detector = new SlopeChangeSwingDetector(5);
        SwingDetector factoryDetector = SwingDetectors.slopeChange(5);

        assertThat(defaults).isEqualTo(new SlopeChangeConfig(5, 2, 14, 0.0, 0.5));
        assertThat(detector.getConfig()).isEqualTo(defaults);
        assertThat(factoryDetector).isInstanceOf(SlopeChangeSwingDetector.class);
        assertThat(((SlopeChangeSwingDetector) factoryDetector).getConfig()).isEqualTo(defaults);
    }

    @Test
    void balancedDefaultsRejectInsignificantReversals() {
        BarSeries series = noisySeries();
        SlopeChangeSwingDetector balanced = new SlopeChangeSwingDetector(2);
        SlopeChangeSwingDetector permissive = new SlopeChangeSwingDetector(new SlopeChangeConfig(2, 1, 14, 0.0, 0.0));

        SwingDetectorResult balancedResult = balanced.detect(series, series.getEndIndex(), ElliottDegree.MINOR);
        SwingDetectorResult permissiveResult = permissive.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(permissiveResult.pivots()).hasSizeGreaterThan(balancedResult.pivots().size());
    }

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

    @Test
    void rejectsPivotWhenExtremePriceIsNotFinite() {
        BarSeries series = series(1, 2, 4, 7, 9, 10, 9, 7, 4, 2);
        BarSeries invalidSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).build();
        for (int index = 0; index < series.getBarCount(); index++) {
            double close = series.getBar(index).getClosePrice().doubleValue();
            invalidSeries.barBuilder()
                    .openPrice(close)
                    .highPrice(index == 5 ? Double.NaN : close + 0.5)
                    .lowPrice(close - 0.5)
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        SlopeChangeSwingDetector detector = new SlopeChangeSwingDetector(new SlopeChangeConfig(3, 1, 3, 0.1, 0.0));

        assertThat(detector.detect(invalidSeries, invalidSeries.getEndIndex(), ElliottDegree.MINOR).pivots()).isEmpty();
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

    private BarSeries noisySeries() {
        BarSeries series = new MockBarSeriesBuilder().build();
        double[] closes = { 100.0, 100.3, 100.6, 100.2, 99.9, 100.2, 100.5, 100.1, 99.8, 100.1, 100.4, 100.0, 99.7,
                100.0, 100.3, 99.9, 99.6, 99.9, 100.2, 99.8 };
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            double halfRange = index == 0 ? 20.0 : 0.05;
            series.barBuilder()
                    .openPrice(close)
                    .highPrice(close + halfRange)
                    .lowPrice(close - halfRange)
                    .closePrice(close)
                    .volume(1)
                    .add();
        }
        return series;
    }
}
