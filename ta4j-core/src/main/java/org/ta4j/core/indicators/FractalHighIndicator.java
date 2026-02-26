/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Bill Williams Fractal High confirmation indicator.
 * <p>
 * A fractal high is confirmed when a candidate bar's high is strictly greater
 * than the highs in the configured look-back and look-forward windows.
 * <p>
 * To avoid look-ahead bias, this indicator emits {@code true} on the
 * confirmation bar (the current index), not on the candidate pivot bar. The
 * corresponding pivot index can be obtained through
 * {@link #getConfirmedFractalIndex(int)}.
 * <p>
 * Default settings are {@code 2} preceding bars and {@code 2} following bars,
 * matching Bill Williams' classic fractal definition from Trading Chaos.
 * <p>
 * Interaction note: many Bill Williams systems pair fractal breakouts with the
 * {@link AlligatorIndicator} and only consider long setups when the confirmed
 * fractal is above the alligator teeth.
 *
 * @see FractalLowIndicator
 * @see AlligatorIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/f/fractal.asp">Investopedia: Fractal
 *      Indicator</a>
 * @since 0.22.3
 */
public class FractalHighIndicator extends AbstractFractalConfirmationIndicator {

    /** Classic Bill Williams preceding-bar count. */
    public static final int DEFAULT_PRECEDING_BARS = 2;

    /** Classic Bill Williams following-bar count. */
    public static final int DEFAULT_FOLLOWING_BARS = 2;

    /**
     * Constructor.
     *
     * @param indicator     source indicator, typically {@link HighPriceIndicator}
     * @param precedingBars bars that must be strictly lower before the pivot
     * @param followingBars bars that must be strictly lower after the pivot
     * @throws IllegalArgumentException if {@code indicator} is {@code null},
     *                                  {@code precedingBars < 1}, or
     *                                  {@code followingBars < 1}
     * @since 0.22.3
     */
    public FractalHighIndicator(Indicator<Num> indicator, int precedingBars, int followingBars) {
        super(indicator, precedingBars, followingBars);
    }

    /**
     * Constructor with classic Bill Williams defaults:
     * <ul>
     * <li>{@code precedingBars} = 2</li>
     * <li>{@code followingBars} = 2</li>
     * </ul>
     *
     * @param indicator source indicator, typically {@link HighPriceIndicator}
     * @since 0.22.3
     */
    public FractalHighIndicator(Indicator<Num> indicator) {
        this(indicator, DEFAULT_PRECEDING_BARS, DEFAULT_FOLLOWING_BARS);
    }

    /**
     * Constructor using series highs with configurable windows.
     *
     * @param series        the series
     * @param precedingBars bars that must be strictly lower before the pivot
     * @param followingBars bars that must be strictly lower after the pivot
     * @since 0.22.3
     */
    public FractalHighIndicator(BarSeries series, int precedingBars, int followingBars) {
        this(new HighPriceIndicator(series), precedingBars, followingBars);
    }

    /**
     * Constructor using series highs and classic Bill Williams defaults.
     *
     * @param series the series
     * @since 0.22.3
     */
    public FractalHighIndicator(BarSeries series) {
        this(series, DEFAULT_PRECEDING_BARS, DEFAULT_FOLLOWING_BARS);
    }

    @Override
    protected FractalDetectionHelper.Direction direction() {
        return FractalDetectionHelper.Direction.HIGH;
    }
}
