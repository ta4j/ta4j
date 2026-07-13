/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Arrays;

/**
 * Immutable calibration settings for Log-Periodic Power Law (LPPL) exhaustion
 * indicators.
 *
 * <p>
 * Start with {@link #defaults()} and replace only the related group of settings
 * that needs tuning. This keeps the common path readable while still exposing
 * the complete LPPL search space.
 *
 * @since 0.23.1
 */
public final class LPPLCalibrationProfile {

    static final int MINIMUM_WINDOW = 5;

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

    LPPLCalibrationProfile(int[] windows, double minM, double maxM, int mSteps, double minOmega, double maxOmega,
            int omegaSteps, int minCriticalOffset, int maxCriticalOffset, int criticalOffsetStep,
            int activeMinCriticalOffset, int activeMaxCriticalOffset, int maxEvaluations, double minRSquared) {
        if (windows == null || windows.length == 0) {
            throw new IllegalArgumentException("windows must contain at least one value");
        }
        this.windows = Arrays.stream(windows).sorted().distinct().toArray();
        for (int window : this.windows) {
            if (window < MINIMUM_WINDOW) {
                throw new IllegalArgumentException("windows must be at least 5 bars");
            }
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
        if (activeMinCriticalOffset < minCriticalOffset || activeMaxCriticalOffset > maxCriticalOffset
                || activeMaxCriticalOffset < activeMinCriticalOffset) {
            throw new IllegalArgumentException("active critical-time offsets must be inside the search range");
        }
        if (maxEvaluations < 1) {
            throw new IllegalArgumentException("maxEvaluations must be positive");
        }
        if (!Double.isFinite(minRSquared) || minRSquared < 0 || minRSquared > 1) {
            throw new IllegalArgumentException("minRSquared must be between 0 and 1");
        }
        this.minM = minM;
        this.maxM = maxM;
        this.mSteps = mSteps;
        this.minOmega = minOmega;
        this.maxOmega = maxOmega;
        this.omegaSteps = omegaSteps;
        this.minCriticalOffset = minCriticalOffset;
        this.maxCriticalOffset = maxCriticalOffset;
        this.criticalOffsetStep = criticalOffsetStep;
        this.activeMinCriticalOffset = activeMinCriticalOffset;
        this.activeMaxCriticalOffset = activeMaxCriticalOffset;
        this.maxEvaluations = maxEvaluations;
        this.minRSquared = minRSquared;
    }

    /**
     * @return daily-equity defaults suitable for first-pass LPPL exhaustion scans
     * @since 0.23.1
     */
    public static LPPLCalibrationProfile defaults() {
        return new LPPLCalibrationProfile(new int[] { 200, 300, 400, 500 }, 0.1, 0.9, 5, 6.0, 13.0, 8, 1, 60, 5, 10, 30,
                120, 0.75);
    }

    /**
     * @param windows rolling fit windows in bars
     * @return a profile using the supplied windows
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withWindows(int... windows) {
        return copy(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, activeMinCriticalOffset, activeMaxCriticalOffset, maxEvaluations, minRSquared);
    }

    /**
     * @param minM  lower power-law exponent bound
     * @param maxM  upper power-law exponent bound
     * @param steps grid-search steps
     * @return a profile using the supplied exponent search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withExponentSearch(double minM, double maxM, int steps) {
        return copy(windows, minM, maxM, steps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, activeMinCriticalOffset, activeMaxCriticalOffset, maxEvaluations, minRSquared);
    }

    /**
     * @param minOmega lower log-periodic frequency bound
     * @param maxOmega upper log-periodic frequency bound
     * @param steps    grid-search steps
     * @return a profile using the supplied frequency search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withFrequencySearch(double minOmega, double maxOmega, int steps) {
        return copy(windows, minM, maxM, mSteps, minOmega, maxOmega, steps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, activeMinCriticalOffset, activeMaxCriticalOffset, maxEvaluations, minRSquared);
    }

    /**
     * Narrows the actionable range to the overlap with this search, or to the
     * complete search when the previous actionable range does not overlap.
     *
     * @param minimumOffset minimum searched critical-time offset
     * @param maximumOffset maximum searched critical-time offset
     * @param step          grid-search step
     * @return a profile using the supplied critical-time search
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withCriticalTimeSearch(int minimumOffset, int maximumOffset, int step) {
        int actionableMinimum = Math.max(minimumOffset, activeMinCriticalOffset);
        int actionableMaximum = Math.min(maximumOffset, activeMaxCriticalOffset);
        if (actionableMinimum > actionableMaximum) {
            actionableMinimum = minimumOffset;
            actionableMaximum = maximumOffset;
        }
        return copy(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minimumOffset, maximumOffset, step,
                actionableMinimum, actionableMaximum, maxEvaluations, minRSquared);
    }

    /**
     * @param minimumOffset minimum actionable critical-time offset
     * @param maximumOffset maximum actionable critical-time offset
     * @return a profile using the supplied actionable range
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withActionableCriticalTimeRange(int minimumOffset, int maximumOffset) {
        return copy(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, minimumOffset, maximumOffset, maxEvaluations, minRSquared);
    }

    /**
     * @param maxEvaluations optimizer evaluation budget per fit
     * @param minRSquared    minimum actionable fit quality
     * @return a profile using the supplied optimizer settings
     * @since 0.23.1
     */
    public LPPLCalibrationProfile withOptimizerSettings(int maxEvaluations, double minRSquared) {
        return copy(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps, minCriticalOffset, maxCriticalOffset,
                criticalOffsetStep, activeMinCriticalOffset, activeMaxCriticalOffset, maxEvaluations, minRSquared);
    }

    /**
     * @return defensive copy of rolling fit windows
     * @since 0.23.1
     */
    public int[] windows() {
        return Arrays.copyOf(windows, windows.length);
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
     * @return lower frequency bound
     * @since 0.23.1
     */
    public double minOmega() {
        return minOmega;
    }

    /**
     * @return upper frequency bound
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
     * @return minimum searched critical-time offset
     * @since 0.23.1
     */
    public int minCriticalOffset() {
        return minCriticalOffset;
    }

    /**
     * @return maximum searched critical-time offset
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
     * @return minimum actionable critical-time offset
     * @since 0.23.1
     */
    public int activeMinCriticalOffset() {
        return activeMinCriticalOffset;
    }

    /**
     * @return maximum actionable critical-time offset
     * @since 0.23.1
     */
    public int activeMaxCriticalOffset() {
        return activeMaxCriticalOffset;
    }

    /**
     * @return optimizer evaluation budget per fit
     * @since 0.23.1
     */
    public int maxEvaluations() {
        return maxEvaluations;
    }

    /**
     * @return minimum actionable coefficient of determination
     * @since 0.23.1
     */
    public double minRSquared() {
        return minRSquared;
    }

    private LPPLCalibrationProfile copy(int[] windows, double minM, double maxM, int mSteps, double minOmega,
            double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset, int criticalOffsetStep,
            int activeMinCriticalOffset, int activeMaxCriticalOffset, int maxEvaluations, double minRSquared) {
        return new LPPLCalibrationProfile(windows, minM, maxM, mSteps, minOmega, maxOmega, omegaSteps,
                minCriticalOffset, maxCriticalOffset, criticalOffsetStep, activeMinCriticalOffset,
                activeMaxCriticalOffset, maxEvaluations, minRSquared);
    }

    int maxWindow() {
        return windows[windows.length - 1];
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LPPLCalibrationProfile other)) {
            return false;
        }
        return Arrays.equals(windows, other.windows) && Double.compare(minM, other.minM) == 0
                && Double.compare(maxM, other.maxM) == 0 && mSteps == other.mSteps
                && Double.compare(minOmega, other.minOmega) == 0 && Double.compare(maxOmega, other.maxOmega) == 0
                && omegaSteps == other.omegaSteps && minCriticalOffset == other.minCriticalOffset
                && maxCriticalOffset == other.maxCriticalOffset && criticalOffsetStep == other.criticalOffsetStep
                && activeMinCriticalOffset == other.activeMinCriticalOffset
                && activeMaxCriticalOffset == other.activeMaxCriticalOffset && maxEvaluations == other.maxEvaluations
                && Double.compare(minRSquared, other.minRSquared) == 0;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(windows);
        result = 31 * result + Double.hashCode(minM);
        result = 31 * result + Double.hashCode(maxM);
        result = 31 * result + Integer.hashCode(mSteps);
        result = 31 * result + Double.hashCode(minOmega);
        result = 31 * result + Double.hashCode(maxOmega);
        result = 31 * result + Integer.hashCode(omegaSteps);
        result = 31 * result + Integer.hashCode(minCriticalOffset);
        result = 31 * result + Integer.hashCode(maxCriticalOffset);
        result = 31 * result + Integer.hashCode(criticalOffsetStep);
        result = 31 * result + Integer.hashCode(activeMinCriticalOffset);
        result = 31 * result + Integer.hashCode(activeMaxCriticalOffset);
        result = 31 * result + Integer.hashCode(maxEvaluations);
        result = 31 * result + Double.hashCode(minRSquared);
        return result;
    }

    @Override
    public String toString() {
        return "LPPLCalibrationProfile[windows=" + Arrays.toString(windows) + ", minM=" + minM + ", maxM=" + maxM
                + ", mSteps=" + mSteps + ", minOmega=" + minOmega + ", maxOmega=" + maxOmega + ", omegaSteps="
                + omegaSteps + ", minCriticalOffset=" + minCriticalOffset + ", maxCriticalOffset=" + maxCriticalOffset
                + ", criticalOffsetStep=" + criticalOffsetStep + ", activeMinCriticalOffset=" + activeMinCriticalOffset
                + ", activeMaxCriticalOffset=" + activeMaxCriticalOffset + ", maxEvaluations=" + maxEvaluations
                + ", minRSquared=" + minRSquared + "]";
    }
}
