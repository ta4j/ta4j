/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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

    private final Indicator<Num> priceIndicator;
    private final int[] windows;
    private final double minM;
    private final double maxM;
    private final int mSteps;
    private final double minOmega;
    private final double maxOmega;
    private final int omegaSteps;
    private final int minCriticalOffset;
    private final int maxCriticalOffset;
    private final int criticalOffsetStep;
    private final int activeMinCriticalOffset;
    private final int activeMaxCriticalOffset;
    private final int maxEvaluations;
    private final double minRSquared;
    private final transient Indicator<LPPLExhaustion> exhaustionIndicator;

    /**
     * Creates a score indicator over close prices using default calibration
     * settings.
     *
     * @param series underlying bar series
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(BarSeries series) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")));
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
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), profile);
    }

    /**
     * Creates a score indicator using the supplied price source and default
     * calibration settings.
     *
     * @param priceIndicator price source to model
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, LPPLCalibrationProfile.defaults());
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
        this(priceIndicator, Objects.requireNonNull(profile, "profile").windows(), profile.minM(), profile.maxM(),
                profile.mSteps(), profile.minOmega(), profile.maxOmega(), profile.omegaSteps(),
                profile.minCriticalOffset(), profile.maxCriticalOffset(), profile.criticalOffsetStep(),
                profile.activeMinCriticalOffset(), profile.activeMaxCriticalOffset(), profile.maxEvaluations(),
                profile.minRSquared());
    }

    /**
     * Creates a score indicator from a rich LPPL exhaustion indicator.
     *
     * @param exhaustionIndicator rich LPPL exhaustion source
     * @since 0.22.9
     */
    public LPPLExhaustionScoreIndicator(LPPLExhaustionIndicator exhaustionIndicator) {
        this(Objects.requireNonNull(exhaustionIndicator, "exhaustionIndicator"), exhaustionIndicator.getProfile());
    }

    LPPLExhaustionScoreIndicator(Indicator<Num> priceIndicator, int[] windows, double minM, double maxM, int mSteps,
            double minOmega, double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset,
            int criticalOffsetStep, int activeMinCriticalOffset, int activeMaxCriticalOffset, int maxEvaluations,
            double minRSquared) {
        this(new LPPLExhaustionIndicator(priceIndicator, windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps,
                minCriticalOffset, maxCriticalOffset, criticalOffsetStep, activeMinCriticalOffset,
                activeMaxCriticalOffset, maxEvaluations, minRSquared));
    }

    private LPPLExhaustionScoreIndicator(LPPLExhaustionIndicator exhaustionIndicator, LPPLCalibrationProfile profile) {
        super(exhaustionIndicator.getPriceIndicator());
        this.priceIndicator = exhaustionIndicator.getPriceIndicator();
        this.windows = profile.windows();
        this.minM = profile.minM();
        this.maxM = profile.maxM();
        this.mSteps = profile.mSteps();
        this.minOmega = profile.minOmega();
        this.maxOmega = profile.maxOmega();
        this.omegaSteps = profile.omegaSteps();
        this.minCriticalOffset = profile.minCriticalOffset();
        this.maxCriticalOffset = profile.maxCriticalOffset();
        this.criticalOffsetStep = profile.criticalOffsetStep();
        this.activeMinCriticalOffset = profile.activeMinCriticalOffset();
        this.activeMaxCriticalOffset = profile.activeMaxCriticalOffset();
        this.maxEvaluations = profile.maxEvaluations();
        this.minRSquared = profile.minRSquared();
        this.exhaustionIndicator = exhaustionIndicator;
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

}
