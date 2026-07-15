/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Objects;

/**
 * Immutable calibration settings for a causal Log-Periodic Power Law (LPPL)
 * fit.
 *
 * <p>
 * Start with {@link #defaults()} and replace only the related group of settings
 * that needs tuning. The fit window ends one bar before the value being
 * evaluated, so every result is usable without look-ahead.
 *
 * @since 0.23.1
 */
public final class LPPLCalibrationProfile {

    static final int MINIMUM_WINDOW = 5;

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

    LPPLCalibrationProfile(int window, double minM, double maxM, int mSteps, double minOmega, double maxOmega,
            int omegaSteps, int minCriticalOffset, int maxCriticalOffset, int criticalOffsetStep, int maxEvaluations,
            double minRSquared) {
        if (window < MINIMUM_WINDOW) {
            throw new IllegalArgumentException("window must be at least 5 bars");
        }
        if (!Double.isFinite(minM) || !Double.isFinite(maxM) || minM <= 0 || maxM <= minM || maxM >= 1) {
            throw new IllegalArgumentException("m bounds must satisfy 0 < minM < maxM < 1");
        }
        if (mSteps < 1) {
            throw new IllegalArgumentException("mSteps must be positive");
        }
        if (!Double.isFinite(minOmega) || !Double.isFinite(maxOmega) || minOmega <= 0 || maxOmega <= minOmega) {
            throw new IllegalArgumentException("omega bounds must satisfy 0 < minOmega < maxOmega");
        }
        if (omegaSteps < 1) {
            throw new IllegalArgumentException("omegaSteps must be positive");
        }
        if (minCriticalOffset < 1 || maxCriticalOffset < minCriticalOffset) {
            throw new IllegalArgumentException("critical-time offsets must be positive and ordered");
        }
        if (criticalOffsetStep < 1) {
            throw new IllegalArgumentException("criticalOffsetStep must be positive");
        }
        if (maxEvaluations < 1) {
            throw new IllegalArgumentException("maxEvaluations must be positive");
        }
        if (!Double.isFinite(minRSquared) || minRSquared < 0 || minRSquared > 1) {
            throw new IllegalArgumentException("minRSquared must be between 0 and 1");
        }
        this.window = window;
        this.minM = minM;
        this.maxM = maxM;
        this.mSteps = mSteps;
        this.minOmega = minOmega;
        this.maxOmega = maxOmega;
        this.omegaSteps = omegaSteps;
        this.minCriticalOffset = minCriticalOffset;
        this.maxCriticalOffset = maxCriticalOffset;
        this.criticalOffsetStep = criticalOffsetStep;
        this.maxEvaluations = maxEvaluations;
        this.minRSquared = minRSquared;
    }

    /**
     * @return daily-equity defaults for a 500-session causal LPPL fit
     * @since 0.23.1
     */
    public static LPPLCalibrationProfile defaults() {
        return new LPPLCalibrationProfile(500, 0.1, 0.9, 5, 6.0, 13.0, 8, 1, 60, 5, 120, 0.75);
    }

    /**
     * @param window trailing bars used to calibrate the model before evaluation
     * @return a profile using the supplied fit window
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withWindow(int window) {
        return copy(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, maxEvaluations, minRSquared);
    }

    /**
     * @param minM  lower power-law exponent bound
     * @param maxM  upper power-law exponent bound
     * @param steps grid-search steps
     * @return a profile using the supplied exponent search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withExponentSearch(double minM, double maxM, int steps) {
        return copy(window, minM, maxM, steps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, maxEvaluations, minRSquared);
    }

    /**
     * @param minOmega lower log-periodic frequency bound
     * @param maxOmega upper log-periodic frequency bound
     * @param steps    grid-search steps
     * @return a profile using the supplied frequency search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withFrequencySearch(double minOmega, double maxOmega, int steps) {
        return copy(window, minM, maxM, mSteps, minOmega, maxOmega, steps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, maxEvaluations, minRSquared);
    }

    /**
     * Critical offsets are measured from the evaluated bar, not from the final
     * calibration bar.
     *
     * @param minimumOffset minimum searched critical-time offset
     * @param maximumOffset maximum searched critical-time offset
     * @param step          grid-search step
     * @return a profile using the supplied critical-time search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withCriticalTimeSearch(int minimumOffset, int maximumOffset, int step) {
        return copy(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minimumOffset, maximumOffset, step,
                maxEvaluations, minRSquared);
    }

    /**
     * @param maxEvaluations optimizer evaluation budget per fit
     * @param minRSquared    minimum qualified in-sample fit quality
     * @return a profile using the supplied optimizer settings
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withOptimizerSettings(int maxEvaluations, double minRSquared) {
        return copy(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, maxEvaluations, minRSquared);
    }

    /**
     * @return trailing calibration window in bars
     * @since 0.23.1
     */
    public int window() {
        return window;
    }

    /**
     * @return lower power-law exponent bound
     * @since 0.23.1
     */
    public double minM() {
        return minM;
    }

    /**
     * @return upper power-law exponent bound
     * @since 0.23.1
     */
    public double maxM() {
        return maxM;
    }

    /**
     * @return exponent grid-search steps
     * @since 0.23.1
     */
    public int mSteps() {
        return mSteps;
    }

    /**
     * @return lower log-periodic frequency bound
     * @since 0.23.1
     */
    public double minOmega() {
        return minOmega;
    }

    /**
     * @return upper log-periodic frequency bound
     * @since 0.23.1
     */
    public double maxOmega() {
        return maxOmega;
    }

    /**
     * @return frequency grid-search steps
     * @since 0.23.1
     */
    public int omegaSteps() {
        return omegaSteps;
    }

    /**
     * @return minimum critical-time offset from the evaluated bar
     * @since 0.23.1
     */
    public int minCriticalOffset() {
        return minCriticalOffset;
    }

    /**
     * @return maximum critical-time offset from the evaluated bar
     * @since 0.23.1
     */
    public int maxCriticalOffset() {
        return maxCriticalOffset;
    }

    /**
     * @return critical-time grid-search step
     * @since 0.23.1
     */
    public int criticalOffsetStep() {
        return criticalOffsetStep;
    }

    /**
     * @return optimizer evaluation budget
     * @since 0.23.1
     */
    public int maxEvaluations() {
        return maxEvaluations;
    }

    /**
     * @return minimum qualified coefficient of determination
     * @since 0.23.1
     */
    public double minRSquared() {
        return minRSquared;
    }

    private LPPLCalibrationProfile copy(int window, double minM, double maxM, int mSteps, double minOmega,
            double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset, int criticalOffsetStep,
            int maxEvaluations, double minRSquared) {
        return new LPPLCalibrationProfile(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset,
                maxCriticalOffset, criticalOffsetStep, maxEvaluations, minRSquared);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LPPLCalibrationProfile other)) {
            return false;
        }
        return window == other.window && Double.compare(minM, other.minM) == 0 && Double.compare(maxM, other.maxM) == 0
                && mSteps == other.mSteps && Double.compare(minOmega, other.minOmega) == 0
                && Double.compare(maxOmega, other.maxOmega) == 0 && omegaSteps == other.omegaSteps
                && minCriticalOffset == other.minCriticalOffset && maxCriticalOffset == other.maxCriticalOffset
                && criticalOffsetStep == other.criticalOffsetStep && maxEvaluations == other.maxEvaluations
                && Double.compare(minRSquared, other.minRSquared) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(window, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset,
                maxCriticalOffset, criticalOffsetStep, maxEvaluations, minRSquared);
    }

    @Override
    public String toString() {
        return "LPPLCalibrationProfile[window=" + window + ", minM=" + minM + ", maxM=" + maxM + ", mSteps=" + mSteps
                + ", minOmega=" + minOmega + ", maxOmega=" + maxOmega + ", omegaSteps=" + omegaSteps
                + ", minCriticalOffset=" + minCriticalOffset + ", maxCriticalOffset=" + maxCriticalOffset
                + ", criticalOffsetStep=" + criticalOffsetStep + ", maxEvaluations=" + maxEvaluations + ", minRSquared="
                + minRSquared + "]";
    }
}
