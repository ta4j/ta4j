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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DecimalNumFactory implements NumFactory {

    /**
     * prebuilt constants with defined precisions
     */
    private static final Map<MathContext, Map<String, DecimalNum>> decimalNums = new ConcurrentHashMap<>();

    /**
     * factory singletons for specific precisions
     */
    private static final Map<Integer, NumFactory> factories = new ConcurrentHashMap<>();

    private final MathContext mathContext;

    private DecimalNumFactory() {
        this(DEFAULT_PRECISION);
    }

    private DecimalNumFactory(final int precision) {
        this.mathContext = new MathContext(precision, RoundingMode.HALF_UP);
    }

    @Override
    public Num minusOne() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("-1");
    }

    @Override
    public Num zero() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("0");
    }

    @Override
    public Num one() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("1");
    }

    @Override
    public Num two() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("2");
    }

    @Override
    public Num three() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("3");
    }

    @Override
    public Num hundred() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("100");
    }

    @Override
    public Num thousand() {
        return decimalNums.computeIfAbsent(this.mathContext, DecimalNumFactory::initConstants).get("1000");
    }

    @Override
    public Num numOf(final Number number) {
        return numOf(number.toString());
    }

    @Override
    public Num numOf(final String number) {
        return DecimalNum.valueOf(number, this.mathContext);
    }


    private static Map<String, DecimalNum> initConstants(final MathContext mathContext) {
        return Map.of(
            "-1", DecimalNum.valueOf("-1", mathContext), "0", DecimalNum.valueOf("0", mathContext), "1",
            DecimalNum.valueOf("1", mathContext), "2", DecimalNum.valueOf("2", mathContext), "3",
            DecimalNum.valueOf("3", mathContext), "50", DecimalNum.valueOf("50", mathContext), "100",
            DecimalNum.valueOf("100", mathContext), "1000", DecimalNum.valueOf("1000", mathContext)
        );
    }

    public static NumFactory getInstance() {
        return getInstance(DEFAULT_PRECISION);
    }

    public static NumFactory getInstance(final int precision) {
        return factories.computeIfAbsent(precision, DecimalNumFactory::new);
    }
}
