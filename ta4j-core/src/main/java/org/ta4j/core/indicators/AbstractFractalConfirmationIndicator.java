/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Shared base for confirmation-based fractal indicators.
 * <p>
 * Concrete implementations provide only the directional comparison semantics
 * ({@link FractalDetectionHelper.Direction#HIGH} or
 * {@link FractalDetectionHelper.Direction#LOW}); this base class centralizes:
 * <ul>
 * <li>window validation</li>
 * <li>warm-up/unstable-bar handling</li>
 * <li>confirmation-bar evaluation (without look-ahead bias)</li>
 * <li>pivot-index mapping via {@link #getConfirmedFractalIndex(int)}</li>
 * </ul>
 */
abstract class AbstractFractalConfirmationIndicator extends CachedIndicator<Boolean> {

    private final Indicator<Num> indicator;
    private final int precedingBars;
    private final int followingBars;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator     source price indicator
     * @param precedingBars bars that must be dominated before the pivot
     * @param followingBars bars that must be dominated after the pivot
     * @throws IllegalArgumentException if {@code indicator} is {@code null},
     *                                  {@code precedingBars < 1}, or
     *                                  {@code followingBars < 1}
     */
    protected AbstractFractalConfirmationIndicator(Indicator<Num> indicator, int precedingBars, int followingBars) {
        super(IndicatorUtils.requireIndicator(indicator, "indicator"));
        if (precedingBars < 1) {
            throw new IllegalArgumentException("precedingBars must be greater than 0");
        }
        if (followingBars < 1) {
            throw new IllegalArgumentException("followingBars must be greater than 0");
        }
        this.indicator = indicator;
        this.precedingBars = precedingBars;
        this.followingBars = followingBars;
        this.unstableBars = indicator.getCountOfUnstableBars() + precedingBars + followingBars;
    }

    @Override
    protected final Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return Boolean.FALSE;
        }
        final int candidateIndex = index - followingBars;
        return FractalDetectionHelper.isConfirmedFractal(indicator, getBarSeries(), candidateIndex, precedingBars,
                followingBars, index, 0, direction());
    }

    /**
     * Returns the pivot index confirmed at {@code index}.
     *
     * @param index current bar index
     * @return confirmed fractal pivot index, or {@code -1} when no confirmation
     *         occurs at {@code index}
     */
    public int getConfirmedFractalIndex(int index) {
        return Boolean.TRUE.equals(getValue(index)) ? index - followingBars : -1;
    }

    /**
     * @return source indicator used for fractal comparisons
     */
    public Indicator<Num> getPriceIndicator() {
        return indicator;
    }

    @Override
    public final int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * Returns the directional dominance rule used for confirmation.
     *
     * @return fractal direction
     */
    protected abstract FractalDetectionHelper.Direction direction();
}
