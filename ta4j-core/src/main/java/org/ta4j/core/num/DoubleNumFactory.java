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
package org.ta4j.core.num;

public class DoubleNumFactory implements NumFactory {

    private static final DoubleNumFactory DOUBLE_NUM_FACTORY = new DoubleNumFactory();

    private static final DoubleNum MINUS_ONE = DoubleNum.valueOf(-1);
    private static final DoubleNum ZERO = DoubleNum.valueOf(0);
    private static final DoubleNum ONE = DoubleNum.valueOf(1);
    private static final DoubleNum TWO = DoubleNum.valueOf(2);
    private static final DoubleNum THREE = DoubleNum.valueOf(3);
    private static final DoubleNum HUNDRED = DoubleNum.valueOf(100);
    private static final DoubleNum THOUSAND = DoubleNum.valueOf(1000);

    private DoubleNumFactory() {
        // hidden
    }

    @Override
    public Num minusOne() {
        return MINUS_ONE;
    }

    @Override
    public Num zero() {
        return ZERO;
    }

    @Override
    public Num one() {
        return ONE;
    }

    @Override
    public Num two() {
        return TWO;
    }

    @Override
    public Num three() {
        return THREE;
    }

    @Override
    public Num hundred() {
        return HUNDRED;
    }

    @Override
    public Num thousand() {
        return THOUSAND;
    }

    @Override
    public Num numOf(final Number number) {
        return DoubleNum.valueOf(number);
    }

    @Override
    public Num numOf(final String number) {
        return DoubleNum.valueOf(number);
    }

    public static DoubleNumFactory getInstance() {
        return DOUBLE_NUM_FACTORY;
    }
}
