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
 * Causal normalized LPPL prediction residual in the range {@code [-1, 1]}.
 *
 * <p>
 * At index {@code t}, the model is calibrated only through {@code t - 1}. The
 * returned value is the observed-minus-predicted log-price residual at
 * {@code t}, normalized by the maximum absolute residual across that fixed
 * fitted trajectory. Positive values are above the LPPL path and negative
 * values are below it. The value is not, by itself, a valuation judgment or a
 * crash forecast.
 *
 * <p>
 * Warm-up bars, invalid prices, failed optimizations, and fits that do not pass
 * the profile's quality filters return {@link org.ta4j.core.num.NaN#NaN}.
 *
 * @since 0.23.1
 */
public final class LPPLResidualIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> priceIndicator;
    private final int window;
    private final double minM;
    private final double maxM;
    private final int mSteps;
    private final double minOmega;
    private final double maxOmega;
    private final int omegaSteps;
    private final int minCriticalOffset;
    private final int maxCriticalOffset;
    private final int criticalOffsetStep;
    private final int maxEvaluations;
    private final double minRSquared;
    private final transient LPPLCalibrationProfile profile;
    private final transient Indicator<LPPLFit> fitIndicator;

    /**
     * Creates an LPPL residual over close prices using default settings.
     *
     * @param series underlying bar series
     * @since 0.23.1
     */
    public LPPLResidualIndicator(BarSeries series) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")));
    }

    /**
     * Creates an LPPL residual over close prices.
     *
     * @param series  underlying bar series
     * @param profile calibration profile
     * @since 0.23.1
     */
    public LPPLResidualIndicator(BarSeries series, LPPLCalibrationProfile profile) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), profile);
    }

    /**
     * Creates an LPPL residual over a custom positive price source using default
     * settings.
     *
     * @param priceIndicator positive price source
     * @since 0.23.1
     */
    public LPPLResidualIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, LPPLCalibrationProfile.defaults());
    }

    /**
     * Creates an LPPL residual over a custom positive price source.
     *
     * @param priceIndicator positive price source
     * @param profile        calibration profile
     * @since 0.23.1
     */
    public LPPLResidualIndicator(Indicator<Num> priceIndicator, LPPLCalibrationProfile profile) {
        this(priceIndicator, Objects.requireNonNull(profile, "profile").window(), profile.minM(), profile.maxM(),
                profile.mSteps(), profile.minOmega(), profile.maxOmega(), profile.omegaSteps(),
                profile.minCriticalOffset(), profile.maxCriticalOffset(), profile.criticalOffsetStep(),
                profile.maxEvaluations(), profile.minRSquared());
    }

    LPPLResidualIndicator(Indicator<Num> priceIndicator, int window, double minM, double maxM, int mSteps,
            double minOmega, double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset,
            int criticalOffsetStep, int maxEvaluations, double minRSquared) {
        this(new LPPLFitIndicator(priceIndicator, window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps,
                minCriticalOffset, maxCriticalOffset, criticalOffsetStep, maxEvaluations, minRSquared));
    }

    private LPPLResidualIndicator(LPPLFitIndicator fitIndicator) {
        super(fitIndicator.getPriceIndicator());
        this.fitIndicator = fitIndicator;
        this.priceIndicator = fitIndicator.getPriceIndicator();
        this.profile = fitIndicator.getProfile();
        this.window = profile.window();
        this.minM = profile.minM();
        this.maxM = profile.maxM();
        this.mSteps = profile.mSteps();
        this.minOmega = profile.minOmega();
        this.maxOmega = profile.maxOmega();
        this.omegaSteps = profile.omegaSteps();
        this.minCriticalOffset = profile.minCriticalOffset();
        this.maxCriticalOffset = profile.maxCriticalOffset();
        this.criticalOffsetStep = profile.criticalOffsetStep();
        this.maxEvaluations = profile.maxEvaluations();
        this.minRSquared = profile.minRSquared();
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN;
        }
        LPPLFit fit = fitIndicator.getValue(index);
        if (!fit.isQualified(profile)) {
            return NaN;
        }
        return getBarSeries().numFactory().numOf(fit.normalizedResidual());
    }

    /**
     * @return source price indicator
     * @since 0.23.1
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * Returns the diagnostic indicator used by this residual indicator.
     *
     * <p>
     * The returned view delegates to the exact cached component used by
     * {@link #getValue(int)}. Reuse it when both the numeric residual and fit
     * diagnostics are needed to avoid calibrating the same index twice. The view
     * does not expose the mutable cache itself.
     *
     * @return backing LPPL fit indicator
     * @since 0.23.1
     */
    public Indicator<LPPLFit> getFitIndicator() {
        return new Indicator<>() {

            @Override
            public LPPLFit getValue(int index) {
                return fitIndicator.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return fitIndicator.getCountOfUnstableBars();
            }

            @Override
            public BarSeries getBarSeries() {
                return fitIndicator.getBarSeries();
            }
        };
    }

    /**
     * @return immutable calibration profile
     * @since 0.23.1
     */
    public LPPLCalibrationProfile getProfile() {
        return profile;
    }

    /**
     * @return unstable bar count from the underlying causal fit indicator
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return fitIndicator.getCountOfUnstableBars();
    }
}
