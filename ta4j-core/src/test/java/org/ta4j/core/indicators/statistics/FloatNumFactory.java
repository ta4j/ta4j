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
public class FloatNumFactory implements NumFactory {

    private static final long serialVersionUID = 1L;

    private static final FloatNumFactory INSTANCE = new FloatNumFactory();

    private FloatNumFactory() {
    }

    public static FloatNumFactory getInstance() {
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

    @Override
    public Num epsilon() {
        // The default epsilon (1e-12) is below the float rounding scale
        // (ULP(1.0f) is about 1.2e-7), so precision-aware guardrails such as
        // correlation and normalized-entropy bounds would reject valid
        // roundoff. 1e-5f is the float-domain analogue of the default's
        // 1e-12 double margin.
        return FloatNum.valueOf(1.0e-5f);
    }
}
