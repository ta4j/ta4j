/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;

import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/**
 * Test support for building series whose raw storage retains readable trailing
 * bars beyond the logical window end. The package-private {@link BaseBarSeries}
 * constructor required for this shape is only reachable inside
 * {@code org.ta4j.core}, so analysis tests share this factory.
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
}
