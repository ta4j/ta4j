/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.math.MathContext;

public class DecimalNumFactory implements NumFactory {

    @Serial
    private static final long serialVersionUID = 1L;

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

    @Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (this.mathContext == null) {
            throw new InvalidObjectException("MathContext must not be null");
        }
        if (this.minusOne == null || this.zero == null || this.one == null || this.two == null || this.three == null
                || this.hundred == null || this.thousand == null) {
            throw new InvalidObjectException("Cached DecimalNum constants must not be null");
        }
    }

    @Serial
    private void readObjectNoData() throws InvalidObjectException {
        throw new InvalidObjectException("DecimalNumFactory class data is required");
    }

}
