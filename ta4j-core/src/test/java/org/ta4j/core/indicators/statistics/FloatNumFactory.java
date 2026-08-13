/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Test-only {@link NumFactory} producing {@link FloatNum} instances with
 * single-precision arithmetic.
 */
class FloatNumFactory implements NumFactory {

    private static final long serialVersionUID = 1L;

    private static final FloatNumFactory INSTANCE = new FloatNumFactory();

    private FloatNumFactory() {
    }

    static FloatNumFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public Num minusOne() {
        return FloatNum.valueOf(-1);
    }

    @Override
    public Num zero() {
        return FloatNum.valueOf(0);
    }

    @Override
    public Num one() {
        return FloatNum.valueOf(1);
    }

    @Override
    public Num two() {
        return FloatNum.valueOf(2);
    }

    @Override
    public Num three() {
        return FloatNum.valueOf(3);
    }

    @Override
    public Num hundred() {
        return FloatNum.valueOf(100);
    }

    @Override
    public Num thousand() {
        return FloatNum.valueOf(1000);
    }

    @Override
    public Num numOf(final Number number) {
        return FloatNum.valueOf(number);
    }

    @Override
    public Num numOf(final String number) {
        return FloatNum.valueOf(number);
    }
}
