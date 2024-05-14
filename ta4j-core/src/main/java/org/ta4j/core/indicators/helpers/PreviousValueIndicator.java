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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Returns the (n-th) previous value of an indicator.
 *
 * If the (n-th) previous index is below the first index from the bar series,
 * then {@link NaN#NaN} is returned.
 */
public class PreviousValueIndicator extends AbstractIndicator<Num> {

    private final int n;
    private final Indicator<Num> indicator;
    private final LinkedList<Num> previousValues = new LinkedList<>();
    private Num value;
    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     */
    public PreviousValueIndicator(final Indicator<Num> indicator) {
        this(indicator, 1);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     * @param n         parameter defines the previous n-th value
     */
    public PreviousValueIndicator(final Indicator<Num> indicator, final int n) {
        super(indicator.getBarSeries());
        if (n < 1) {
            throw new IllegalArgumentException("n must be positive number, but was: " + n);
        }
        this.n = n;
        this.indicator = indicator;
    }

    protected Num calculate() {
        final var currentValue = this.indicator.getValue();
        this.previousValues.addLast(currentValue);

        if (this.previousValues.size() > this.n) {
            return this.previousValues.removeFirst();
        }

        return getBarSeries().numFactory().zero();
    }

    @Override
    public String toString() {
        final String nInfo = this.n == 1 ? "" : "(" + this.n + ")";
        return getClass().getSimpleName() + nInfo + "[" + this.indicator + "]";
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick) || tick.isBefore(this.currentTick)) {
            this.indicator.refresh(tick);
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.previousValues.size() == this.n;
    }
}
