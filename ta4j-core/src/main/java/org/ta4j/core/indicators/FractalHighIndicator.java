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
public class FractalHighIndicator extends CachedIndicator<Boolean> {

    /** Classic Bill Williams preceding-bar count. */
    public static final int DEFAULT_PRECEDING_BARS = 2;

    /** Classic Bill Williams following-bar count. */
    public static final int DEFAULT_FOLLOWING_BARS = 2;

    private final Indicator<Num> indicator;
    private final int precedingBars;
    private final int followingBars;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator     source indicator, typically {@link HighPriceIndicator}
     * @param precedingBars bars that must be strictly lower before the pivot
     * @param followingBars bars that must be strictly lower after the pivot
     * @since 0.22.3
     */
    public FractalHighIndicator(Indicator<Num> indicator, int precedingBars, int followingBars) {
        super(indicator);
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
    protected Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return Boolean.FALSE;
        }

        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        final int candidateIndex = index - followingBars;
        if (candidateIndex - precedingBars < beginIndex) {
            return Boolean.FALSE;
        }

        final Num candidateValue = indicator.getValue(candidateIndex);
        if (isInvalid(candidateValue)) {
            return Boolean.FALSE;
        }

        for (int i = 1; i <= precedingBars; i++) {
            final Num previousValue = indicator.getValue(candidateIndex - i);
            if (isInvalid(previousValue) || !candidateValue.isGreaterThan(previousValue)) {
                return Boolean.FALSE;
            }
        }

        for (int i = 1; i <= followingBars; i++) {
            final Num nextValue = indicator.getValue(candidateIndex + i);
            if (isInvalid(nextValue) || !candidateValue.isGreaterThan(nextValue)) {
                return Boolean.FALSE;
            }
        }

        return Boolean.TRUE;
    }

    /**
     * Returns the pivot index confirmed at {@code index}.
     *
     * @param index current bar index
     * @return confirmed fractal pivot index, or {@code -1} when no confirmation
     *         occurs at {@code index}
     * @since 0.22.3
     */
    public int getConfirmedFractalIndex(int index) {
        return Boolean.TRUE.equals(getValue(index)) ? index - followingBars : -1;
    }

    /**
     * @return source indicator used for high comparisons
     * @since 0.22.3
     */
    public Indicator<Num> getPriceIndicator() {
        return indicator;
    }

    /** @return the number of bars required before confirmations can appear */
    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    private static boolean isInvalid(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
