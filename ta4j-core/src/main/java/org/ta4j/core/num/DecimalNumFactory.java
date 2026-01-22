/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import java.math.MathContext;

public class DecimalNumFactory implements NumFactory {

    private final MathContext mathContext;
    private final DecimalNum minusOne;
    private final DecimalNum zero;
    private final DecimalNum one;
    private final DecimalNum two;
    private final DecimalNum three;
    private final DecimalNum hundred;
    private final DecimalNum thousand;

    private DecimalNumFactory(final MathContext mathContext) {
        this.mathContext = mathContext;
        this.minusOne = DecimalNum.valueOf(-1, mathContext);
        this.zero = DecimalNum.valueOf(0, mathContext);
        this.one = DecimalNum.valueOf(1, mathContext);
        this.two = DecimalNum.valueOf(2, mathContext);
        this.three = DecimalNum.valueOf(3, mathContext);
        this.hundred = DecimalNum.valueOf(100, mathContext);
        this.thousand = DecimalNum.valueOf(1000, mathContext);
    }

    public static NumFactory getInstance() {
        return getInstance(DecimalNum.getDefaultMathContext());
    }

    public static NumFactory getInstance(final int precision) {
        final var roundingMode = DecimalNum.getDefaultMathContext().getRoundingMode();
        return new DecimalNumFactory(new MathContext(precision, roundingMode));
    }

    public static NumFactory getInstance(final MathContext mathContext) {
        return new DecimalNumFactory(mathContext);
    }

    @Override
    public Num minusOne() {
        return this.minusOne;
    }

    @Override
    public Num zero() {
        return this.zero;
    }

    @Override
    public Num one() {
        return this.one;
    }

    @Override
    public Num two() {
        return this.two;
    }

    @Override
    public Num three() {
        return this.three;
    }

    @Override
    public Num hundred() {
        return this.hundred;
    }

    @Override
    public Num thousand() {
        return this.thousand;
    }

    @Override
    public Num numOf(final Number number) {
        return numOf(number.toString());
    }

    @Override
    public Num numOf(final String number) {
        return DecimalNum.valueOf(number, this.mathContext);
    }

}
