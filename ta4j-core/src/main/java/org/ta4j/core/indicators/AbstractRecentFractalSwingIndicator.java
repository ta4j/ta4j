/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Shared base for recent fractal swing indicators.
 * <p>
 * This base centralizes latest-swing scanning via
 * {@link FractalDetectionHelper#findLatestConfirmedFractalIndex(Indicator, org.ta4j.core.BarSeries, int, int, int, int, FractalDetectionHelper.Direction)}
 * while subclasses define direction and local parameter naming. A configured
 * following window of zero still requires one dominated bar so a returned pivot
 * is confirmed and cannot be invalidated by an equal or more extreme next bar.
 */
abstract class AbstractRecentFractalSwingIndicator extends AbstractRecentSwingIndicator {

    private final transient Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator    source price indicator
     * @param unstableBars unstable-bar count contributed by local window settings
     */
    protected AbstractRecentFractalSwingIndicator(Indicator<Num> indicator, int unstableBars) {
        super(indicator, unstableBars);
        this.indicator = indicator;
    }

    /**
     * Validates window configuration and returns the corresponding unstable-bar
     * count.
     *
     * @param precedingBars             required dominating bars before the
     *                                  candidate
     * @param followingBars             required dominating bars after the candidate
     * @param allowedEqualBars          maximum additional equal bars in a plateau
     * @param precedingBarsArgumentName argument label for preceding bars
     * @param followingBarsArgumentName argument label for following bars
     * @return unstable-bar contribution
     * @throws IllegalArgumentException if window values are invalid
     */
    protected static int validateWindowConfiguration(int precedingBars, int followingBars, int allowedEqualBars,
            String precedingBarsArgumentName, String followingBarsArgumentName) {
        if (precedingBars < 1) {
            throw new IllegalArgumentException(precedingBarsArgumentName + " must be greater than 0");
        }
        if (followingBars < 0) {
            throw new IllegalArgumentException(followingBarsArgumentName + " must be 0 or greater");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        return precedingBars + Math.max(1, followingBars);
    }

    @Override
    protected final int detectLatestSwingIndex(int index) {
        return FractalDetectionHelper.findLatestConfirmedFractalIndex(indicator, getBarSeries(), index, precedingBars(),
                Math.max(1, followingBars()), allowedEqualBars(), direction());
    }

    /**
     * @return number of required dominating preceding bars
     */
    protected abstract int precedingBars();

    /**
     * @return number of required dominating following bars
     */
    protected abstract int followingBars();

    /**
     * @return maximum additional equal bars permitted in a plateau
     */
    protected abstract int allowedEqualBars();

    /**
     * @return fractal direction
     */
    protected abstract FractalDetectionHelper.Direction direction();
}
