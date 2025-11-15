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
import org.ta4j.core.num.Num;

/**
 * @deprecated Use {@link UnaryOperationIndicator} instead. This class will be
 *             removed in a future release.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class UnaryOperation implements Indicator<Num> {

    private final UnaryOperationIndicator delegate;

    private UnaryOperation(final UnaryOperationIndicator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Num getValue(final int index) {
        return delegate.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return delegate.getCountOfUnstableBars();
    }

    @Override
    public BarSeries getBarSeries() {
        return delegate.getBarSeries();
    }

    /**
     * @deprecated Use {@link UnaryOperationIndicator#sqrt(Indicator)} instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static UnaryOperation sqrt(final Indicator<Num> operand) {
        return new UnaryOperation(UnaryOperationIndicator.sqrt(operand));
    }

    /**
     * @deprecated Use {@link UnaryOperationIndicator#abs(Indicator)} instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static UnaryOperation abs(final Indicator<Num> operand) {
        return new UnaryOperation(UnaryOperationIndicator.abs(operand));
    }

    /**
     * @deprecated Use {@link UnaryOperationIndicator#pow(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static UnaryOperation pow(final Indicator<Num> operand, final Number exponent) {
        return new UnaryOperation(UnaryOperationIndicator.pow(operand, exponent));
    }

    /**
     * @deprecated Use {@link UnaryOperationIndicator#log(Indicator)} instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static UnaryOperation log(final Indicator<Num> operand) {
        return new UnaryOperation(UnaryOperationIndicator.log(operand));
    }

    /**
     * @deprecated Use
     *             {@link UnaryOperationIndicator#substitute(Indicator, Num, Num)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static UnaryOperation substitute(final Indicator<Num> operand, final Num valueToReplace,
            final Num replacementValue) {
        return new UnaryOperation(UnaryOperationIndicator.substitute(operand, valueToReplace, replacementValue));
    }
}
