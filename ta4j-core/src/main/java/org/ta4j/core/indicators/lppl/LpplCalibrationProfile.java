/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Arrays;

/**
 * Calibration profile for LPPL exhaustion indicators.
 *
 * <p>
 * The default profile uses the common LPPL empirical ranges for {@code m} and
 * {@code omega}, searches critical times after the current bar, and validates
 * active exhaustion only when the critical time is near enough to matter for a
 * trading or rotation signal.
 *
 * @param windows                 rolling fit windows in bars; values are sorted
 *                                and duplicates are removed
 * @param minM                    lower bound for the power-law exponent
 * @param maxM                    upper bound for the power-law exponent
 * @param mSteps                  grid-search steps for {@code m}
 * @param minOmega                lower bound for log-periodic frequency
 * @param maxOmega                upper bound for log-periodic frequency
 * @param omegaSteps              grid-search steps for {@code omega}
 * @param minCriticalOffset       minimum critical-time offset from the current
 *                                bar
 * @param maxCriticalOffset       maximum critical-time offset from the current
 *                                bar
 * @param criticalOffsetStep      grid-search step for critical-time offsets
 * @param activeMinCriticalOffset minimum offset for an actionable exhaustion
 *                                signal
 * @param activeMaxCriticalOffset maximum offset for an actionable exhaustion
 *                                signal
 * @param maxEvaluations          optimizer evaluation budget per fit
 * @param minRSquared             minimum fit quality for an actionable signal
 * @since 0.22.7
 */
public record LpplCalibrationProfile(int[] windows, double minM, double maxM, int mSteps, double minOmega,
        double maxOmega, int omegaSteps, int minCriticalOffset, int maxCriticalOffset, int criticalOffsetStep,
        int activeMinCriticalOffset, int activeMaxCriticalOffset, int maxEvaluations, double minRSquared) {

    /**
     * Creates a validated calibration profile.
     *
     * @since 0.22.7
     */
    public LpplCalibrationProfile {
        if (windows == null || windows.length == 0) {
            throw new IllegalArgumentException("windows must contain at least one value");
        }
        windows = Arrays.stream(windows).sorted().distinct().toArray();
        for (int window : windows) {
            if (window < 5) {
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
    }

    /**
     * @return daily-equity defaults suitable for first-pass LPPL exhaustion scans
     * @since 0.22.7
     */
    public static LpplCalibrationProfile defaults() {
        return new LpplCalibrationProfile(new int[] { 200, 300, 400, 500 }, 0.1, 0.9, 5, 6.0, 13.0, 8, 1, 60, 5, 10, 30,
                120, 0.75);
    }

    /**
     * @return defensive copy of rolling fit windows
     * @since 0.22.7
     */
    @Override
    public int[] windows() {
        return Arrays.copyOf(windows, windows.length);
    }

    /**
     * Compares profiles using window values instead of array identity.
     *
     * @since 0.22.7
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LpplCalibrationProfile other)) {
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

    /**
     * @return hash code based on window values and scalar profile settings
     * @since 0.22.7
     */
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

    /**
     * @return readable profile description with window values instead of array
     *         identity
     * @since 0.22.7
     */
    @Override
    public String toString() {
        return "LpplCalibrationProfile[windows=" + Arrays.toString(windows) + ", minM=" + minM + ", maxM=" + maxM
                + ", mSteps=" + mSteps + ", minOmega=" + minOmega + ", maxOmega=" + maxOmega + ", omegaSteps="
                + omegaSteps + ", minCriticalOffset=" + minCriticalOffset + ", maxCriticalOffset=" + maxCriticalOffset
                + ", criticalOffsetStep=" + criticalOffsetStep + ", activeMinCriticalOffset=" + activeMinCriticalOffset
                + ", activeMaxCriticalOffset=" + activeMaxCriticalOffset + ", maxEvaluations=" + maxEvaluations
                + ", minRSquared=" + minRSquared + "]";
    }

    int maxWindow() {
        return windows[windows.length - 1];
    }
}
