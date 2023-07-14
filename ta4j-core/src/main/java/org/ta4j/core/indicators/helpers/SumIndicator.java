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
 * Sum indicator.
 *
 * I.e.: operand0 + operand1 + ... + operandN
 */
public class SumIndicator extends CachedIndicator<Num> {

    private final Indicator<Num>[] operands;

    /**
     * Constructor. (operand0 plus operand1 plus ... plus operandN)
     *
     * @param operands the operand indicators for the sum
     */
    @SafeVarargs
    public SumIndicator(Indicator<Num>... operands) {
        // TODO: check if first series is equal to the other ones
        super(operands[0]);
        this.operands = operands;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = numOf(0);
        for (Indicator<Num> operand : operands) {
            sum = sum.plus(operand.getValue(index));
        }
        return sum;
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
