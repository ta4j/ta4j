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
package org.ta4j.core.utils;

import org.ta4j.core.num.Num;

/**
 * Common formulas for Math and Finance.
 */
public final class NumUtils {

    /**
     * Calculates the compound interest by the following formula:
     * 
     * <pre>
     * (1 + percentGrowth/100)pow(days) * initialCapital
     * </pre>
     * 
     * @param initialCapital the initial capital
     * @param percentGrowth  the percent growth in percentage format (e.g. 5%)
     * @param time           the time (e.g. 1 year)
     * @return the compound interest
     */
    public static Num compoundInterest(Num initialCapital, Num percentGrowth, int time) {
        Num one = initialCapital.function().apply(1);
        Num hundred = initialCapital.function().apply(100);
        return ((one.plus(percentGrowth.dividedBy(hundred)).pow(time)).minus(one)).multipliedBy(initialCapital);
    }

}
