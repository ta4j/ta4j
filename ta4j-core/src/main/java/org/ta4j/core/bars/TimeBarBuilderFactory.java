/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
