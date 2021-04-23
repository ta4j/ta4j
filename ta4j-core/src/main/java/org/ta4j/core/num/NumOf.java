/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.num;

public final class NumOf {

    private NumOf() {
    }

    /**
     * @param anyNum any Num to determine the Num type
     * @return 0 with type of anyNum
     */
    public static Num ZERO(Num anyNum) {
        if (isDecimalNum(anyNum)) {
            return DecimalNum.ZERO;
        } else if (isDoubleNum(anyNum)) {
            return DoubleNum.ZERO;
        }
        return NaN.NaN;
    }

    /**
     * @param anyNum any Num to determine the Num type
     * @return 1 with type of anyNum
     */
    public static Num ONE(Num anyNum) {
        if (isDecimalNum(anyNum)) {
            return DecimalNum.ONE;
        } else if (isDoubleNum(anyNum)) {
            return DoubleNum.ONE;
        }
        return NaN.NaN;
    }

    /**
     * @param anyNum any Num to determine the Num type
     * @return 100 with type of anyNum
     */
    public static Num HUNDRED(Num anyNum) {
        if (isDecimalNum(anyNum)) {
            return DecimalNum.HUNDRED;
        } else if (isDoubleNum(anyNum)) {
            return DoubleNum.HUNDRED;
        }
        return NaN.NaN;
    }

    /**
     * @param anyNum
     * @return true if anyNum is of type DecimalNum
     */
    private static boolean isDecimalNum(Num anyNum) {
        return anyNum.getClass() == DecimalNum.class;
    }

    /**
     * @param anyNum
     * @return true if anyNum is of type DoubleNum
     */
    private static boolean isDoubleNum(Num anyNum) {
        return anyNum.getClass() == DoubleNum.class;
    }

}
