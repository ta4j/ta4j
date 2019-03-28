/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.indicators;


import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;


/**
 * Linearly Weighted Moving Average (LWMA).
 *
 * @see <a href="https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp">
 *     https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp</a>
 */
public class LWMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    private final int barCount;

    public LWMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = numOf(0);
        Num denominator = numOf(0);
        int count = 0;

        if ((index + 1) < barCount) {
            return numOf(0);
        }

        int startIndex = Math.max(0, index - barCount + 1);
        for (int i = startIndex; i <= index; i++) {
            count++;
            denominator = denominator.plus(numOf(count));
            sum = sum.plus(indicator.getValue(i).multipliedBy(numOf((count) * 1)));
        }
        return sum.dividedBy(denominator);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
