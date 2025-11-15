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
 * @deprecated Use {@link BinaryOperationIndicator} instead. This class will be
 *             removed in a future release.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class BinaryOperation implements Indicator<Num> {

    private final BinaryOperationIndicator delegate;

    private BinaryOperation(final BinaryOperationIndicator delegate) {
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
     * @deprecated Use {@link BinaryOperationIndicator#sum(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation sum(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.sum(left, right));
    }

    /**
     * @deprecated Use
     *             {@link BinaryOperationIndicator#difference(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation difference(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.difference(left, right));
    }

    /**
     * @deprecated Use
     *             {@link BinaryOperationIndicator#product(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation product(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.product(left, right));
    }

    /**
     * @deprecated Use
     *             {@link BinaryOperationIndicator#quotient(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation quotient(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.quotient(left, right));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#min(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation min(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.min(left, right));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#max(Indicator, Indicator)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation max(final Indicator<Num> left, final Indicator<Num> right) {
        return new BinaryOperation(BinaryOperationIndicator.max(left, right));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#sum(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation sum(final Indicator<Num> indicator, final Number addend) {
        return new BinaryOperation(BinaryOperationIndicator.sum(indicator, addend));
    }

    /**
     * @deprecated Use
     *             {@link BinaryOperationIndicator#difference(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation difference(final Indicator<Num> indicator, final Number subtrahend) {
        return new BinaryOperation(BinaryOperationIndicator.difference(indicator, subtrahend));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#product(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation product(final Indicator<Num> indicator, final Number coefficient) {
        return new BinaryOperation(BinaryOperationIndicator.product(indicator, coefficient));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#quotient(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation quotient(final Indicator<Num> indicator, final Number coefficient) {
        return new BinaryOperation(BinaryOperationIndicator.quotient(indicator, coefficient));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#min(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation min(final Indicator<Num> indicator, final Number constant) {
        return new BinaryOperation(BinaryOperationIndicator.min(indicator, constant));
    }

    /**
     * @deprecated Use {@link BinaryOperationIndicator#max(Indicator, Number)}
     *             instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static BinaryOperation max(final Indicator<Num> indicator, final Number constant) {
        return new BinaryOperation(BinaryOperationIndicator.max(indicator, constant));
    }
}
