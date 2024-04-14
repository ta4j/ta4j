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

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple moving average (SMA) indicator.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/sma.asp">https://www.investopedia.com/terms/s/sma.asp</a>
 */
public class SMAIndicator extends AbstractIndicator<Num> {

    private final int barCount;
    private final RunningTotalIndicator sum;
    private Num value;
    private int processedBars;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public SMAIndicator(final Indicator<Num> indicator, final int barCount) {
        super(indicator.getBarSeries());
        this.sum = new RunningTotalIndicator(indicator, barCount);
        this.barCount = barCount;
    }

    protected Num calculate() {
        final var sum = partialSum();
        return sum.dividedBy(getBarSeries().numFactory().numOf(this.barCount));
    }

    private Num partialSum() {
        return this.sum.getValue();
    }

    @Override
    public Num getValue() {
        if (this.value == null) {
            throw new IllegalStateException("Indicator has not been initialized");
        }

        return this.value;
    }

    @Override
    public boolean isStable() {
        return this.processedBars >= this.barCount;
    }

    @Override
    public void refresh() {
        this.processedBars++;
        this.sum.refresh();
        this.value = calculate();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }

}
