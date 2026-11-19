/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
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
    private final transient int unstableBars;
    private transient volatile Windows windows;

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
        this(validatedConfig(indicator, precedingBars, followingBars));
    }

    private AbstractFractalConfirmationIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.precedingBars = config.precedingBars();
        this.followingBars = config.followingBars();
        this.unstableBars = config.unstableBars();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int precedingBars, int followingBars) {
        Indicator<Num> validatedIndicator = IndicatorUtils.requireIndicator(indicator, "indicator");
        if (precedingBars < 1) {
            throw new IllegalArgumentException("precedingBars must be greater than 0");
        }
        if (followingBars < 1) {
            throw new IllegalArgumentException("followingBars must be greater than 0");
        }
        int unstableBars = validatedIndicator.getCountOfUnstableBars() + precedingBars + followingBars;
        return new Config(validatedIndicator, precedingBars, followingBars, unstableBars);
    }

    @Override
    protected final Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return Boolean.FALSE;
        }
        long windowLength = (long) precedingBars + followingBars + 1;
        if (windowLength > Integer.MAX_VALUE) {
            return FractalDetectionHelper.isConfirmedFractal(indicator, getBarSeries(), index - followingBars,
                    precedingBars, followingBars, index, 0, direction());
        }
        int candidateIndex = index - followingBars;
        if ((long) candidateIndex - precedingBars < getBarSeries().getBeginIndex()) {
            return Boolean.FALSE;
        }
        FractalDetectionHelper.Direction direction = direction();
        if (direction == null) {
            return Boolean.FALSE;
        }
        Windows active = windows(direction, (int) windowLength);
        if (!active.nonFinite().getValue(index).isZero()) {
            return Boolean.FALSE;
        }
        Num candidate = indicator.getValue(candidateIndex);
        return direction.isCandidateDominant(candidate, active.preceding().getValue(candidateIndex - 1))
                && direction.isCandidateDominant(candidate, active.following().getValue(index));
    }

    private Windows windows(FractalDetectionHelper.Direction direction, int windowLength) {
        Windows current = windows;
        if (current != null && current.direction() == direction) {
            return current;
        }
        synchronized (this) {
            current = windows;
            if (current == null || current.direction() != direction) {
                Indicator<Num> nonFinite = new CachedIndicator<Num>(indicator) {
                    @Override
                    protected Num calculate(int index) {
                        var factory = getBarSeries().numFactory();
                        return Num.isFinite(indicator.getValue(index)) ? factory.zero() : factory.one();
                    }

                    @Override
                    public int getCountOfUnstableBars() {
                        return indicator.getCountOfUnstableBars();
                    }
                };
                Indicator<Num> preceding = direction == FractalDetectionHelper.Direction.HIGH
                        ? new HighestValueIndicator(indicator, precedingBars)
                        : new LowestValueIndicator(indicator, precedingBars);
                Indicator<Num> following = direction == FractalDetectionHelper.Direction.HIGH
                        ? new HighestValueIndicator(indicator, followingBars)
                        : new LowestValueIndicator(indicator, followingBars);
                current = new Windows(direction, preceding, following,
                        new HighestValueIndicator(nonFinite, windowLength));
                windows = current;
            }
        }
        return current;
    }

    private record Windows(FractalDetectionHelper.Direction direction, Indicator<Num> preceding,
            Indicator<Num> following, Indicator<Num> nonFinite) {
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

    private record Config(Indicator<Num> indicator, int precedingBars, int followingBars, int unstableBars) {
    }
}
