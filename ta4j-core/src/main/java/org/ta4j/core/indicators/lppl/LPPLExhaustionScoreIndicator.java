/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Numeric Log-Periodic Power Law (LPPL) exhaustion score in the range
 * {@code [-1, 1]}.
 *
 * <p>
 * Positive values represent crash exhaustion. Negative values represent bubble
 * exhaustion. Warm-up bars return {@link org.ta4j.core.num.NaN#NaN}.
 *
 * @since 0.22.9
 */
public final class LPPLExhaustionScoreIndicator extends CachedIndicator<Num> {

    private final LPPLExhaustionIndicator exhaustionIndicator;

    /**
     * Creates a score indicator over close prices using default calibration
     * settings.
     *
     * @param series underlying bar series
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(BarSeries series) {
        this(new LPPLExhaustionIndicator(series));
    }

    /**
     * Creates a score indicator over close prices using an explicit calibration
     * profile.
     *
     * @param series  underlying bar series
     * @param profile calibration profile
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(BarSeries series, LPPLCalibrationProfile profile) {
        this(new LPPLExhaustionIndicator(series, profile));
    }

    /**
     * Creates a score indicator using the supplied price source and default
     * calibration settings.
     *
     * @param priceIndicator price source to model
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(Indicator<Num> priceIndicator) {
        this(new LPPLExhaustionIndicator(priceIndicator));
    }

    /**
     * Creates a score indicator using the supplied price source and explicit
     * calibration profile.
     *
     * @param priceIndicator price source to model
     * @param profile        calibration profile
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(Indicator<Num> priceIndicator, LPPLCalibrationProfile profile) {
        this(new LPPLExhaustionIndicator(priceIndicator, profile));
    }

    /**
     * Creates a score indicator from a rich LPPL exhaustion indicator.
     *
     * @param exhaustionIndicator rich LPPL exhaustion source
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(LPPLExhaustionIndicator exhaustionIndicator) {
        super(copyOf(exhaustionIndicator));
        this.exhaustionIndicator = copyOf(exhaustionIndicator);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN;
        }
        return exhaustionIndicator.getValue(index).score();
    }

    /**
     * @return unstable bar count from the wrapped rich LPPL exhaustion indicator
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return exhaustionIndicator.getCountOfUnstableBars();
    }

    /**
     * @return rich LPPL exhaustion indicator used by this numeric projection
     * @since 0.22.9
     */
    public LPPLExhaustionIndicator getExhaustionIndicator() {
        return copyOf(exhaustionIndicator);
    }

    private static LPPLExhaustionIndicator copyOf(LPPLExhaustionIndicator indicator) {
        LPPLExhaustionIndicator source = Objects.requireNonNull(indicator, "exhaustionIndicator");
        return new LPPLExhaustionIndicator(source.getPriceIndicator(), source.getProfile());
    }
}
