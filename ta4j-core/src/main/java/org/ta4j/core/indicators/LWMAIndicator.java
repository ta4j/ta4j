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
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Linearly Weighted Moving Average (LWMA) indicator.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp">
 *      https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp</a>
 */
public class LWMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num zero = zero();

    /**
     * Constructor.
     * 
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public LWMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = zero;
        Num denominator = zero;
        int count = 0;

        if ((index + 1) < barCount) {
            return zero;
        }

        int startIndex = (index - barCount) + 1;
        for (int i = startIndex; i <= index; i++) {
            count++;
            denominator = denominator.plus(numOf(count));
            sum = sum.plus(indicator.getValue(i).multipliedBy(numOf(count)));
        }
        return sum.dividedBy(denominator);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
