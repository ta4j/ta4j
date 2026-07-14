/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Objects;

/**
 * Result of fitting LPPL to a trailing window and evaluating the next bar.
 *
 * <p>
 * The model form is:
 *
 * <pre>
 * log(price(t)) = A + B * dt^m + C1 * dt^m * cos(omega * log(dt))
 *               + C2 * dt^m * sin(omega * log(dt))
 * </pre>
 *
 * where {@code dt = tc - t}. The normalized residual is the one-step residual
 * divided by the maximum absolute residual across the calibration window and
 * evaluated bar under this same fitted trajectory.
 *
 * @param window             trailing calibration window
 * @param status             fit outcome
 * @param a                  linear level parameter
 * @param b                  power-law loading
 * @param c1                 cosine loading
 * @param c2                 sine loading
 * @param criticalTime       fitted critical time in local coordinates
 * @param m                  power-law exponent
 * @param omega              log-periodic frequency
 * @param rss                in-sample residual sum of squares
 * @param rms                in-sample residual root mean square
 * @param rSquared           in-sample coefficient of determination
 * @param criticalOffset     critical-time offset from the evaluated bar
 * @param evaluations        optimizer evaluations consumed
 * @param predictedLogPrice  predicted log price for the evaluated bar
 * @param residual           observed minus predicted log price
 * @param maxAbsResidual     normalization denominator across the fitted path
 * @param normalizedResidual residual normalized into {@code [-1, 1]}
 * @since 0.23.1
 */
public record LPPLFit(int window, LPPLFitStatus status, double a, double b, double c1, double c2,
        double criticalTime, double m, double omega, double rss, double rms, double rSquared, int criticalOffset,
        int evaluations, double predictedLogPrice, double residual, double maxAbsResidual, double normalizedResidual) {

    /**
     * Creates a validated fit result.
     *
     * @since 0.23.1
     */
    public LPPLFit {
        if (window < 0) {
            throw new IllegalArgumentException("window must be non-negative");
        }
        Objects.requireNonNull(status, "status");
        if (status == LPPLFitStatus.VALID && window < LPPLCalibrationProfile.MINIMUM_WINDOW) {
            throw new IllegalArgumentException("valid fits require at least five bars");
        }
        if (evaluations < 0) {
            throw new IllegalArgumentException("evaluations must be non-negative");
        }
        if (status == LPPLFitStatus.VALID
                && (!finite(a, b, c1, c2, criticalTime, m, omega, rss, rms, rSquared, predictedLogPrice, residual,
                        maxAbsResidual, normalizedResidual)
                        || maxAbsResidual < 0.0 || normalizedResidual < -1.0 || normalizedResidual > 1.0)) {
            throw new IllegalArgumentException("valid fits require finite, internally consistent diagnostics");
        }
    }

    /**
     * @return {@code true} when finite fit and evaluation diagnostics are present
     * @since 0.23.1
     */
    public boolean isConverged() {
        return status == LPPLFitStatus.VALID
                && finite(a, b, c1, c2, criticalTime, m, omega, rss, rms, rSquared, predictedLogPrice, residual,
                        maxAbsResidual, normalizedResidual);
    }

    /**
     * Tests whether the fit passes the profile's structural quality filters.
     * Qualification does not classify valuation or predict a crash.
     *
     * @param profile calibration profile used to interpret the fit
     * @return {@code true} when the fit is finite and passes all configured filters
     * @since 0.23.1
     */
    public boolean isQualified(LPPLCalibrationProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return isConverged() && rSquared >= profile.minRSquared() && m >= profile.minM() && m <= profile.maxM()
                && omega >= profile.minOmega() && omega <= profile.maxOmega() && b != 0.0
                && criticalOffset >= profile.minCriticalOffset() && criticalOffset <= profile.maxCriticalOffset();
    }

    /**
     * Evaluates the fitted log-price trajectory at a local time coordinate.
     *
     * @param time local time, where the evaluated bar is {@code window}
     * @return fitted log price, or {@link Double#NaN} at or beyond the singularity
     * @since 0.23.1
     */
    public double fittedLogPriceAt(double time) {
        if (!isConverged()) {
            return Double.NaN;
        }
        double dt = criticalTime - time;
        if (!Double.isFinite(dt) || dt <= 0.0) {
            return Double.NaN;
        }
        double power = Math.pow(dt, m);
        double logDt = Math.log(dt);
        return a + b * power + c1 * power * Math.cos(omega * logDt) + c2 * power * Math.sin(omega * logDt);
    }

    static LPPLFit invalid(int window, LPPLFitStatus status) {
        return new LPPLFit(window, status, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, 0, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN);
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
