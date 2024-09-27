/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

    private DoubleNumFactory() {
        // hidden
    }

    @Override
    public Num minusOne() {
        return DoubleNum.MINUS_ONE;
    }

    @Override
    public Num zero() {
        return DoubleNum.ZERO;
    }

    @Override
    public Num one() {
        return DoubleNum.ONE;
    }

    @Override
    public Num two() {
        return DoubleNum.TWO;
    }

    @Override
    public Num three() {
        return DoubleNum.THREE;
    }

    @Override
    public Num hundred() {
        return DoubleNum.HUNDRED;
    }

    @Override
    public Num thousand() {
        return DoubleNum.THOUSAND;
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
