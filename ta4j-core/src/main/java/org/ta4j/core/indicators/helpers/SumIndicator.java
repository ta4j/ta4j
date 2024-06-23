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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Calculates the sum of all indicator values.
 * 
 * <pre>
 * Sum = summand0 + summand1 + ... + summandN
 * </pre>
 */
public class SumIndicator extends CachedIndicator<Num> {

    private final Indicator<Num>[] summands;

    /**
     * Constructor.
     *
     * @param summands the indicators ​​to be summed
     */
    @SafeVarargs
    public SumIndicator(Indicator<Num>... summands) {
        // TODO: check if first series is equal to the other ones
        super(summands[0]);
        this.summands = summands;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = zero();
        for (Indicator<Num> summand : summands) {
            sum = sum.plus(summand.getValue(index));
        }
        return sum;
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
