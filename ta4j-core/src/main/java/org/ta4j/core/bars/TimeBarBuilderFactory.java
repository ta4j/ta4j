/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.time.Duration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseRealtimeBar;

@SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of time period is a documented constructor contract; subclassing remains supported.")
public class TimeBarBuilderFactory implements BarBuilderFactory {

    private static final long serialVersionUID = -1417573901424958954L;

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
     * @throws IllegalArgumentException if {@code timePeriod} is non-null and not
     *                                  positive
     * @since 0.22.2
     */
    public TimeBarBuilderFactory(final Duration timePeriod, final boolean realtimeBars) {
        if (timePeriod != null && (timePeriod.isNegative() || timePeriod.isZero())) {
            throw new IllegalArgumentException("timePeriod must be positive");
        }
        this.timePeriod = timePeriod;
        this.realtimeBars = realtimeBars;
    }

    @Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        if (timePeriod != null && (timePeriod.isNegative() || timePeriod.isZero())) {
            throw new InvalidObjectException("timePeriod must be positive");
        }
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
