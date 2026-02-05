/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Cross indicator.
 *
 * <p>
 * Boolean indicator that monitors the crossing of two indicators.
 */
public class CrossIndicator extends CachedIndicator<Boolean> {

    /** Upper indicator */
    private final Indicator<Num> up;

    /** Lower indicator */
    private final Indicator<Num> low;

    /**
     * Constructor.
     *
     * @param up  the upper indicator
     * @param low the lower indicator
     */
    public CrossIndicator(Indicator<Num> up, Indicator<Num> low) {
        // TODO: check if up series is equal to low series
        super(up);
        this.up = up;
        this.low = low;
    }

    @Override
    protected Boolean calculate(int index) {

        int i = index;
        if (i == 0 || up.getValue(i).isGreaterThanOrEqual(low.getValue(i))) {
            return false;
        }

        do {
            i--;
        } while (i > 0 && up.getValue(i).isEqual(low.getValue(i)));

        return up.getValue(i).isGreaterThan(low.getValue(i));
    }

    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(up.getCountOfUnstableBars(), low.getCountOfUnstableBars());
        return Math.max(1, unstableBars);
    }

    /** @return the initial lower indicator */
    public Indicator<Num> getLow() {
        return low;
    }

    /** @return the initial upper indicator */
    public Indicator<Num> getUp() {
        return up;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + low + " " + up;
    }
}
