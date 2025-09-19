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
package org.ta4j.core.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

import java.util.function.UnaryOperator;

/**
 * Objects of this class defer the evaluation of a unary operator, like sqrt().
 * <p>
 * There may be other unary operations on Num that could be added here.
 */
public class UnaryOperationIndicator implements Indicator<Num> {

    private final UnaryOperator<Num> operator;
    private final Indicator<Num> operand;

    private UnaryOperationIndicator(UnaryOperator<Num> operator, Indicator<Num> operand) {
        this.operator = operator;
        this.operand = operand;
    }

    /**
     * Returns an {@code Indicator} whose value is {@code √(operand)}.
     *
     * @param operand
     * @return {@code √(operand)}
     * @see Num#sqrt
     */
    public static UnaryOperationIndicator sqrt(Indicator<Num> operand) {
        return new UnaryOperationIndicator(Num::sqrt, operand);
    }

    /**
     * Returns an {@code Indicator} whose value is the absolute value of
     * {@code operand}.
     *
     * @param operand
     * @return {@code abs(operand)}
     * @see Num#abs
     */
    public static UnaryOperationIndicator abs(Indicator<Num> operand) {
        return new UnaryOperationIndicator(Num::abs, operand);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code operand^exponent}.
     *
     * @param operand  the operand indicator
     * @param exponent the power exponent
     * @return {@code operand^exponent}
     * @see Num#pow
     */
    public static UnaryOperationIndicator pow(Indicator<Num> operand, Number exponent) {
        final var numExponent = operand.getBarSeries().numFactory().numOf(exponent);
        return new UnaryOperationIndicator(val -> val.pow(numExponent), operand);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code log(operand)}.
     *
     * @param operand the operand indicator
     * @return {@code log(operand)}
     * @apiNote precision may be lost, because this implementation is using the
     *          underlying doubleValue method
     */
    public static UnaryOperationIndicator log(Indicator<Num> operand) {
        return new UnaryOperationIndicator(val -> DecimalNumFactory.getInstance().numOf(Math.log(val.doubleValue())),
                operand);
    }

    /***
     *
     * @param operand
     * @param valueToReplace
     * @param replacementValue
     * @return
     */
    public static UnaryOperationIndicator substitute(final Indicator<Num> operand, final Num valueToReplace,
            final Num replacementValue) {
        return new UnaryOperationIndicator(
                operandValue -> operandValue.equals(valueToReplace) ? replacementValue : operandValue, operand);
    }

    @Override
    public Num getValue(int index) {
        Num n = operand.getValue(index);
        return operator.apply(n);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return operand.getBarSeries();
    }

}
