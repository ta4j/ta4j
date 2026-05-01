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
 * @param windows                 rolling fit windows in bars
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
        windows = windows.clone();
        Arrays.sort(windows);
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
        return windows.clone();
    }

    int maxWindow() {
        return windows[windows.length - 1];
    }
}
