/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.concurrent.atomic.AtomicInteger;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Test indicator that counts read and calculation requests.
 *
 * @since 0.22.7
 */
final class CountingIndicator extends CachedIndicator<Num> {

    private final AtomicInteger calculations = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();
    private final Indicator<Num> delegate;

    CountingIndicator(BarSeries series) {
        this(series, null);
    }

    private CountingIndicator(BarSeries series, Indicator<Num> delegate) {
        super(series);
        this.delegate = delegate;
    }

    static CountingIndicator closePrice(BarSeries series) {
        return new CountingIndicator(series, new ClosePriceIndicator(series));
    }

    static CountingIndicator delegate(Indicator<Num> delegate) {
        return new CountingIndicator(delegate.getBarSeries(), delegate);
    }

    @Override
    public Num getValue(int index) {
        reads.incrementAndGet();
        return super.getValue(index);
    }

    @Override
    protected Num calculate(int index) {
        calculations.incrementAndGet();
        if (delegate != null) {
            return delegate.getValue(index);
        }
        return getBarSeries().numFactory().numOf(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return delegate == null ? 0 : delegate.getCountOfUnstableBars();
    }

    int getCalculationCount() {
        return calculations.get();
    }

    int readCount() {
        return reads.get();
    }

    void resetCalculationCount() {
        calculations.set(0);
    }
}
