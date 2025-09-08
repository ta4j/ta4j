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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change (ROCIndicator) indicator (also called "Momentum").
 *
 * <p>
 * The ROCIndicator calculation compares the current {@link #indicator}-value
 * with the {@link #indicator}-value "n" periods ago. If a
 * {@link #previousIndicator} is given, it compares the current
 * {@link #indicator}-value with the {@link #previousIndicator}-value "n"
 * periods ago.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/pricerateofchange.asp">https://www.investopedia.com/terms/p/pricerateofchange.asp</a>
 */
public class ROCIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Indicator<Num> previousIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public ROCIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, null, barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator         the indicator
     * @param previousIndicator the previous indicator (optional)
     * @param barCount          the time frame
     */
    public ROCIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.previousIndicator = previousIndicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = previousIndicator == null ? indicator.getValue(nIndex)
                : previousIndicator.getValue(nIndex);
        Num currentValue = indicator.getValue(index);
        return currentValue.minus(nPeriodsAgoValue)
                .dividedBy(nPeriodsAgoValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
