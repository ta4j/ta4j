/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Calibrates LPPL on a trailing window ending at {@code index - 1} and returns
 * rich diagnostics for the one-step evaluation at {@code index}.
 *
 * <p>
 * Use {@link LPPLIndicator} for the ordinary numeric indicator path.
 *
 * @since 0.23.1
 */
public final class LPPLFitIndicator extends CachedIndicator<LPPLFit> {

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
    private final transient LPPLFitCalibrator calibrator;

    /**
     * Creates a causal LPPL fit indicator over close prices using default settings.
     *
     * @param series underlying bar series
     * @since 0.23.1
     */
    public LPPLFitIndicator(BarSeries series) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")));
    }

    /**
     * Creates a causal LPPL fit indicator over close prices.
     *
     * @param series  underlying bar series
     * @param profile calibration profile
     * @since 0.23.1
     */
    public LPPLFitIndicator(BarSeries series, LPPLCalibrationProfile profile) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), profile);
    }

    /**
     * Creates a causal LPPL fit indicator over a custom price source using default
     * settings.
     *
     * @param priceIndicator positive price source
     * @since 0.23.1
     */
    public LPPLFitIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, LPPLCalibrationProfile.defaults());
    }

    /**
     * Creates a causal LPPL fit indicator over a custom price source.
     *
     * @param priceIndicator positive price source
     * @param profile        calibration profile
     * @since 0.23.1
     */
    public LPPLFitIndicator(Indicator<Num> priceIndicator, LPPLCalibrationProfile profile) {
        this(priceIndicator, Objects.requireNonNull(profile, "profile").window(), profile.minM(), profile.maxM(),
                profile.mSteps(), profile.minOmega(), profile.maxOmega(), profile.omegaSteps(),
                profile.minCriticalOffset(), profile.maxCriticalOffset(), profile.criticalOffsetStep(),
                profile.maxEvaluations(), profile.minRSquared());
    }

    LPPLFitIndicator(Indicator<Num> priceIndicator, int window, double minM, double maxM, int mSteps,
            double minOmega, double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset,
            int criticalOffsetStep, int maxEvaluations, double minRSquared) {
        super(requireSeries(priceIndicator));
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.profile = new LPPLCalibrationProfile(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps,
                minCriticalOffset, maxCriticalOffset, criticalOffsetStep, maxEvaluations, minRSquared);
        this.window = this.profile.window();
        this.minM = this.profile.minM();
        this.maxM = this.profile.maxM();
        this.mSteps = this.profile.mSteps();
        this.minOmega = this.profile.minOmega();
        this.maxOmega = this.profile.maxOmega();
        this.omegaSteps = this.profile.omegaSteps();
        this.minCriticalOffset = this.profile.minCriticalOffset();
        this.maxCriticalOffset = this.profile.maxCriticalOffset();
        this.criticalOffsetStep = this.profile.criticalOffsetStep();
        this.maxEvaluations = this.profile.maxEvaluations();
        this.minRSquared = this.profile.minRSquared();
        this.calibrator = new LPPLFitCalibrator(this.profile);
    }

    private static BarSeries requireSeries(Indicator<Num> priceIndicator) {
        BarSeries series = Objects.requireNonNull(priceIndicator, "priceIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("priceIndicator must expose a backing BarSeries");
        }
        return series;
    }

    @Override
    protected LPPLFit calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return LPPLFit.invalid(window, LPPLFitStatus.INSUFFICIENT_DATA);
        }
        double[] trainingLogPrices = new double[window];
        int startIndex = index - window;
        for (int i = 0; i < window; i++) {
            double logPrice = logPrice(startIndex + i);
            if (!Double.isFinite(logPrice)) {
                return LPPLFit.invalid(window, LPPLFitStatus.INVALID_INPUT);
            }
            trainingLogPrices[i] = logPrice;
        }
        double evaluationLogPrice = logPrice(index);
        if (!Double.isFinite(evaluationLogPrice)) {
            return LPPLFit.invalid(window, LPPLFitStatus.INVALID_INPUT);
        }
        return calibrator.fit(trainingLogPrices, evaluationLogPrice);
    }

    private double logPrice(int index) {
        Num value = priceIndicator.getValue(index);
        if (Num.isNaNOrNull(value)) {
            return Double.NaN;
        }
        double price = value.doubleValue();
        return Double.isFinite(price) && price > 0.0 ? Math.log(price) : Double.NaN;
    }

    /**
     * @return source unstable bars plus the trailing calibration window
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return priceIndicator.getCountOfUnstableBars() + window;
    }

    /**
     * @return source price indicator
     * @since 0.23.1
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return immutable calibration profile
     * @since 0.23.1
     */
    public LPPLCalibrationProfile getProfile() {
        return profile;
    }
}
