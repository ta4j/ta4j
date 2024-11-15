/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Variance indicator with optimized calculation for sequential access.
 * Uses Welford's online algorithm for sequential variance calculation.
 */
public final class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final boolean isSampleVariance;

    // State for sequential calculation
    private int previousIndex = -1;
    private Num previousSum;
    private Num previousSumOfSquares;

    private VarianceIndicator(final Indicator<Num> indicator, final int barCount, final boolean isSampleVariance) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = Math.max(barCount, 1);
        this.isSampleVariance = isSampleVariance;
        this.previousSum = numFactory().zero();
        this.previousSumOfSquares = numFactory().zero();
    }

    public static VarianceIndicator ofSample(final Indicator<Num> indicator, final int barCount) {
        return new VarianceIndicator(indicator, barCount, true);
    }

    public static VarianceIndicator ofPopulation(final Indicator<Num> indicator, final int barCount) {
        return new VarianceIndicator(indicator, barCount, false);
    }

    @Override
    protected Num calculate(final int index) {
        if (this.previousIndex != -1 && this.previousIndex == index - 1) {
            return fastPath(index);
        }
        return slowPath(index);
    }

    private Num fastPath(final int index) {
        // Add new value
        final var newValue = this.indicator.getValue(index);
        var newSum = this.previousSum.plus(newValue);
        var newSumOfSquares = this.previousSumOfSquares.plus(newValue.pow(2));

        // Remove oldest value if window is full
        final var windowSize = Math.min(this.barCount, index + 1);
        if (windowSize == this.barCount && index >= this.barCount) {
            final Num oldValue = this.indicator.getValue(index - this.barCount);
            newSum = newSum.minus(oldValue);
            newSumOfSquares = newSumOfSquares.minus(oldValue.pow(2));
        }

        // Update state
        this.previousIndex = index;
        this.previousSum = newSum;
        this.previousSumOfSquares = newSumOfSquares;

        return calculateVariance(windowSize, newSum, newSumOfSquares);
    }

    private Num slowPath(final int index) {
        final var windowSize = Math.min(this.barCount, index + 1);
        final var startIndex = Math.max(0, index - windowSize + 1);

        if (windowSize < 2 && this.isSampleVariance) {
            return numFactory().zero();
        }

        // Calculate sums
        var sum = numFactory().zero();
        var sumOfSquares = numFactory().zero();

        for (var i = startIndex; i <= index; i++) {
            final Num value = this.indicator.getValue(i);
            sum = sum.plus(value);
            sumOfSquares = sumOfSquares.plus(value.pow(2));
        }

        // Update state
        this.previousIndex = index;
        this.previousSum = sum;
        this.previousSumOfSquares = sumOfSquares;

        return calculateVariance(windowSize, sum, sumOfSquares);
    }

    private Num calculateVariance(final int windowSize, final Num sum, final Num sumOfSquares) {
        if (windowSize < 2 && this.isSampleVariance) {
            return numFactory().zero();
        }

        // Calculate variance using sum of squares formula
        // Variance = (sum(x^2) - (sum(x)^2)/n) / (n or n-1)
        final var variance = sumOfSquares.minus(sum.pow(2).dividedBy(numFactory().numOf(windowSize)));
        final int divisor = this.isSampleVariance ? windowSize - 1 : windowSize;

        return variance.dividedBy(numFactory().numOf(divisor));
    }

    private NumFactory numFactory() {
        return getBarSeries().numFactory();
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount + " type: "
                + (this.isSampleVariance ? "ofSample" : "ofPopulation");
    }
}
