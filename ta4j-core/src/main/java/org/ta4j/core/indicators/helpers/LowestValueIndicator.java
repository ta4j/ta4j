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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.function.Predicate;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Lowest value indicator.
 *
 * <p>
 * Returns the lowest indicator value from the bar series within the bar count.
 */
public class LowestValueIndicator extends AbstractIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;

    /** circular array */
    private final ArrayList<Num> data;
    private Num value;
    private int barsPassed;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public LowestValueIndicator(final Indicator<Num> indicator, final int barCount) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCount;
        this.data = new ArrayList<>(barCount);
        for (int i = 0; i < barCount; i++) {
            this.data.add(NaN.NaN);
        }
    }

    protected Num calculate() {
        final var indicatorValue = this.indicator.getValue();
        this.data.set(this.barsPassed % this.barCount, indicatorValue);
        return this.data.stream().filter(Predicate.not(Num::isNaN)).min(Num::compareTo).orElse(NaN.NaN);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + this.indicator;
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        ++this.barsPassed;
        this.value = calculate();
    }

    @Override
    public boolean isStable() {
        return this.barsPassed >= this.barCount && this.indicator.isStable();
    }
}
