/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Num indicator.
 *
 * <p>
 * Returns a {@link Num} of (or for) a bar.
 *
 * <p>
 * <b>Hint:</b> It is recommended to use the {@code NumIndicator} with its
 * {@link #action} mainly for complex functions and not for simple get
 * functions. For simple get functions it might be better to use the
 * corresponding indicator due to better readability and complexity (e.g. to get
 * the close price just use the {@code ClosePriceIndicator} instead of the
 * {@code NumIndicator}).
 */
public class NumIndicator extends CachedIndicator<Num> {

    /** The action to calculate or determine a num on the bar. */
    private final Function<Bar, Num> action;

    /**
     * Constructor.
     *
     * @param barSeries the bar series
     * @param action    the action to calculate or determine a num on the bar
     */
    public NumIndicator(BarSeries barSeries, Function<Bar, Num> action) {
        super(barSeries);
        this.action = action;
    }

    @Override
    protected Num calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        return this.action.apply(bar);
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
