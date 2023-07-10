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
 * Ulcer index indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index</a>
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Ulcer_index">https://en.wikipedia.org/wiki/Ulcer_index</a>
 */
public class UlcerIndexIndicator extends CachedIndicator<Num> {

    private final Num hundred;
    private final Num zero;

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public UlcerIndexIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.zero = zero();
        this.hundred = hundred();
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num squaredAverage = zero;
        Num highestValue = indicator.getValue(startIndex);
        for (int i = startIndex; i <= index; i++) {
            Num currentValue = indicator.getValue(i);
            if (currentValue.isGreaterThan(highestValue)) {
                highestValue = currentValue;
            }
            Num percentageDrawdown = currentValue.minus(highestValue).dividedBy(highestValue).multipliedBy(hundred);
            squaredAverage = squaredAverage.plus(percentageDrawdown.pow(2));
        }
        squaredAverage = squaredAverage.dividedBy(numOf(numberOfObservations));
        return squaredAverage.sqrt();
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
