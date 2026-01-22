/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.time.Instant;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * DateTime indicator.
 *
 * <p>
 * Returns a {@link Instant} of (or for) a bar.
 */
public class DateTimeIndicator extends CachedIndicator<Instant> {

    private final Function<Bar, Instant> action;

    /**
     * Constructor to return {@link Bar#getBeginTime()} of a bar.
     *
     * @param barSeries the bar series
     */
    public DateTimeIndicator(BarSeries barSeries) {
        this(barSeries, Bar::getBeginTime);
    }

    /**
     * Constructor.
     *
     * @param barSeries the bar series
     * @param action    the action
     */
    public DateTimeIndicator(BarSeries barSeries, Function<Bar, Instant> action) {
        super(barSeries);
        this.action = action;
    }

    @Override
    protected Instant calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        return this.action.apply(bar);
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
