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
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SqueezeProIndicator.SqueezeLevel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SqueezeProIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int BAR_COUNT = 20;
    private static final double BB_MULTIPLIER = 2.0;
    private static final double KC_HIGH = 1.0;
    private static final double KC_MID = 1.5;
    private static final double KC_LOW = 2.0;

    public SqueezeProIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesLazyBearMomentumAndSqueezeLevels() {
        BarSeries series = buildReferenceSeries();
        var indicator = new SqueezeProIndicator(series, BAR_COUNT, BB_MULTIPLIER, KC_HIGH, KC_MID, KC_LOW);

        ReferenceValues reference = computeReference(series);

        for (int i = 0; i < BAR_COUNT; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
            assertEquals(SqueezeLevel.NONE, indicator.getSqueezeLevel(i));
        }

        for (int i = BAR_COUNT; i < series.getBarCount(); i++) {
            Num expected = reference.momentumValues().get(i);
            Num actual = indicator.getValue(i);
            assertThat(actual.minus(expected).abs().doubleValue()).isLessThan(1e-9);
            assertEquals("Unexpected squeeze level at index " + i, reference.squeezeLevels().get(i),
                    indicator.getSqueezeLevel(i));
        }
    }

    @Test
    public void isInSqueezeReflectsCompressionLevels() {
        BarSeries series = buildReferenceSeries();
        var indicator = new SqueezeProIndicator(series, BAR_COUNT, BB_MULTIPLIER, KC_HIGH, KC_MID, KC_LOW);

        assertThat(indicator.isInSqueeze(BAR_COUNT)).isTrue(); // high squeeze
        assertThat(indicator.getSqueezeLevel(BAR_COUNT + 11)).isEqualTo(SqueezeLevel.MID);
        assertThat(indicator.isInSqueeze(BAR_COUNT + 12)).isTrue();
        assertThat(indicator.getSqueezeLevel(BAR_COUNT + 12)).isEqualTo(SqueezeLevel.LOW);
        assertThat(indicator.isInSqueeze(BAR_COUNT + 13)).isFalse();
    }

    private BarSeries buildReferenceSeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < BAR_COUNT; i++) {
            series.barBuilder().openPrice(100).highPrice(100.2).lowPrice(99.8).closePrice(100).add();
        }

        double[] alternating = new double[] { 100.5, 99.5, 100.5, 99.5, 100.5, 99.5, 100.5, 99.5, 100.5, 99.5 };
        for (double close : alternating) {
            series.barBuilder().openPrice(close).highPrice(close + 0.2).lowPrice(close - 0.2).closePrice(close).add();
        }

        for (int i = 0; i < 10; i++) {
            double close = 100.5 + i * 1.2;
            series.barBuilder().openPrice(close).highPrice(close + 0.2).lowPrice(close - 0.2).closePrice(close).add();
        }

        return series;
    }

    private ReferenceValues computeReference(BarSeries series) {
        int size = series.getBarCount();
        List<Num> closes = new ArrayList<>(size);
        List<Num> trueRanges = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Bar bar = series.getBar(i);
            Num high = bar.getHighPrice();
            Num low = bar.getLowPrice();
            Num close = bar.getClosePrice();
            closes.add(close);
            if (i == 0) {
                trueRanges.add(high.minus(low).abs());
            } else {
                Num prevClose = series.getBar(i - 1).getClosePrice();
                Num hl = high.minus(low).abs();
                Num hc = high.minus(prevClose).abs();
                Num lc = prevClose.minus(low).abs();
                trueRanges.add(hl.max(hc).max(lc));
            }
        }

        List<Num> closeSma = rollingAverage(closes);
        List<Num> closeStdDev = rollingStandardDeviation(closes, closeSma);
        List<Num> trSma = rollingAverage(trueRanges);

        List<Num> detrended = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            detrended.add(closes.get(i).minus(closeSma.get(i)));
        }

        List<Num> momentumValues = new ArrayList<>(size);
        List<SqueezeLevel> squeezeLevels = new ArrayList<>(size);
        Num bbMultiplier = numFactory.numOf(BB_MULTIPLIER);
        Num kcHigh = numFactory.numOf(KC_HIGH);
        Num kcMid = numFactory.numOf(KC_MID);
        Num kcLow = numFactory.numOf(KC_LOW);

        for (int i = 0; i < size; i++) {
            if (i < BAR_COUNT) {
                squeezeLevels.add(SqueezeLevel.NONE);
                momentumValues.add(NaN);
                continue;
            }

            Num basis = closeSma.get(i);
            Num bbUpper = basis.plus(closeStdDev.get(i).multipliedBy(bbMultiplier));
            Num bbLower = basis.minus(closeStdDev.get(i).multipliedBy(bbMultiplier));

            squeezeLevels.add(determineLevel(bbLower, bbUpper, basis, trSma.get(i), kcHigh, kcMid, kcLow));
            momentumValues.add(linearRegression(detrended, i));
        }

        return new ReferenceValues(momentumValues, squeezeLevels);
    }

    private List<Num> rollingAverage(List<Num> values) {
        List<Num> averages = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num sum = numFactory.zero();
            for (int j = start; j <= i; j++) {
                sum = sum.plus(values.get(j));
            }
            averages.add(sum.dividedBy(numFactory.numOf(i - start + 1)));
        }
        return averages;
    }

    private List<Num> rollingStandardDeviation(List<Num> values, List<Num> averages) {
        List<Num> stdDeviations = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num mean = averages.get(i);
            Num variance = numFactory.zero();
            for (int j = start; j <= i; j++) {
                Num delta = values.get(j).minus(mean);
                variance = variance.plus(delta.pow(2));
            }
            Num samples = numFactory.numOf(i - start + 1);
            stdDeviations.add(variance.dividedBy(samples).sqrt());
        }
        return stdDeviations;
    }

    private Num linearRegression(List<Num> values, int index) {
        int start = Math.max(0, index - BAR_COUNT + 1);
        int observations = index - start + 1;
        if (observations < 2) {
            return NaN;
        }
        Num sumX = numFactory.zero();
        Num sumY = numFactory.zero();
        for (int i = start; i <= index; i++) {
            sumX = sumX.plus(numFactory.numOf(i));
            sumY = sumY.plus(values.get(i));
        }

        Num n = numFactory.numOf(observations);
        Num meanX = sumX.dividedBy(n);
        Num meanY = sumY.dividedBy(n);

        Num xx = numFactory.zero();
        Num xy = numFactory.zero();
        for (int i = start; i <= index; i++) {
            Num x = numFactory.numOf(i);
            Num xDiff = x.minus(meanX);
            Num yDiff = values.get(i).minus(meanY);
            xx = xx.plus(xDiff.pow(2));
            xy = xy.plus(xDiff.multipliedBy(yDiff));
        }
        Num slope = xy.dividedBy(xx);
        Num intercept = meanY.minus(slope.multipliedBy(meanX));
        return slope.multipliedBy(numFactory.numOf(index)).plus(intercept);
    }

    private SqueezeLevel determineLevel(Num bbLower, Num bbUpper, Num basis, Num trAverage, Num kcHigh, Num kcMid,
            Num kcLow) {
        Num upperHigh = basis.plus(trAverage.multipliedBy(kcHigh));
        Num lowerHigh = basis.minus(trAverage.multipliedBy(kcHigh));
        if (bbLower.isGreaterThan(lowerHigh) && bbUpper.isLessThan(upperHigh)) {
            return SqueezeLevel.HIGH;
        }

        Num upperMid = basis.plus(trAverage.multipliedBy(kcMid));
        Num lowerMid = basis.minus(trAverage.multipliedBy(kcMid));
        if (bbLower.isGreaterThan(lowerMid) && bbUpper.isLessThan(upperMid)) {
            return SqueezeLevel.MID;
        }

        Num upperLow = basis.plus(trAverage.multipliedBy(kcLow));
        Num lowerLow = basis.minus(trAverage.multipliedBy(kcLow));
        if (bbLower.isGreaterThan(lowerLow) && bbUpper.isLessThan(upperLow)) {
            return SqueezeLevel.LOW;
        }
        return SqueezeLevel.NONE;
    }

    private static final class ReferenceValues {
        private final List<Num> momentumValues;
        private final List<SqueezeLevel> squeezeLevels;

        private ReferenceValues(List<Num> momentumValues, List<SqueezeLevel> squeezeLevels) {
            this.momentumValues = momentumValues;
            this.squeezeLevels = squeezeLevels;
        }

        List<Num> momentumValues() {
            return momentumValues;
        }

        List<SqueezeLevel> squeezeLevels() {
            return squeezeLevels;
        }
    }
}
