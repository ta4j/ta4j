/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class AdaptiveZigZagSwingDetectorTest {

    @Test
    void adaptiveThresholdKeepsSwingsWhenAtrIsLarge() {
        BarSeries series = buildVolatileRangeSeries();
        int endIndex = series.getEndIndex();

        List<ElliottSwing> baseline = baselineZigZagSwings(series, endIndex);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(1, 1.0, 0.0, 20.0, 1);
        AdaptiveZigZagSwingDetector adaptiveDetector = new AdaptiveZigZagSwingDetector(config);
        SwingDetectorResult adaptive = adaptiveDetector.detect(series, endIndex, ElliottDegree.PRIMARY);

        assertThat(adaptive.swings()).isNotEmpty();
        assertThat(adaptive.swings().size()).isGreaterThanOrEqualTo(baseline.size());
    }

    @Test
    void reusesIndicatorStateAcrossRepeatedLiveDetection() {
        CountingBarSeries series = buildLiveSeries(300);
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3);
        AdaptiveZigZagSwingDetector detector = new AdaptiveZigZagSwingDetector(config);

        SwingDetectorResult initial = detector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);
        series.resetBarReads();

        SwingDetectorResult repeated = detector.detect(series, series.getEndIndex(), ElliottDegree.SUB_MINUETTE);

        assertThat(repeated).isEqualTo(initial);
        assertThat(series.barReads()).isLessThan(20);

        SwingDetectorResult differentDegree = detector.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);
        assertThat(differentDegree.swings()).isNotEmpty().allMatch(swing -> swing.degree() == ElliottDegree.PRIMARY);
    }

    private List<ElliottSwing> baselineZigZagSwings(BarSeries series, int endIndex) {
        ClosePriceIndicator price = new ClosePriceIndicator(series);
        ATRIndicator atr = new ATRIndicator(series, 1);
        ZigZagStateIndicator state = new ZigZagStateIndicator(price, atr);
        ElliottSwingIndicator indicator = ElliottSwingIndicator.zigZag(state, price, ElliottDegree.PRIMARY);
        return indicator.getValue(endIndex);
    }

    private BarSeries buildVolatileRangeSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("AdaptiveZigZagTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        double[] closes = { 100.0, 130.0, 90.0, 140.0, 80.0, 150.0 };
        for (int i = 0; i < closes.length; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i)))
                    .openPrice(closes[i])
                    .highPrice(closes[i] + 1.0)
                    .lowPrice(closes[i] - 1.0)
                    .closePrice(closes[i])
                    .volume(1000.0)
                    .add();
        }
        return series;
    }

    private CountingBarSeries buildLiveSeries(final int barCount) {
        CountingBarSeries series = new CountingBarSeries();
        Duration period = Duration.ofMinutes(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int index = 0; index < barCount; index++) {
            double close = 100.0 + Math.sin(index * 0.2);
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(index + 1L)))
                    .openPrice(close)
                    .highPrice(close + 0.2)
                    .lowPrice(close - 0.2)
                    .closePrice(close)
                    .volume(1000.0)
                    .add();
        }
        return series;
    }

    private static final class CountingBarSeries extends BaseBarSeries {

        private long barReads;

        private CountingBarSeries() {
            super("AdaptiveZigZagLiveTest", List.of());
        }

        @Override
        public Bar getBar(final int index) {
            barReads++;
            return super.getBar(index);
        }

        private long barReads() {
            return barReads;
        }

        private void resetBarReads() {
            barReads = 0;
        }
    }

}
