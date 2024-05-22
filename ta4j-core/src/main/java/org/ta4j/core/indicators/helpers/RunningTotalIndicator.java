/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.helpers;

import java.time.Instant;

import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Running Total aka Cumulative Sum indicator
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Running_total">https://en.wikipedia.org/wiki/Running_total</a>
 */
public class RunningTotalIndicator extends NumericIndicator {
    private final Indicator<Num> indicator;
    private final int barCount;
    private final PreviousValueIndicator previousValue;
    private Num previousSum;
    private Num value;
    private int processedBars;
    private Instant currentTick = Instant.EPOCH;

    public RunningTotalIndicator(final Indicator<Num> indicator, final int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.previousSum = indicator.getBarSeries().numFactory().zero();
        this.previousValue = new PreviousValueIndicator(indicator, barCount);
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    protected Num calculate() {
        final var newSum = partialSum();
        return newSum;
    }

    private Num partialSum() {
        final var indicatorValue = this.indicator.getValue();

        var sum = this.previousSum.plus(indicatorValue);

        if (this.previousValue.isStable()) {
            sum = sum.minus(this.previousValue.getValue());
        }

        this.previousSum = sum;
        return sum;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }

    @Override
    public void refresh(final Instant tick) {
        if (tick.isAfter(this.currentTick)) {
            ++this.processedBars;
            this.previousValue.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        } else if (tick.isBefore(this.currentTick)) {
            this.processedBars = 1;
            this.previousValue.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.processedBars >= this.barCount && this.indicator.isStable();
    }
}
