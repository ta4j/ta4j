/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_DRIFTS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_MEANS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_PRICES;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_RETURNS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_VARIANCES;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_NORMAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.VOLATILITY_CONSTANT;

import java.util.List;

import org.ta4j.core.indicators.forecast.MonteCarloKernel;

/**
 * Condition-aware accuracy admission for approximate shock-path lanes.
 *
 * <p>
 * Core does not replay the scalar simulation, so a provider may only accept an
 * approximate request whose tolerance it can guarantee from the request inputs
 * alone. This class bounds, per simulated path, the absolute error of the
 * cumulative log-return {@code C = s_1 + ... + s_H} a lane computes; core maps
 * it to a terminal price {@code price * exp(C)}, so the relative price error is
 * at most {@code expm1(bound)}.
 *
 * <p>
 * The bound covers the lane's input rounding (FP32 lanes narrow every input),
 * each step's arithmetic, and the accumulation of {@code H} steps whose partial
 * sums grow to {@code k * B} for a per-step magnitude bound {@code B}:
 * <ul>
 * <li>historical bootstrap: {@code s = w}, so {@code B = max|w|} and a step
 * only inherits the input rounding of {@code w};</li>
 * <li>standardized empirical with constant volatility:
 * {@code s = d + vol * ((w - m) / vol)}; the rounded volatility cancels up to
 * rounding, so {@code B = max|w| + |m| + |d|} bounds every intermediate and a
 * step contributes at most {@code (e + 6u) * B};</li>
 * <li>normal with constant volatility: {@code s = d + vol * z} with a
 * Box-Muller draw {@code |z| <= 8.58}, so {@code B = |d| + vol * 8.58}; the
 * transcendental draw contributes at most {@code 16u * B}.</li>
 * </ul>
 * {@code u} is the lane's arithmetic unit roundoff and {@code e} its input
 * rounding unit. The bound is intentionally conservative ({@code max|w|} is
 * taken over the whole shared returns buffer) and linear in the inputs.
 *
 * <p>
 * Requests the bound cannot cover are declined for the scalar lane: EWMA
 * volatility paths, whose step magnitudes depend on the simulated path; normal
 * shocks on FP32 lanes, whose clamped single-precision Box-Muller tail departs
 * from the scalar draw by more than rounding; and FP32 inputs whose variance
 * underflows single precision, which would silently zero the volatility; and
 * requests whose worst-case cumulative log-return, widened by its error bound,
 * could reach core's exponent guard or push the terminal price out of the
 * normal double range, where the bound on the smooth {@code exp(C)} mapping
 * does not describe the scalar lane's unstable result.
 *
 * @since 0.26.1
 */
final class ShockPathErrorBound {

    /** Upper bound of {@code |z|} for a Box-Muller draw from 53-bit uniforms. */
    static final double MAX_ABS_GAUSSIAN = 8.58d;

    /**
     * Absolute slack added to the log-domain guard comparisons for the rounding of
     * {@code Math.log} and {@code Math.exp} at the guard boundaries.
     */
    private static final double GUARD_SLACK = 1e-9d;

    private static final double LOG_MAX_VALUE = Math.log(Double.MAX_VALUE);

    private static final double LOG_MIN_NORMAL = Math.log(Double.MIN_NORMAL);

    /** Arithmetic and input precision of a native lane. */
    enum Precision {

        /** Double-precision arithmetic on unmodified double inputs. */
        FP64(0x1.0p-53, 0d),

        /** Single-precision arithmetic on inputs narrowed to float. */
        FP32(0x1.0p-24, 0x1.0p-24);

        private final double unitRoundoff;
        private final double inputRoundoff;

        Precision(double unitRoundoff, double inputRoundoff) {
            this.unitRoundoff = unitRoundoff;
            this.inputRoundoff = inputRoundoff;
        }
    }

    private ShockPathErrorBound() {
    }

    /**
     * Returns why an approximate request cannot be certified at all on this lane,
     * or {@code null} when {@link #maxRelativePriceError} applies.
     *
     * <p>
     * Besides the lane limits, a request is declined when a row's worst-case
     * cumulative log-return, widened by its error bound, could reach core's
     * {@code abs(C) <= MAX_EXPONENT} guard or push {@code price * exp(C)} outside
     * the normal double range. Core's {@code terminalPrice} publishes an unstable
     * forecast there, and the exponential bound only covers the smooth mapping, so
     * a device sample within its error of a guard could decode stable while the
     * scalar lane does not.
     */
    static String uncertifiableReason(Precision precision, int shockModel, int volatilityMode, int horizon,
            List<double[]> inputs) {
        if (volatilityMode != VOLATILITY_CONSTANT) {
            return "EWMA volatility paths have no input-derived error bound";
        }
        if (shockModel == SHOCK_NORMAL && precision == Precision.FP32) {
            return "the fp32 Box-Muller draw departs from the scalar normal tail by more than rounding";
        }
        if (precision == Precision.FP32) {
            for (double variance : inputs.get(INPUT_VARIANCES)) {
                if (variance > 0d && Math.abs((float) variance) < Float.MIN_NORMAL) {
                    return "a variance of " + variance + " underflows single precision";
                }
            }
        }
        double maxReturn = maxAbsReturn(inputs);
        double[] prices = inputs.get(INPUT_PRICES);
        double[] means = inputs.get(INPUT_MEANS);
        double[] drifts = inputs.get(INPUT_DRIFTS);
        double[] variances = inputs.get(INPUT_VARIANCES);
        for (int row = 0; row < means.length; row++) {
            RowBound bound = rowBound(precision, shockModel, horizon, maxReturn, means[row], drifts[row],
                    variances[row]);
            // Every comparison is written so a NaN or infinite bound declines.
            double reach = horizon * bound.stepBound() + bound.logReturnError() + GUARD_SLACK;
            if (!(reach < MonteCarloKernel.MAX_EXPONENT)) {
                return "a cumulative log-return of up to " + reach + " may reach the +/-"
                        + MonteCarloKernel.MAX_EXPONENT + " exponent guard, where core publishes an unstable forecast";
            }
            double logPrice = Math.log(prices[row]);
            if (!(logPrice + reach < LOG_MAX_VALUE)) {
                return "the terminal price of " + prices[row] + " may overflow within the error of the scalar guard";
            }
            if (!(logPrice - reach > LOG_MIN_NORMAL)) {
                return "the terminal price of " + prices[row]
                        + " may underflow below the normal double range within the error of the scalar guard";
            }
        }
        return null;
    }

    /**
     * Returns an upper bound of the relative terminal-price error of any sample,
     * for a request that {@link #uncertifiableReason} accepts.
     */
    static double maxRelativePriceError(Precision precision, int shockModel, int horizon, List<double[]> inputs) {
        double maxReturn = maxAbsReturn(inputs);
        double[] means = inputs.get(INPUT_MEANS);
        double[] drifts = inputs.get(INPUT_DRIFTS);
        double[] variances = inputs.get(INPUT_VARIANCES);
        double worst = 0d;
        for (int row = 0; row < means.length; row++) {
            worst = Math.max(worst,
                    rowBound(precision, shockModel, horizon, maxReturn, means[row], drifts[row], variances[row])
                            .logReturnError());
        }
        return Double.isFinite(worst) ? Math.expm1(worst) : Double.POSITIVE_INFINITY;
    }

    private static double maxAbsReturn(List<double[]> inputs) {
        double maxReturn = 0d;
        for (double value : inputs.get(INPUT_RETURNS)) {
            maxReturn = Math.max(maxReturn, Math.abs(value));
        }
        return maxReturn;
    }

    /**
     * Bounds one decision row: the magnitude of every step and the absolute error
     * of the cumulative log-return over {@code horizon} steps.
     */
    private static RowBound rowBound(Precision precision, int shockModel, int horizon, double maxReturn, double mean,
            double drift, double variance) {
        double u = precision.unitRoundoff;
        double e = precision.inputRoundoff;
        double stepBound;
        double stepError;
        if (shockModel == SHOCK_HISTORICAL_BOOTSTRAP) {
            stepBound = maxReturn;
            stepError = e * maxReturn;
        } else if (shockModel == SHOCK_STANDARDIZED_EMPIRICAL) {
            stepBound = maxReturn + Math.abs(mean) + Math.abs(drift);
            stepError = (e + 6d * u) * stepBound;
        } else {
            double volatility = variance > 0d ? Math.sqrt(variance) : 0d;
            stepBound = Math.abs(drift) + volatility * MAX_ABS_GAUSSIAN;
            stepError = e * Math.abs(drift) + 16d * u * stepBound;
        }
        double accumulationSteps = horizon * (horizon + 1d) / 2d;
        return new RowBound(stepBound, horizon * stepError + u * stepBound * accumulationSteps);
    }

    private record RowBound(double stepBound, double logReturnError) {
    }
}
