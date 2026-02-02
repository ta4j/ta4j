/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.time.Duration;

import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;

public class TimeBarBuilderFactory implements BarBuilderFactory {

    private final boolean realtimeBars;
    private final Duration timePeriod;

    /**
     * Constructor.
     *
     * @since 0.22.2
     */
    public TimeBarBuilderFactory() {
        this(null, false);
    }

    /**
     * Constructor.
     *
     * @param realtimeBars {@code true} to build {@link BaseRealtimeBar} instances
     *
     * @since 0.22.2
     */
    public TimeBarBuilderFactory(final boolean realtimeBars) {
        this(null, realtimeBars);
    }

    /**
     * Constructor.
     *
     * @param timePeriod the default time period for time bars
     *
     * @since 0.22.0
     */
    public TimeBarBuilderFactory(final Duration timePeriod) {
        this(timePeriod, false);
    }

    /**
     * Constructor.
     *
     * @param timePeriod   the default time period for time bars
     * @param realtimeBars {@code true} to build {@link BaseRealtimeBar} instances
     *
     * @since 0.22.2
     */
    public TimeBarBuilderFactory(final Duration timePeriod, final boolean realtimeBars) {
        this.timePeriod = timePeriod;
        this.realtimeBars = realtimeBars;
    }

    @Override
    public BarBuilder createBarBuilder(BarSeries series) {
        BarBuilder builder = new TimeBarBuilder(series.numFactory(), realtimeBars).bindTo(series);
        if (timePeriod != null) {
            builder.timePeriod(timePeriod);
        }
        return builder;
    }
}
