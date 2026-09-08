/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * Test support for series whose retention or logical bounds require
 * package-private constructors. Analysis tests share these factories without
 * exposing additional production constructors.
 */
public final class ConstrainedSeriesSupport {

    private ConstrainedSeriesSupport() {
    }

    /**
     * Builds a constrained series holding every {@code closes} bar in raw storage
     * while exposing only the logical window {@code [0, endIndex]}. The trailing
     * bars after {@code endIndex} stay addressable so analyses can price exits that
     * landed beyond the window.
     *
     * @param name       the series name
     * @param numFactory the number factory
     * @param endIndex   the last index of the exposed logical window
     * @param closes     the raw close prices, at least {@code endIndex + 1} entries
     * @return the constrained series
     */
    public static BarSeries trailingConstrainedSeries(String name, NumFactory numFactory, int endIndex,
            double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), 0, endIndex, true, numFactory,
                new TimeBarBuilderFactory());
    }

    /**
     * Builds a series with an explicit logical/raw index offset.
     *
     * @param name             the series name
     * @param numFactory       the number factory
     * @param beginIndex       first logical index
     * @param endIndex         last logical index
     * @param removedBarsCount number of leading raw indexes
     * @param closes           raw close prices
     * @return the offset series
     */
    public static BarSeries offsetSeries(String name, NumFactory numFactory, int beginIndex, int endIndex,
            int removedBarsCount, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), beginIndex, endIndex, removedBarsCount, false,
                numFactory, new TimeBarBuilderFactory());
    }

    /**
     * Builds a constrained one-bar series whose single bar sits at
     * {@link Integer#MAX_VALUE}, exposing the terminal index to analyses that must
     * survive loop arithmetic on the last representable index.
     *
     * @param name       the series name
     * @param numFactory the number factory
     * @param close      the raw close price of the terminal bar
     * @return the constrained terminal series
     */
    public static BarSeries terminalOneBarSeries(String name, NumFactory numFactory, double close) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(close).build();
        return new BaseBarSeries(name, List.copyOf(source.getBarData()), Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, true, numFactory, new TimeBarBuilderFactory());
    }

    /**
     * Builds a rolling window containing all but the last close. When armed, the
     * next read lease appends the last bar immediately before acquiring the lock,
     * reproducing retention advancement at the materialization boundary.
     */
    public static ConcurrentBarSeries rollingSeriesWithAppendBeforeReadLock(NumFactory numFactory,
            AtomicBoolean appendBeforeLock, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        int initialSize = source.getBarCount() - 1;
        ConcurrentBarSeries series = new ConcurrentBarSeries("rolling-read-lease",
                new ArrayList<>(source.getBarData().subList(0, initialSize)), 0, initialSize - 1, false, numFactory,
                new MockBarBuilderFactory()) {
            @Override
            public void withReadLock(Runnable action) {
                if (appendBeforeLock.compareAndSet(true, false)) {
                    addBar(source.getBar(initialSize));
                }
                super.withReadLock(action);
            }
        };
        series.setMaximumBarCount(initialSize);
        return series;
    }

    /**
     * Appends the last close immediately after the next outermost read lease,
     * reproducing retention advancement between materialization and consumption
     * when a caller fails to hold one coherent lease.
     */
    public static ConcurrentBarSeries rollingSeriesWithAppendAfterReadLock(NumFactory numFactory,
            AtomicBoolean appendAfterLock, double... closes) {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        int initialSize = source.getBarCount() - 1;
        ConcurrentBarSeries series = new ConcurrentBarSeries("rolling-read-lease",
                new ArrayList<>(source.getBarData().subList(0, initialSize)), 0, initialSize - 1, false, numFactory,
                new MockBarBuilderFactory()) {
            private int readDepth;

            @Override
            public void withReadLock(Runnable action) {
                readDepth++;
                try {
                    super.withReadLock(action);
                } finally {
                    appendAfterOutermostLease();
                }
            }

            @Override
            public <T> T withReadLock(Supplier<T> action) {
                readDepth++;
                try {
                    return super.withReadLock(action);
                } finally {
                    appendAfterOutermostLease();
                }
            }

            private void appendAfterOutermostLease() {
                if (--readDepth == 0 && appendAfterLock.compareAndSet(true, false)) {
                    addBar(source.getBar(initialSize));
                }
            }
        };
        series.setMaximumBarCount(initialSize);
        return series;
    }
}
