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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

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
                    .highPrice(closes[i])
                    .lowPrice(closes[i])
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
        double[] expected = computeMacdv(closes, volumes, 5, 10);

        for (int i = 0; i < expected.length; i++) {
            assertNumEquals(expected[i], macdv.getValue(i));
        }

        var shortVwema = macdv.getShortTermVolumeWeightedEma();
        var longVwema = macdv.getLongTermVolumeWeightedEma();
        double[] shortExpected = computeVolumeWeightedEma(closes, volumes, 5);
        double[] longExpected = computeVolumeWeightedEma(closes, volumes, 10);

        assertNumEquals(shortExpected[5], shortVwema.getValue(5));
        assertNumEquals(longExpected[5], longVwema.getValue(5));
        assertNumEquals(shortExpected[10], shortVwema.getValue(10));
        assertNumEquals(longExpected[10], longVwema.getValue(10));
    }

    @Test
    public void signalLineAndHistogram() {
        var macdv = new MACDVIndicator(series);
        double[] macdvValues = computeMacdv(closes, volumes, 12, 26);
        double[] signal = ema(macdvValues, 9);

        var signalLine = macdv.getSignalLine(9);
        var histogram = macdv.getHistogram(9);

        for (int i = 0; i < macdvValues.length; i++) {
            assertNumEquals(signal[i], signalLine.getValue(i));
            if (Double.isNaN(macdvValues[i]) || Double.isNaN(signal[i])) {
                assertTrue(histogram.getValue(i).isNaN());
            } else {
                assertNumEquals(macdvValues[i] - signal[i], histogram.getValue(i));
            }
        }
    }

    @Test
    public void nanPropagation() {
        var builder = new BaseBarSeriesBuilder().withName("macdv-nan").withNumFactory(numFactory);
        var nanSeries = builder.build();
        double[] specialCloses = { 10.0, Double.NaN, 11.0, 12.0 };
        double[] specialVolumes = { 1000, 1100, 0, 1200 };
        for (int i = 0; i < specialCloses.length; i++) {
            var barBuilder = nanSeries.barBuilder()
                    .timePeriod(PERIOD)
                    .endTime(START.plus(PERIOD.multipliedBy(i + 1)))
                    .volume(specialVolumes[i]);
            double close = specialCloses[i];
            if (Double.isNaN(close)) {
                barBuilder.openPrice(NaN.NaN).closePrice(NaN.NaN).highPrice(NaN.NaN).lowPrice(NaN.NaN);
            } else {
                barBuilder.openPrice(close).closePrice(close).highPrice(close).lowPrice(close);
            }
            barBuilder.add();
        }

        var macdv = new MACDVIndicator(nanSeries, 2, 3);
        assertFalse(macdv.getValue(0).isNaN());
        assertTrue(macdv.getValue(1).isNaN());
        assertTrue(macdv.getValue(2).isNaN());
        assertTrue(macdv.getValue(3).isNaN());
    }

    private static double[] computeMacdv(double[] price, double[] volume, int shortPeriod, int longPeriod) {
        double[] shortVwema = computeVolumeWeightedEma(price, volume, shortPeriod);
        double[] longVwema = computeVolumeWeightedEma(price, volume, longPeriod);
        double[] macdv = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double shortVal = shortVwema[i];
            double longVal = longVwema[i];
            macdv[i] = Double.isNaN(shortVal) || Double.isNaN(longVal) ? Double.NaN : shortVal - longVal;
        }
        return macdv;
    }

    private static double[] computeVolumeWeightedEma(double[] price, double[] volume, int period) {
        double[] priceVolume = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double p = price[i];
            double v = volume[i];
            priceVolume[i] = Double.isNaN(p) || Double.isNaN(v) ? Double.NaN : p * v;
        }

        double[] priceVolumeEma = ema(priceVolume, period);
        double[] volumeEma = ema(volume, period);
        double[] vwema = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double numerator = priceVolumeEma[i];
            double denominator = volumeEma[i];
            if (Double.isNaN(numerator) || Double.isNaN(denominator) || denominator == 0.0) {
                vwema[i] = Double.NaN;
            } else {
                vwema[i] = numerator / denominator;
            }
        }
        return vwema;
    }

    private static double[] ema(double[] values, int period) {
        double multiplier = 2.0 / (period + 1.0);
        double[] result = new double[values.length];
        if (values.length == 0) {
            return result;
        }
        result[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            double prev = result[i - 1];
            double current = values[i];
            if (Double.isNaN(prev) || Double.isNaN(current)) {
                result[i] = Double.NaN;
            } else {
                result[i] = (current - prev) * multiplier + prev;
            }
        }
        return result;
    }
}
