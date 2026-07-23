/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.NaN;

/**
 * Constant indicator.
 *
 * <p>
 * Returns a constant value for a bar.
 *
 * @param <T> constant value type
 */
public class ConstantIndicator<T> extends AbstractIndicator<T> {

    private final T value;

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param t      the constant value
     */
    public ConstantIndicator(BarSeries series, T t) {
        super(series, isStructurallyShareable(t) ? identityOfExact(ConstantIndicator.class, t) : null);
        this.value = t;
    }

    private static boolean isStructurallyShareable(Object value) {
        return value == null || value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal || value instanceof Enum<?> || value instanceof DecimalNum
                || value instanceof DoubleNum || value == NaN.NaN;
    }

    @Override
    public T getValue(int index) {
        return value;
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Value: " + value;
    }
}
