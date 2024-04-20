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
package org.ta4j.core.indicators.average;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.Num;

/**
 * Base class for Exponential Moving Average implementations.
 */
public abstract class AbstractEMAIndicator extends AbstractIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num multiplier;

    private Num previousValue;
    private Num currentValue;
    private int barsPassed;

    // LocalDateTime may be better fit
    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());


    /**
     * Constructor.
     *
     * @param indicator  the {@link Indicator}
     * @param barCount   the time frame
     * @param multiplier the multiplier
     */
    protected AbstractEMAIndicator(final Indicator<Num> indicator, final int barCount, final double multiplier) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCount;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
    }

    @Override
    public Num getValue() {
        return this.currentValue;
    }

    protected Num calculate() {
        if (this.previousValue == null) {
            this.previousValue = this.indicator.getValue();
            return this.previousValue;
        }

        final var newValue = this.indicator.getValue()
                .minus(this.previousValue)
                .multipliedBy(this.multiplier)
                .plus(this.previousValue);
        this.previousValue = newValue;
        return newValue;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }

    public int getBarCount() {
        return this.barCount;
    }

    @Override
    public boolean isStable() {
        return this.barsPassed > this.barCount && this.indicator.isStable();
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick)) {
            ++this.barsPassed;
            this.indicator.refresh(tick);
            this.currentValue = calculate();
            this.currentTick = tick;
        } else if (tick.isBefore(this.currentTick)) {
            this.barsPassed = 1;
            this.indicator.refresh(tick);
            this.currentValue = calculate();
            this.currentTick = tick;
        }
    }
}
