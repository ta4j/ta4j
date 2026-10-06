/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.random.RandomGenerator;

/**
 * Shared exact random-variate samplers for the built-in Monte Carlo techniques.
 *
 * <p>
 * All samplers draw exclusively from the supplied {@link RandomGenerator}, so
 * equal generator states reproduce equal variates. The draw count of the
 * rejection-based samplers varies per call but is deterministic for a given
 * generator state.
 */
final class RandomSamplers {

    private RandomSamplers() {
    }

    /**
     * Exact gamma variate with the given shape via the Marsaglia-Tsang rejection
     * method. For {@code shape < 1} a boosting draw is folded in, so the algorithm
     * covers every positive shape in expected constant time. The boost uses
     * {@code 1 - nextDouble()} to sample from {@code (0, 1]}, so a legal zero
     * uniform never produces a zero multiplier.
     *
     * <p>
     * Consumption: each rejection attempt redraws {@code nextGaussian()} until
     * {@code 1 + c * x > 0}, then draws one {@code nextDouble()}; the
     * {@code shape < 1} branch adds one {@code nextDouble()}. Both the Gaussian
     * redraws and the acceptance probability depend on {@code shape} through
     * {@code c = 1 / sqrt(9 * (shape - 1/3))}, so the draw count varies with the
     * shape as well as the stream, while the expected count stays bounded for every
     * shape.
     *
     * @param random deterministic seeded random generator
     * @param shape  gamma shape parameter, must be &gt; 0
     * @return a gamma-distributed variate
     */
    static double nextGamma(RandomGenerator random, double shape) {
        if (shape < 1d) {
            return nextGamma(random, shape + 1d) * Math.pow(1d - random.nextDouble(), 1d / shape);
        }
        double delta = shape - 1d / 3d;
        double c = 1d / Math.sqrt(9d * delta);
        while (true) {
            double x;
            double v;
            do {
                x = random.nextGaussian();
                v = 1d + c * x;
            } while (v <= 0d);
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1d - 0.0331d * x * x * x * x) {
                return delta * v;
            }
            if (Math.log(u) < 0.5d * x * x + delta * (1d - v + Math.log(v))) {
                return delta * v;
            }
        }
    }

    /**
     * Exact chi-square variate with {@code degreesOfFreedom} via the gamma
     * representation {@code chiSq(k) = 2 * Gamma(k/2)}. The draw count follows
     * {@link #nextGamma(RandomGenerator, double)}: it varies with
     * {@code degreesOfFreedom} but its expectation stays bounded, so arbitrarily
     * large degrees of freedom remain expected constant-time.
     *
     * @param random           deterministic seeded random generator
     * @param degreesOfFreedom degrees of freedom, must be &gt;= 2
     * @return a chi-square-distributed variate
     */
    static double nextChiSquared(RandomGenerator random, int degreesOfFreedom) {
        return 2d * nextGamma(random, degreesOfFreedom / 2d);
    }
}
