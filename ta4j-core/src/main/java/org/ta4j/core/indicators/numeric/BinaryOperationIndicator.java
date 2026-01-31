/*
 * SPDX-License-Identifier: MIT
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
public class BinaryOperationIndicator implements Indicator<Num> {

    /**
     * Enumeration of supported binary operations.
     */
    public enum Operation {
        PLUS, MINUS, MULTIPLY, DIVIDE, MIN, MAX
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left + right)}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return {@code left + right}, rounded as necessary
     * @see Num#plus
     */
    public static BinaryOperationIndicator sum(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.PLUS, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left - right)}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return {@code left - right}, rounded as necessary
     * @see Num#minus
     */
    public static BinaryOperationIndicator difference(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.MINUS, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left * right)}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return {@code left * right}, rounded as necessary
     * @see Num#multipliedBy
     */
    public static BinaryOperationIndicator product(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.MULTIPLY, left, right);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (left / right)}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return {@code left / right}, rounded as necessary
     * @see Num#dividedBy
     */
    public static BinaryOperationIndicator quotient(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.DIVIDE, left, right);
    }

    /**
     * Returns the minimum of {@code left} and {@code right} as an
     * {@code Indicator}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return the {@code Indicator} whose value is the smaller of {@code left} and
     *         {@code right}. If they are equal, {@code left} is returned.
     * @see Num#min
     */
    public static BinaryOperationIndicator min(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.MIN, left, right);
    }

    /**
     * Returns the maximum of {@code left} and {@code right} as an
     * {@code Indicator}.
     *
     * @param left  the left operand Indicator
     * @param right the right operand Indicator
     * @return the {@code Indicator} whose value is the greater of {@code left} and
     *         {@code right}. If they are equal, {@code left} is returned.
     * @see Num#max
     */
    public static BinaryOperationIndicator max(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperationIndicator(Operation.MAX, left, right);
    }

    // Overloaded methods for operations with constants

    /**
     * Returns an {@code Indicator} whose value is {@code (indicator + addend)}.
     *
     * @param indicator the indicator
     * @param addend    the coefficient to add
     * @return {@code indicator + addend}, rounded as necessary
     */
    public static BinaryOperationIndicator sum(final Indicator<Num> indicator, final Number addend) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(addend));
        return new BinaryOperationIndicator(Operation.PLUS, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code (indicator - subtrahend)}.
     *
     * @param indicator  the indicator
     * @param subtrahend the subtrahend to subtract
     * @return {@code indicator - subtrahend}, rounded as necessary
     */
    public static BinaryOperationIndicator difference(final Indicator<Num> indicator, final Number subtrahend) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(subtrahend));
        return new BinaryOperationIndicator(Operation.MINUS, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is
     * {@code (indicator * coefficient)}.
     *
     * @param indicator   the indicator
     * @param coefficient the coefficient to multiply by
     * @return {@code indicator * coefficient}, rounded as necessary
     */
    public static BinaryOperationIndicator product(final Indicator<Num> indicator, final Number coefficient) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(coefficient));
        return new BinaryOperationIndicator(Operation.MULTIPLY, indicator, constantIndicator);
    }

    /**
     * Returns an {@code Indicator} whose value is
     * {@code (indicator / coefficient)}.
     *
     * @param indicator   the indicator
     * @param coefficient the coefficient to divide by
     * @return {@code indicator / coefficient}, rounded as necessary
     */
    public static BinaryOperationIndicator quotient(final Indicator<Num> indicator, final Number coefficient) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(coefficient));
        return new BinaryOperationIndicator(Operation.DIVIDE, indicator, constantIndicator);
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
    public static BinaryOperationIndicator min(final Indicator<Num> indicator, final Number constant) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(constant));
        return new BinaryOperationIndicator(Operation.MIN, indicator, constantIndicator);
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
    public static BinaryOperationIndicator max(final Indicator<Num> indicator, final Number constant) {
        final var constantIndicator = new ConstantIndicator<>(indicator.getBarSeries(),
                indicator.getBarSeries().numFactory().numOf(constant));
        return new BinaryOperationIndicator(Operation.MAX, indicator, constantIndicator);
    }

    private final Operation operation;
    private final BinaryOperator<Num> operator;
    private final Indicator<Num> left;
    private final Indicator<Num> right;

    /**
     * Constructor for serialization support.
     *
     * @param operation the operation type
     * @param left      the left operand indicator
     * @param right     the right operand indicator
     */
    public BinaryOperationIndicator(final Operation operation, final Indicator<Num> left, final Indicator<Num> right) {
        if (operation == null || left == null || right == null) {
            throw new IllegalArgumentException("Operation and indicators must not be null");
        }
        if (!left.getBarSeries().equals(right.getBarSeries())) {
            throw new IllegalArgumentException("Left and right indicators must share the same BarSeries");
        }
        this.operation = operation;
        this.operator = getOperator(operation);
        this.left = left;
        this.right = right;
    }

    private static BinaryOperator<Num> getOperator(Operation operation) {
        return switch (operation) {
        case PLUS -> Num::plus;
        case MINUS -> Num::minus;
        case MULTIPLY -> Num::multipliedBy;
        case DIVIDE -> Num::dividedBy;
        case MIN -> Num::min;
        case MAX -> Num::max;
        };
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
        return left.getBarSeries(); // Both indicators share the same series (validated in constructor)
    }

}
