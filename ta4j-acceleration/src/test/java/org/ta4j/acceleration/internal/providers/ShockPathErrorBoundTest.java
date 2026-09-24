/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_NORMAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.VOLATILITY_CONSTANT;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.VOLATILITY_EWMA;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.providers.ShockPathErrorBound.Precision;

class ShockPathErrorBoundTest {

    @Test
    void fp32InputRoundingCounterexampleExceedsAMicroToleranceAndStaysWithinTheBound() {
        // price 1, mean 32, drift 0, variance 1, history [32.0000015]: the scalar
        // terminal is exp(1.5e-6) while fp32 narrows both 32s to the same float.
        List<double[]> inputs = inputs(new double[] { 32d }, new double[] { 0d }, new double[] { 1d },
                new double[] { 32.0000015d });

        double bound = ShockPathErrorBound.maxRelativePriceError(Precision.FP32, SHOCK_STANDARDIZED_EMPIRICAL, 1,
                inputs);
        double actual = maxObservedRelativeError(SHOCK_STANDARDIZED_EMPIRICAL, 32d, 0d, 1d,
                new double[] { 32.0000015d }, 1, 1);

        assertThat(actual).isGreaterThan(1e-6);
        assertThat(bound).isGreaterThanOrEqualTo(actual).isGreaterThan(1e-6);
    }

    @Test
    void fp32BoundCoversCancellationAndLongCumulativeHorizons() {
        SplittableRandom random = new SplittableRandom(7L);
        double[] window = new double[64];
        for (int index = 0; index < window.length; index++) {
            window[index] = 0.05d * (random.nextDouble() - 0.5d);
        }
        double[] nearMean = new double[] { 1.0000001d, 1.0000002d, 0.9999999d, 1d };
        for (Case fixture : List.of(new Case(SHOCK_HISTORICAL_BOOTSTRAP, 0d, 0d, 0d, window, 250),
                new Case(SHOCK_STANDARDIZED_EMPIRICAL, 0.001d, 0.0002d, 0.0004d, window, 250),
                new Case(SHOCK_STANDARDIZED_EMPIRICAL, 1d, 0d, 1e-12d, nearMean, 20))) {
            double[] returns = fixture.window();
            double bound = ShockPathErrorBound.maxRelativePriceError(Precision.FP32, fixture.shockModel(),
                    fixture.horizon(), inputs(new double[] { fixture.mean() }, new double[] { fixture.drift() },
                            new double[] { fixture.variance() }, returns));

            double actual = maxObservedRelativeError(fixture.shockModel(), fixture.mean(), fixture.drift(),
                    fixture.variance(), returns, fixture.horizon(), 256);

            assertThat(bound).as("bound for %s", fixture).isGreaterThanOrEqualTo(actual);
        }
    }

    @Test
    void fp64BootstrapBoundIsOnlyTheAccumulationRounding() {
        List<double[]> inputs = inputs(new double[] { 0d }, new double[] { 0d }, new double[] { 0d },
                new double[] { 0.01d, -0.02d });

        double bound = ShockPathErrorBound.maxRelativePriceError(Precision.FP64, SHOCK_HISTORICAL_BOOTSTRAP, 10,
                inputs);

        assertThat(bound).isPositive().isLessThan(1e-15);
    }

    @Test
    void pathDependentAndUnderflowingRequestsCannotBeCertified() {
        List<double[]> ordinary = inputs(new double[] { 0d }, new double[] { 0d }, new double[] { 1e-4d },
                new double[] { 0.01d });
        List<double[]> underflowing = inputs(new double[] { 0d }, new double[] { 0d }, new double[] { 1e-45d },
                new double[] { 0.01d });

        assertThat(ShockPathErrorBound.uncertifiableReason(Precision.FP64, SHOCK_STANDARDIZED_EMPIRICAL,
                VOLATILITY_EWMA, ordinary)).contains("EWMA");
        assertThat(ShockPathErrorBound.uncertifiableReason(Precision.FP32, SHOCK_NORMAL, VOLATILITY_CONSTANT, ordinary))
                .contains("Box-Muller");
        assertThat(ShockPathErrorBound.uncertifiableReason(Precision.FP32, SHOCK_STANDARDIZED_EMPIRICAL,
                VOLATILITY_CONSTANT, underflowing)).contains("underflows");
        assertThat(ShockPathErrorBound.uncertifiableReason(Precision.FP64, SHOCK_NORMAL, VOLATILITY_CONSTANT, ordinary))
                .isNull();
        assertThat(ShockPathErrorBound.uncertifiableReason(Precision.FP64, SHOCK_STANDARDIZED_EMPIRICAL,
                VOLATILITY_CONSTANT, underflowing)).isNull();
    }

    private static double maxObservedRelativeError(int shockModel, double mean, double drift, double variance,
            double[] window, int horizon, int paths) {
        double worst = 0d;
        for (int path = 0; path < paths; path++) {
            double exact = ShockPathReference.cumulativeLogReturn(false, shockModel, mean, drift, variance, window,
                    horizon, 42L, 3, path);
            double single = ShockPathReference.cumulativeLogReturn(true, shockModel, mean, drift, variance, window,
                    horizon, 42L, 3, path);
            worst = Math.max(worst, Math.abs(Math.expm1(single - exact)));
        }
        return worst;
    }

    static List<double[]> inputs(double[] means, double[] drifts, double[] variances, double[] returns) {
        double[] prices = new double[means.length];
        Arrays.fill(prices, 1d);
        return List.of(prices, means, drifts, variances, returns);
    }

    private record Case(int shockModel, double mean, double drift, double variance, double[] window, int horizon) {
    }
}
