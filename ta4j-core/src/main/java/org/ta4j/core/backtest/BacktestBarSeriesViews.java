/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.IndicatorContext;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class BacktestBarSeriesViews {

    private BacktestBarSeriesViews() {
    }

    static BarSeries snapshot(BarSeries barSeries) {
        BarSeries series = Objects.requireNonNull(barSeries, "barSeries");
        return new BaseBarSeriesBuilder().withName(series.getName())
                .withNumFactory(series.numFactory())
                .withBars(series.getBarData())
                .withMaxBarCount(series.getMaximumBarCount())
                .build();
    }

    static BarSeries readOnlyView(BarSeries barSeries) {
        return new ReadOnlyBarSeriesView(barSeries);
    }

    private static final class ReadOnlyBarSeriesView implements BarSeries {

        @Serial
        private static final long serialVersionUID = 8372163584204322040L;

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
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
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
        public IndicatorContext indicators() {
            return delegate.indicators();
        }

        @Override
        public long getBarHistoryEpoch() {
            return delegate.getBarHistoryEpoch();
        }

        @Override
        public long getBarHistoryRevision() {
            return delegate.getBarHistoryRevision();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
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
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
        }

        @Override
        public int getRemovedBarsCount() {
            return delegate.getRemovedBarsCount();
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("Backtest bar series views are read-only");
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            return delegate.getSubSeries(startIndex, endIndex);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReadOnlyBarSeriesView view && delegate == view.delegate;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(delegate);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
