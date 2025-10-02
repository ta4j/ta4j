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
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

import java.util.function.BinaryOperator;

/**
 * Objects of this class defer evaluation of an arithmetic operation.
 *
 * <p>
 * This is a lightweight, non-cached implementation for binary operations
 * between two indicators.
 */
public class BinaryOperation implements Indicator<Num> {

    /**
     * Returns an {@code Indicator} whose value is {@code (left + right)}.
     *
     * @param left
     * @param right
     * @return {@code left + right}, rounded as necessary
     * @see Num#plus
     */
    public static BinaryOperation sum(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::plus, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left - right)}.
     *
     * @param left
     * @param right
     * @return {@code left - right}, rounded as necessary
     * @see Num#minus
     */
    public static BinaryOperation difference(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::minus, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left * right)}.
     *
     * @param left
     * @param right
     * @return {@code left * right}, rounded as necessary
     * @see Num#multipliedBy
     */
    public static BinaryOperation product(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::multipliedBy, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left / right)}.
     *
     * @param left
     * @param right
     * @return {@code left / right}, rounded as necessary
     * @see Num#dividedBy
     */
    public static BinaryOperation quotient(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::dividedBy, left, right);
    }

    /**
     * Returns the minimum of {@code left} and {@code right} as an
     * {@code Indicator}.
     *
     * @param left
     * @param right
     * @return the {@code Indicator} whose value is the smaller of {@code left} and
     *         {@code right}. If they are equal, {@code left} is returned.
     * @see Num#min
     */
    public static BinaryOperation min(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::min, left, right);
    }

    /**
     * Returns the maximum of {@code left} and {@code right} as an
     * {@code Indicator}.
     *
     * @param left
     * @param right
     * @return the {@code Indicator} whose value is the greater of {@code left} and
     *         {@code right}. If they are equal, {@code left} is returned.
     * @see Num#max
     */
    public static BinaryOperation max(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(Num::max, left, right);
    }

    // Overloaded methods for operations with constants

    /**
     * Returns an {@code Indicator} whose value is {@code (indicator + addend)}.
     *
     * @param indicator the indicator
     * @param addend    the coefficient to add
     * @return {@code indicator + addend}, rounded as necessary
     */
    public static BinaryOperation sum(final Indicator<Num> indicator, final Number addend) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(addend));
        return new BinaryOperation(Num::plus, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (indicator - subtrahend)}.
     *
     * @param indicator  the indicator
     * @param subtrahend the subtrahend to subtract
     * @return {@code indicator - subtrahend}, rounded as necessary
     */
    public static BinaryOperation difference(final Indicator<Num> indicator, final Number subtrahend) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(subtrahend));
        return new BinaryOperation(Num::minus, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is
     * {@code (indicator * coefficient)}.
     *
     * @param indicator   the indicator
     * @param coefficient the coefficient to multiply by
     * @return {@code indicator * coefficient}, rounded as necessary
     */
    public static BinaryOperation product(final Indicator<Num> indicator, final Number coefficient) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(coefficient));
        return new BinaryOperation(Num::multipliedBy, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is
     * {@code (indicator / coefficient)}.
     *
     * @param indicator   the indicator
     * @param coefficient the coefficient to divide by
     * @return {@code indicator / coefficient}, rounded as necessary
     */
    public static BinaryOperation quotient(final Indicator<Num> indicator, final Number coefficient) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(coefficient));
        return new BinaryOperation(Num::dividedBy, indicator, constantIndicator);
    }

    /**
     * Returns the minimum of {@code indicator} and {@code constant} as an
     * {@code Indicator}.
     *
     * @param indicator the indicator
     * @param constant  the constant to compare with
     * @return the {@code Indicator} whose value is the smaller of {@code indicator}
     *         and {@code constant}. If they are equal, {@code indicator} is
     *         returned.
     */
    public static BinaryOperation min(final Indicator<Num> indicator, final Number constant) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(constant));
        return new BinaryOperation(Num::min, indicator, constantIndicator);
    }

    /**
     * Returns the maximum of {@code indicator} and {@code constant} as an
     * {@code Indicator}.
     *
     * @param indicator the indicator
     * @param constant  the constant to compare with
     * @return the {@code Indicator} whose value is the greater of {@code indicator}
     *         and {@code constant}. If they are equal, {@code indicator} is
     *         returned.
     */
    public static BinaryOperation max(final Indicator<Num> indicator, final Number constant) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(constant));
        return new BinaryOperation(Num::max, indicator, constantIndicator);
    }

    private final BinaryOperator<Num> operator;
    private final Indicator<Num> left;
    private final Indicator<Num> right;

    private BinaryOperation(final BinaryOperator<Num> operator, final Indicator<Num> left, final Indicator<Num> right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public Num getValue(final int index) {
        final var n1 = left.getValue(index);
        final var n2 = right.getValue(index);
        return operator.apply(n1, n2);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(left.getCountOfUnstableBars(), right.getCountOfUnstableBars());
    }

    @Override
    public BarSeries getBarSeries() {
        return left.getBarSeries();
    }

}
