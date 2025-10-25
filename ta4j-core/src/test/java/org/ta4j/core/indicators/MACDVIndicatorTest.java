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
        double[] expected = computeMacdv(closes, highs, lows, volumes, 5, 10);

        for (int i = 0; i < expected.length; i++) {
            assertNumEquals(expected[i], macdv.getValue(i));
        }

        var shortVwema = macdv.getShortTermVolumeWeightedEma();
        var longVwema = macdv.getLongTermVolumeWeightedEma();
        double[] shortExpected = computeVolumeAtrWeightedEma(closes, highs, lows, volumes, 5);
        double[] longExpected = computeVolumeAtrWeightedEma(closes, highs, lows, volumes, 10);

        assertNumEquals(shortExpected[5], shortVwema.getValue(5));
        assertNumEquals(longExpected[5], longVwema.getValue(5));
        assertNumEquals(shortExpected[10], shortVwema.getValue(10));
        assertNumEquals(longExpected[10], longVwema.getValue(10));
    }

    @Test
    public void signalLineAndHistogram() {
        var macdv = new MACDVIndicator(series);
        double[] macdvValues = computeMacdv(closes, highs, lows, volumes, 12, 26);
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
        assertFalse(macdv.getValue(0).isNaN());
        assertTrue(macdv.getValue(1).isNaN());
        assertTrue(macdv.getValue(2).isNaN());
        assertTrue(macdv.getValue(3).isNaN());
    }

    private static double[] computeMacdv(double[] price, double[] high, double[] low, double[] volume, int shortPeriod,
            int longPeriod) {
        double[] shortVwema = computeVolumeAtrWeightedEma(price, high, low, volume, shortPeriod);
        double[] longVwema = computeVolumeAtrWeightedEma(price, high, low, volume, longPeriod);
        double[] macdv = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double shortVal = shortVwema[i];
            double longVal = longVwema[i];
            macdv[i] = Double.isNaN(shortVal) || Double.isNaN(longVal) ? Double.NaN : shortVal - longVal;
        }
        return macdv;
    }

    private static double[] computeVolumeAtrWeightedEma(double[] price, double[] high, double[] low, double[] volume,
            int period) {
        double[] atr = computeAtr(high, low, price, period);
        double[] weights = new double[price.length];
        double[] weightedPrice = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double p = price[i];
            double v = volume[i];
            double a = atr[i];
            if (Double.isNaN(p) || Double.isNaN(v) || Double.isNaN(a) || a == 0.0) {
                weights[i] = Double.NaN;
                weightedPrice[i] = Double.NaN;
            } else {
                double w = v / a;
                weights[i] = w;
                weightedPrice[i] = p * w;
            }
        }

        double[] priceVolumeEma = ema(weightedPrice, period);
        double[] weightEma = ema(weights, period);
        double[] vwema = new double[price.length];
        for (int i = 0; i < price.length; i++) {
            double numerator = priceVolumeEma[i];
            double denominator = weightEma[i];
            if (Double.isNaN(numerator) || Double.isNaN(denominator) || denominator == 0.0) {
                vwema[i] = Double.NaN;
            } else {
                vwema[i] = numerator / denominator;
            }
        }
        return vwema;
    }

    private static double[] computeAtr(double[] high, double[] low, double[] close, int period) {
        double[] trueRange = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            double h = high[i];
            double l = low[i];
            if (Double.isNaN(h) || Double.isNaN(l)) {
                trueRange[i] = Double.NaN;
                continue;
            }
            double hl = Math.abs(h - l);
            if (i == 0) {
                trueRange[i] = hl;
                continue;
            }
            double prevClose = close[i - 1];
            if (Double.isNaN(prevClose)) {
                trueRange[i] = Double.NaN;
                continue;
            }
            double hc = Math.abs(h - prevClose);
            double lc = Math.abs(prevClose - l);
            trueRange[i] = Math.max(hl, Math.max(hc, lc));
        }
        return mma(trueRange, period);
    }

    private static double[] mma(double[] values, int period) {
        double multiplier = 1.0 / period;
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
                result[i] = prev + (current - prev) * multiplier;
            }
        }
        return result;
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
