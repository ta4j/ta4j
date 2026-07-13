/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Detects Log-Periodic Power Law (LPPL) crash and bubble exhaustion over
 * rolling price windows.
 *
 * <p>
 * This indicator fits the log-periodic power-law singularity (LPPLS) model to
 * rolling log-price windows. It returns a rich {@link LPPLExhaustion} snapshot
 * containing the dominant side, bounded score, fit quality, and per-window fit
 * diagnostics. Use {@link LPPLExhaustionScoreIndicator} when only a numeric
 * oscillator-like output is needed.
 *
 * @since 0.23.1
 */
public final class LPPLExhaustionIndicator extends CachedIndicator<LPPLExhaustion> {

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
    private final transient LPPLCalibrationProfile profile;
    private final transient LPPLFitCalibrator calibrator;

    /**
     * Creates an LPPL exhaustion indicator over close prices using default
     * calibration settings.
     *
     * @param series underlying bar series
     * @since 0.23.1
     */
    public LPPLExhaustionIndicator(BarSeries series) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")));
    }

    /**
     * Creates an LPPL exhaustion indicator over close prices using an explicit
     * calibration profile.
     *
     * @param series  underlying bar series
     * @param profile calibration profile
     * @since 0.23.1
     */
    public LPPLExhaustionIndicator(BarSeries series, LPPLCalibrationProfile profile) {
        this(new ClosePriceIndicator(Objects.requireNonNull(series, "series")), profile);
    }

    /**
     * Creates an LPPL exhaustion indicator using the supplied price source and
     * default calibration settings.
     *
     * @param priceIndicator price source to model
     * @since 0.23.1
     */
    public LPPLExhaustionIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, LPPLCalibrationProfile.defaults());
    }

    /**
     * Creates an LPPL exhaustion indicator using an explicit calibration profile.
     *
     * @param priceIndicator price source to model
     * @param profile        calibration profile
     * @since 0.23.1
     */
    public LPPLExhaustionIndicator(Indicator<Num> priceIndicator, LPPLCalibrationProfile profile) {
        this(priceIndicator, Objects.requireNonNull(profile, "profile").windows(), profile.minM(), profile.maxM(),
                profile.mSteps(), profile.minOmega(), profile.maxOmega(), profile.omegaSteps(),
                profile.minCriticalOffset(), profile.maxCriticalOffset(), profile.criticalOffsetStep(),
                profile.activeMinCriticalOffset(), profile.activeMaxCriticalOffset(), profile.maxEvaluations(),
                profile.minRSquared());
    }

    /**
     * Creates an LPPL exhaustion indicator with serializable primitive profile
     * fields.
     *
     * @param priceIndicator          price source to model
     * @param windows                 rolling fit windows in bars
     * @param minM                    lower bound for {@code m}
     * @param maxM                    upper bound for {@code m}
     * @param mSteps                  grid steps for {@code m}
     * @param minOmega                lower bound for {@code omega}
     * @param maxOmega                upper bound for {@code omega}
     * @param omegaSteps              grid steps for {@code omega}
     * @param minCriticalOffset       minimum searched critical-time offset
     * @param maxCriticalOffset       maximum searched critical-time offset
     * @param criticalOffsetStep      grid step for critical-time offsets
     * @param activeMinCriticalOffset minimum actionable critical-time offset
     * @param activeMaxCriticalOffset maximum actionable critical-time offset
     * @param maxEvaluations          optimizer evaluation budget
     * @param minRSquared             minimum structurally qualified fit quality
     * @since 0.23.1
     */
    LPPLExhaustionIndicator(Indicator<Num> priceIndicator, int[] windows, double minM, double maxM, int mSteps,
            double minOmega, double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset,
            int criticalOffsetStep, int activeMinCriticalOffset, int activeMaxCriticalOffset, int maxEvaluations,
            double minRSquared) {
        super(requireSeries(priceIndicator));
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.profile = new LPPLCalibrationProfile(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps,
                minCriticalOffset, maxCriticalOffset, criticalOffsetStep, activeMinCriticalOffset,
                activeMaxCriticalOffset, maxEvaluations, minRSquared);
        this.windows = this.profile.windows();
        this.minM = this.profile.minM();
        this.maxM = this.profile.maxM();
        this.mSteps = this.profile.mSteps();
        this.minOmega = this.profile.minOmega();
        this.maxOmega = this.profile.maxOmega();
        this.omegaSteps = this.profile.omegaSteps();
        this.minCriticalOffset = this.profile.minCriticalOffset();
        this.maxCriticalOffset = this.profile.maxCriticalOffset();
        this.criticalOffsetStep = this.profile.criticalOffsetStep();
        this.activeMinCriticalOffset = this.profile.activeMinCriticalOffset();
        this.activeMaxCriticalOffset = this.profile.activeMaxCriticalOffset();
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
    protected LPPLExhaustion calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return neutral(LPPLExhaustionStatus.INSUFFICIENT_DATA);
        }

        List<LPPLFit> fits = new ArrayList<>(windows.length);
        for (int window : windows) {
            double[] logPrices = extractLogPrices(index, window);
            if (logPrices.length == 0) {
                fits.add(LPPLFit.invalid(window, LPPLExhaustionStatus.INVALID_INPUT));
                continue;
            }
            fits.add(calibrator.fit(logPrices));
        }
        return aggregate(fits);
    }

    private double[] extractLogPrices(int index, int window) {
        int startIndex = index - window + 1;
        if (startIndex < getBarSeries().getBeginIndex()) {
            return new double[0];
        }
        double[] values = new double[window];
        for (int i = 0; i < window; i++) {
            Num value = priceIndicator.getValue(startIndex + i);
            if (Num.isNaNOrNull(value)) {
                return new double[0];
            }
            double price = value.doubleValue();
            if (!Double.isFinite(price) || price <= 0.0) {
                return new double[0];
            }
            values[i] = Math.log(price);
        }
        return values;
    }

    private LPPLExhaustion aggregate(List<LPPLFit> fits) {
        List<LPPLFit> actionable = fits.stream().filter(fit -> fit.isActionable(profile)).toList();
        int crashFits = (int) actionable.stream()
                .filter(fit -> fit.side() == LPPLExhaustionSide.CRASH_EXHAUSTION)
                .count();
        int bubbleFits = (int) actionable.stream()
                .filter(fit -> fit.side() == LPPLExhaustionSide.BUBBLE_EXHAUSTION)
                .count();
        if (actionable.isEmpty() || crashFits == bubbleFits) {
            LPPLFit bestFit = fits.stream()
                    .filter(LPPLFit::isConverged)
                    .max(Comparator.comparingDouble(LPPLFit::rSquared))
                    .orElseGet(() -> LPPLFit.invalid(0, LPPLExhaustionStatus.NO_VALID_FIT));
            return new LPPLExhaustion(LPPLExhaustionStatus.NO_VALID_FIT, LPPLExhaustionSide.NONE,
                    getBarSeries().numFactory().zero(), NaN, bestFit, fits, fits.size(), actionable.size(), crashFits,
                    bubbleFits);
        }

        LPPLExhaustionSide side = crashFits > bubbleFits ? LPPLExhaustionSide.CRASH_EXHAUSTION
                : LPPLExhaustionSide.BUBBLE_EXHAUSTION;
        List<LPPLFit> sideFits = actionable.stream().filter(fit -> fit.side() == side).toList();
        LPPLFit dominantFit = sideFits.stream().max(Comparator.comparingDouble(this::fitStrength)).orElseThrow();
        double agreement = sideFits.size() / (double) windows.length;
        double averageQuality = sideFits.stream()
                .mapToDouble(fit -> clamp(fit.rSquared(), 0.0, 1.0))
                .average()
                .orElse(0.0);
        double horizonWeight = sideFits.stream().mapToDouble(this::horizonWeight).average().orElse(0.0);
        double magnitude = clamp(agreement * averageQuality * horizonWeight, 0.0, 1.0);
        double signedScore = side.scoreSign() * magnitude;
        return new LPPLExhaustion(LPPLExhaustionStatus.VALID, side, getBarSeries().numFactory().numOf(signedScore),
                getBarSeries().numFactory().numOf(averageQuality), dominantFit, fits, fits.size(), actionable.size(),
                crashFits, bubbleFits);
    }

    private LPPLExhaustion neutral(LPPLExhaustionStatus status) {
        return new LPPLExhaustion(status, LPPLExhaustionSide.NONE, getBarSeries().numFactory().zero(), NaN,
                LPPLFit.invalid(0, status), List.of(), 0, 0, 0, 0);
    }

    private double fitStrength(LPPLFit fit) {
        return clamp(fit.rSquared(), 0.0, 1.0) * horizonWeight(fit);
    }

    private double horizonWeight(LPPLFit fit) {
        double middle = (activeMinCriticalOffset + activeMaxCriticalOffset) * 0.5;
        double halfWidth = Math.max(1.0, (activeMaxCriticalOffset - activeMinCriticalOffset) * 0.5);
        double centeredWeight = 1.0 - Math.abs(fit.criticalOffset() - middle) / halfWidth;
        return 0.5 + 0.5 * clamp(centeredWeight, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @return source unstable bars plus the largest configured LPPL fit window
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return priceIndicator.getCountOfUnstableBars() + profile.maxWindow() - 1;
    }

    /**
     * @return source price indicator
     * @since 0.23.1
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return calibration profile used by this indicator
     * @since 0.23.1
     */
    public LPPLCalibrationProfile getProfile() {
        return profile;
    }
}
