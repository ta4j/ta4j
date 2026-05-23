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
 *
 * @since 0.22.7
 */
final class ReadCountingIndicator implements Indicator<Num> {

    private final Indicator<Num> delegate;
    private long readCount;

    ReadCountingIndicator(Indicator<Num> delegate) {
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
