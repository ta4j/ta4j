/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import java.io.Serial;

public final class DoubleNumFactory implements NumFactory {

    @Serial
    private static final long serialVersionUID = 1L;

    private DoubleNumFactory() {
        // hidden
    }

    @Override
    public Num minusOne() {
        return DoubleNum.MINUS_ONE;
    }

    @Override
    public Num zero() {
        return DoubleNum.ZERO;
    }

    @Override
    public Num one() {
        return DoubleNum.ONE;
    }

    @Override
    public Num two() {
        return DoubleNum.TWO;
    }

    @Override
    public Num three() {
        return DoubleNum.THREE;
    }

    @Override
    public Num hundred() {
        return DoubleNum.HUNDRED;
    }

    @Override
    public Num thousand() {
        return DoubleNum.THOUSAND;
    }

    @Override
    public Num numOf(final Number number) {
        return DoubleNum.valueOf(number);
    }

    @Override
    public Num numOf(final String number) {
        return DoubleNum.valueOf(number);
    }

    public static DoubleNumFactory getInstance() {
        return InstanceHolder.INSTANCE.factory;
    }

    @Serial
    private Object readResolve() {
        return getInstance();
    }

    private enum InstanceHolder {
        INSTANCE;

        private final DoubleNumFactory factory = new DoubleNumFactory();
    }
}
