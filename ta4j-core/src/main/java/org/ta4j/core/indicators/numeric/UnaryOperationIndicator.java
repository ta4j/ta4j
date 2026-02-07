/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.function.UnaryOperator;

/**
 * Objects of this class defer the evaluation of a unary operator, like sqrt().
 * <p>
 * There may be other unary operations on Num that could be added here.
 */
public class UnaryOperationIndicator implements Indicator<Num> {

    /**
     * Enumeration of supported unary operations.
     */
    public enum Operation {
        ABS, SQRT, LOG, POW, SUBSTITUTE
    }

    @SuppressWarnings("unused") // Used by serialization via reflection
    private final Operation operation;
    private final UnaryOperator<Num> operator;
    private final Indicator<Num> operand;
    @SuppressWarnings("unused") // Used by serialization via reflection
    private final Num exponent; // For POW operation
    @SuppressWarnings("unused") // Used by serialization via reflection
    private final Num valueToReplace; // For SUBSTITUTE operation
    @SuppressWarnings("unused") // Used by serialization via reflection
    private final Num replacementValue; // For SUBSTITUTE operation

    /**
     * Constructor for serialization support.
     *
     * @param operation the operation type
     * @param operand   the operand indicator
     */
    public UnaryOperationIndicator(final Operation operation, final Indicator<Num> operand) {
        this(operation, operand, null, null, null);
    }

    /**
     * Constructor for POW operation with exponent.
     *
     * @param operation the operation type (must be POW)
     * @param operand   the operand indicator
     * @param exponent  the exponent for POW operation
     */
    public UnaryOperationIndicator(final Operation operation, final Indicator<Num> operand, final Number exponent) {
        if (operation != Operation.POW) {
            throw new IllegalArgumentException("Exponent parameter is only valid for POW operation");
        }
        this.operation = operation;
        this.operand = operand;
        this.exponent = operand.getBarSeries().numFactory().numOf(exponent);
        this.valueToReplace = null;
        this.replacementValue = null;
        this.operator = getOperator(operation, this.exponent, null, null);
    }

    /**
     * Constructor for SUBSTITUTE operation with replacement values.
     *
     * @param operation        the operation type (must be SUBSTITUTE)
     * @param operand          the operand indicator
     * @param valueToReplace   the value to replace
     * @param replacementValue the replacement value
     */
    public UnaryOperationIndicator(final Operation operation, final Indicator<Num> operand, final Num valueToReplace,
            final Num replacementValue) {
        if (operation != Operation.SUBSTITUTE) {
            throw new IllegalArgumentException("Replacement values are only valid for SUBSTITUTE operation");
        }
        this.operation = operation;
        this.operand = operand;
        this.exponent = null;
        this.valueToReplace = valueToReplace;
        this.replacementValue = replacementValue;
        this.operator = getOperator(operation, null, valueToReplace, replacementValue);
    }

    private UnaryOperationIndicator(final Operation operation, final Indicator<Num> operand, final Num exponent,
            final Num valueToReplace, final Num replacementValue) {
        if (operation == null || operand == null) {
            throw new IllegalArgumentException("Operation and operand must not be null");
        }
        this.operation = operation;
        this.operand = operand;
        this.exponent = exponent;
        this.valueToReplace = valueToReplace;
        this.replacementValue = replacementValue;
        this.operator = getOperator(operation, exponent, valueToReplace, replacementValue);
    }

    private static UnaryOperator<Num> getOperator(Operation operation, Num exponent, Num valueToReplace,
            Num replacementValue) {
        return switch (operation) {
        case ABS -> Num::abs;
        case SQRT -> Num::sqrt;
        case LOG -> Num::log;
        case POW -> {
            if (exponent == null) {
                throw new IllegalArgumentException("Exponent is required for POW operation");
            }
            yield val -> val.pow(exponent);
        }
        case SUBSTITUTE -> {
            if (valueToReplace == null || replacementValue == null) {
                throw new IllegalArgumentException(
                        "ValueToReplace and replacementValue are required for SUBSTITUTE operation");
            }
            yield operandValue -> operandValue.equals(valueToReplace) ? replacementValue : operandValue;
        }
        };
    }

    /**
     * Returns an {@code Indicator} whose value is {@code √(operand)}.
     *
     * @param operand the operand indicator
     * @return {@code √(operand)}
     * @see Num#sqrt
     */
    public static UnaryOperationIndicator sqrt(Indicator<Num> operand) {
        return new UnaryOperationIndicator(Operation.SQRT, operand);
    }

    /**
     * Returns an {@code Indicator} whose value is the absolute value of
     * {@code operand}.
     *
     * @param operand the operand indicator
     * @return {@code abs(operand)}
     * @see Num#abs
     */
    public static UnaryOperationIndicator abs(Indicator<Num> operand) {
        return new UnaryOperationIndicator(Operation.ABS, operand);
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
        return new UnaryOperationIndicator(Operation.POW, operand, exponent);
    }

    /**
     * Returns an {@code Indicator} whose value is {@code log(operand)}.
     *
     * @param operand the operand indicator
     * @return {@code log(operand)}
     */
    public static UnaryOperationIndicator log(Indicator<Num> operand) {
        return new UnaryOperationIndicator(Operation.LOG, operand);
    }

    /**
     * Returns an {@code Indicator} that replaces a given operand value with a
     * substitute.
     *
     * @param operand          the indicator supplying the original values to
     *                         inspect
     * @param valueToReplace   the value that, when matched exactly, triggers
     *                         substitution
     * @param replacementValue the value that replaces {@code valueToReplace} in the
     *                         resulting indicator
     * @return a unary operation indicator reflecting the original values with any
     *         exact matches substituted
     */
    public static UnaryOperationIndicator substitute(final Indicator<Num> operand, final Num valueToReplace,
            final Num replacementValue) {
        return new UnaryOperationIndicator(Operation.SUBSTITUTE, operand, valueToReplace, replacementValue);
    }

    @Override
    public Num getValue(int index) {
        Num n = operand.getValue(index);
        return operator.apply(n);
    }

    @Override
    public int getCountOfUnstableBars() {
        return operand.getCountOfUnstableBars();
    }

    @Override
    public BarSeries getBarSeries() {
        return operand.getBarSeries();
    }

}
