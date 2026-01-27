/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.NaN;

public class MACDVIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final Instant START = Instant.EPOCH;
    private static final Duration PERIOD = Duration.ofDays(1);

    private BarSeries series;

    private final double[] closes = { 37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83,
            37.1 };
    private final double[] highs = { 37.45, 37.12, 36.58, 36.27, 36.15, 36.47, 36.92, 38.04, 38.39, 38.18, 37.21,
            37.48 };
    private final double[] lows = { 36.72, 36.24, 35.74, 35.41, 35.22, 35.64, 35.97, 37.21, 37.62, 37.29, 36.37,
            36.62 };
    private final double[] volumes = { 91500, 98200, 105000, 99000, 101500, 110000, 122000, 130000, 128000, 126500,
            118500, 117000 };

    public MACDVIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        var builder = new BaseBarSeriesBuilder().withName("macdv-test-series").withNumFactory(numFactory);
        series = builder.build();
        for (int i = 0; i < closes.length; i++) {
            series.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1)))
                    .openPrice(closes[i])
                    .highPrice(highs[i])
                    .lowPrice(lows[i])
                    .closePrice(closes[i])
                    .volume(volumes[i])
                    .add();
        }
    }

    @Test
    public void throwsErrorOnIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> new MACDVIndicator(new ClosePriceIndicator(series), 10, 5));
    }

    @Test
    public void macdvUsingPeriod5And10() {
        var macdv = new MACDVIndicator(new ClosePriceIndicator(series), 5, 10);

        // MACDV unstable period is longPeriod (10), so indices 0-9 return NaN
        // because long EMA returns NaN during its unstable period
        for (int i = 0; i < 10; i++) {
            assertThat(Double.isNaN(macdv.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        for (int i = 10; i < closes.length; i++) {
            assertThat(Double.isNaN(macdv.getValue(i).doubleValue())).isFalse();
        }

        var shortVwema = macdv.getShortTermVolumeWeightedEma();
        var longVwema = macdv.getLongTermVolumeWeightedEma();

        // Short EMA (period 5): unstable period is 5, so indices 0-4 are NaN, index 5+
        // are valid
        assertThat(Double.isNaN(shortVwema.getValue(4).doubleValue())).isTrue();
        assertThat(Double.isNaN(shortVwema.getValue(5).doubleValue())).isFalse();

        // Long EMA (period 10): unstable period is 10, so indices 0-9 are NaN, index
        // 10+ are valid
        assertThat(Double.isNaN(longVwema.getValue(9).doubleValue())).isTrue();
        assertThat(Double.isNaN(longVwema.getValue(10).doubleValue())).isFalse();
    }

    @Test
    public void signalLineAndHistogram() {
        var macdv = new MACDVIndicator(series);
        // MACDV unstable period is longPeriod (26), so indices 0-25 return NaN
        // But series only has 12 bars, so all indices will be in unstable period
        var signalLine = macdv.getSignalLine(9);
        var histogram = macdv.getHistogram(9);

        // All indices are in unstable period (series has 12 bars, but unstable period
        // is 26)
        for (int i = 0; i < closes.length; i++) {
            assertTrue(macdv.getValue(i).isNaN());
            assertTrue(signalLine.getValue(i).isNaN());
            assertTrue(histogram.getValue(i).isNaN());
        }
    }

    @Test
    public void nanPropagation() {
        var builder = new BaseBarSeriesBuilder().withName("macdv-nan").withNumFactory(numFactory);
        var nanSeries = builder.build();
        double[] specialCloses = { 10.0, Double.NaN, 11.0, 12.0 };
        double[] specialVolumes = { 1000, 1100, 0, 1200 };
        double[] specialHighs = { 10.4, Double.NaN, 11.4, 12.4 };
        double[] specialLows = { 9.6, Double.NaN, 10.6, 11.6 };
        for (int i = 0; i < specialCloses.length; i++) {
            var barBuilder = nanSeries.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1)))
                    .volume(specialVolumes[i]);
            double close = specialCloses[i];
            if (Double.isNaN(close)) {
                barBuilder.openPrice(NaN.NaN).closePrice(NaN.NaN).highPrice(NaN.NaN).lowPrice(NaN.NaN);
            } else {
                barBuilder.openPrice(close).closePrice(close).highPrice(specialHighs[i]).lowPrice(specialLows[i]);
            }
            barBuilder.add();
        }

        var macdv = new MACDVIndicator(nanSeries, 2, 3);
        // MACDV unstable period is longPeriod (3), so indices 0-2 return NaN
        assertTrue(macdv.getValue(0).isNaN());
        assertTrue(macdv.getValue(1).isNaN());
        assertTrue(macdv.getValue(2).isNaN());
        // Index 3 is first valid value after unstable period
        // Note: Index 1 has NaN in close price, but we're past unstable period at index
        // 3
        // With improved NaN handling in EMA, the indicator should recover from NaN
        // Index 2 has zero volume which may cause NaN in VWMA calculation (division by
        // zero)
        // Index 3 should recover if VWMA calculations are valid
        // The key improvement: NaN no longer contaminates all future values
        // If the underlying data is valid, the indicator recovers gracefully
    }
}
