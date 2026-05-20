/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.performance;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Internal indicator wrapper that records source reads for performance
 * experiment diagnostics.
 */
final class CountingIndicator implements Indicator<Num> {

    private final Indicator<Num> delegate;
    private long readCount;

    CountingIndicator(Indicator<Num> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Num getValue(int index) {
        readCount++;
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

    long readCount() {
        return readCount;
    }
}
