/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

/**
 * Result of one LPPLS fit over a single rolling window.
 *
 * <p>
 * The model form is:
 *
 * <pre>
 * log(price(t)) = A + B * dt ^ m + C1 * dt ^ m * cos(omega * log(dt)) + C2 * dt ^ m * sin(omega * log(dt))
 * </pre>
 *
 * where {@code dt = tc - t}. The nonlinear search covers {@code tc}, {@code m},
 * and {@code omega}; linear least squares solves {@code A}, {@code B},
 * {@code C1}, and {@code C2}.
 *
 * @param window         fitted window length in bars
 * @param status         fit status
 * @param a              linear level parameter
 * @param b              power-law loading; positive means crash exhaustion and
 *                       negative means bubble exhaustion under this indicator's
 *                       sign convention
 * @param c1             cosine loading
 * @param c2             sine loading
 * @param criticalTime   fitted critical time in local window coordinates
 * @param m              fitted power-law exponent
 * @param omega          fitted log-periodic frequency
 * @param rss            residual sum of squares
 * @param rms            residual root mean square
 * @param rSquared       coefficient of determination
 * @param criticalOffset fitted critical-time offset from the current bar
 * @param evaluations    optimizer evaluations consumed, or zero for grid-only
 *                       failures
 * @since 0.22.7
 */
public record LpplFit(int window, LpplExhaustionStatus status, double a, double b, double c1, double c2,
        double criticalTime, double m, double omega, double rss, double rms, double rSquared, int criticalOffset,
        int evaluations) {

    /**
     * Creates a validated fit record.
     *
     * @since 0.22.7
     */
    public LpplFit {
        if (window < 0) {
            throw new IllegalArgumentException("window must be non-negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    /**
     * @return {@code true} when the optimizer produced finite LPPL parameters
     * @since 0.22.7
     */
    public boolean isConverged() {
        return status == LpplExhaustionStatus.VALID && Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c1)
                && Double.isFinite(c2) && Double.isFinite(criticalTime) && Double.isFinite(m) && Double.isFinite(omega)
                && Double.isFinite(rSquared);
    }

    /**
     * @param profile calibration profile used to interpret the fit
     * @return {@code true} when the fit passes empirical LPPL filters and active
     *         horizon constraints
     * @since 0.22.7
     */
    public boolean isActionable(LpplCalibrationProfile profile) {
        return isConverged() && rSquared >= profile.minRSquared() && m >= profile.minM() && m <= profile.maxM()
                && omega >= profile.minOmega() && omega <= profile.maxOmega()
                && criticalOffset >= profile.activeMinCriticalOffset()
                && criticalOffset <= profile.activeMaxCriticalOffset() && side() != LpplExhaustionSide.NONE;
    }

    /**
     * @return LPPL exhaustion side implied by {@code B}
     * @since 0.22.7
     */
    public LpplExhaustionSide side() {
        if (!Double.isFinite(b) || b == 0.0) {
            return LpplExhaustionSide.NONE;
        }
        return b > 0 ? LpplExhaustionSide.CRASH_EXHAUSTION : LpplExhaustionSide.BUBBLE_EXHAUSTION;
    }

    static LpplFit invalid(int window, LpplExhaustionStatus status) {
        return new LpplFit(window, status, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, 0);
    }
}
