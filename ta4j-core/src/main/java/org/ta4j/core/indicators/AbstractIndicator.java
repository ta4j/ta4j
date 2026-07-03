/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract {@link Indicator indicator}.
 */
public abstract class AbstractIndicator<T> implements Indicator<T> {

    /** The logger. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final BarSeries series;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected AbstractIndicator(BarSeries series) {
        this.series = unwrapBarSeries(series);
    }

    @Override
    public BarSeries getBarSeries() {
        return new ReadOnlyBarSeriesView(series);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    static BarSeries unwrapBarSeries(BarSeries barSeries) {
        BarSeries currentSeries = Objects.requireNonNull(barSeries, "barSeries");
        while (currentSeries instanceof ReadOnlyBarSeriesView view) {
            currentSeries = view.delegate;
        }
        return currentSeries;
    }

    private static final class ReadOnlyBarSeriesView implements BarSeries {

        @Serial
        private static final long serialVersionUID = 8231541227186054452L;

        private final BarSeries delegate;

        private ReadOnlyBarSeriesView(BarSeries delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public NumFactory numFactory() {
            return delegate.numFactory();
        }

        @Override
        public BarBuilder barBuilder() {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Bar getBar(int i) {
            return delegate.getBar(i);
        }

        @Override
        public int getBarCount() {
            return delegate.getBarCount();
        }

        @Override
        public List<Bar> getBarData() {
            return List.copyOf(delegate.getBarData());
        }

        @Override
        public int getBeginIndex() {
            return delegate.getBeginIndex();
        }

        @Override
        public int getEndIndex() {
            return delegate.getEndIndex();
        }

        @Override
        public int getMaximumBarCount() {
            return delegate.getMaximumBarCount();
        }

        @Override
        public void setMaximumBarCount(int maximumBarCount) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public int getRemovedBarsCount() {
            return delegate.getRemovedBarsCount();
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            return delegate.getSubSeries(startIndex, endIndex);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BarSeries otherSeries && unwrapBarSeries(delegate) == unwrapBarSeries(otherSeries);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(unwrapBarSeries(delegate));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
