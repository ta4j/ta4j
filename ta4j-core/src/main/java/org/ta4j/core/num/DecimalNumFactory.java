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

import static org.ta4j.core.num.DecimalNum.DEFAULT_PRECISION;

import java.math.MathContext;
import java.math.RoundingMode;

public class DecimalNumFactory implements NumFactory {

    private static final DecimalNum MINUS_ONE = DecimalNum.valueOf(-1, new MathContext(1));
    private static final DecimalNum ZERO = DecimalNum.valueOf(0, new MathContext(1));
    private static final DecimalNum ONE = DecimalNum.valueOf(1, new MathContext(1));
    private static final DecimalNum TWO = DecimalNum.valueOf(2, new MathContext(1));
    private static final DecimalNum THREE = DecimalNum.valueOf(3, new MathContext(1));
    private static final DecimalNum HUNDRED = DecimalNum.valueOf(100, new MathContext(3));
    private static final DecimalNum THOUSAND = DecimalNum.valueOf(1000, new MathContext(4));

    private final MathContext mathContext;

    private DecimalNumFactory(final int precision) {
        this.mathContext = new MathContext(precision, RoundingMode.HALF_UP);
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
        return numOf(number.toString());
    }

    @Override
    public Num numOf(final String number) {
        return DecimalNum.valueOf(number, this.mathContext);
    }

    public static NumFactory getInstance() {
        return getInstance(DEFAULT_PRECISION);
    }

    public static NumFactory getInstance(final int precision) {
        return new DecimalNumFactory(precision);
    }
}
