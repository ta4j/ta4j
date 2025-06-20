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
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Variance indicator with optimized calculation for sequential access. Uses
 * Welford's online algorithm for sequential variance calculation.
 */
public class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Type type;

    // State for Welford's algorithm
    private int previousIndex = -1;
    private PreviousValueIndicator previousValueIndicator;
    private Num runningMean;
    private Num runningM2;
    private int currentCount;

    public VarianceIndicator(final Indicator<Num> indicator, final int barCount, final Type type) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = Math.max(barCount, 1);
        this.type = Objects.requireNonNull(type);
        this.previousValueIndicator = new PreviousValueIndicator(this.indicator, this.barCount);
        this.runningMean = numFactory().zero();
        this.runningM2 = numFactory().zero();
        this.currentCount = 0;
    }

    public static VarianceIndicator ofSample(final Indicator<Num> indicator, final int barCount) {
        return new VarianceIndicator(indicator, barCount, Type.SAMPLE);
    }

    public static VarianceIndicator ofPopulation(final Indicator<Num> indicator, final int barCount) {
        return new VarianceIndicator(indicator, barCount, Type.POPULATION);
    }

    @Override
    protected Num calculate(final int index) {
        if (this.previousIndex != -1 && this.previousIndex == index - 1) {
            return fastPath(index);
        }
        return slowPath(index);
    }

    private Num fastPath(final int index) {
        final var newValue = this.indicator.getValue(index);
        final var windowSize = Math.min(this.barCount, index + 1);

        if (windowSize == this.barCount && index >= this.barCount) {
            // Remove old value using Welford's algorithm
            final var oldValue = this.previousValueIndicator.getValue(index);
            if (!oldValue.isNaN()) {
                final var oldDelta = oldValue.minus(this.runningMean);
                this.runningMean = this.runningMean.minus(oldDelta.dividedBy(numFactory().numOf(windowSize - 1)));
                final var newDelta = oldValue.minus(this.runningMean);
                this.runningM2 = this.runningM2.minus(oldDelta.multipliedBy(newDelta));
            }
        } else {
            this.currentCount++;
        }

        // Add new value using Welford's algorithm
        final var delta = newValue.minus(this.runningMean);
        this.runningMean = this.runningMean.plus(delta.dividedBy(numFactory().numOf(windowSize)));
        final var delta2 = newValue.minus(this.runningMean);
        this.runningM2 = this.runningM2.plus(delta.multipliedBy(delta2));

        this.previousIndex = index;

        return calculateWelfordVariance(windowSize);
    }

    private Num slowPath(final int index) {
        final var windowSize = Math.min(this.barCount, index + 1);
        final var startIndex = Math.max(0, index - windowSize + 1);

        if (windowSize < 2 && isSample()) {
            return numFactory().zero();
        }

        // Reset Welford state and recalculate from scratch
        this.runningMean = numFactory().zero();
        this.runningM2 = numFactory().zero();
        this.currentCount = 0;

        // Apply Welford's algorithm to current window
        for (var i = startIndex; i <= index; i++) {
            final Num value = this.indicator.getValue(i);
            this.currentCount++;

            final var delta = value.minus(this.runningMean);
            this.runningMean = this.runningMean.plus(delta.dividedBy(numFactory().numOf(this.currentCount)));
            final var delta2 = value.minus(this.runningMean);
            this.runningM2 = this.runningM2.plus(delta.multipliedBy(delta2));
        }

        this.previousIndex = index;

        return calculateWelfordVariance(windowSize);
    }

    private Num calculateWelfordVariance(final int windowSize) {
        if (windowSize < 2 && isSample()) {
            return numFactory().zero();
        }

        // Welford's algorithm: variance = M2 / (n-1) for sample, M2 / n for population
        final int divisor = getDivisor(windowSize);

        if (divisor <= 0) {
            return numFactory().zero();
        }

        return this.runningM2.dividedBy(numFactory().numOf(divisor));
    }

    private boolean isSample() {
        return this.type.isSample();
    }

    private int getDivisor(final int windowSize) {
        return switch (this.type) {
        case SAMPLE -> windowSize - 1;
        case POPULATION -> windowSize;
        };
    }

    private NumFactory numFactory() {
        return getBarSeries().numFactory();
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount + " type: " + this.type;
    }

}
