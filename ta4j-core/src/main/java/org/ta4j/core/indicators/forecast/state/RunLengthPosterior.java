/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * One Bayesian online change-point run-length hypothesis.
 *
 * <p>
 * The probability belongs to the complete run-length posterior; a list of top
 * hypotheses is therefore not renormalized to sum to one. Mean and variance are
 * the posterior expected observation moments for this component and use the
 * same numeric factory.
 *
 * @param runLength   observations since the hypothesized most recent change
 * @param probability probability of this run length in the complete posterior
 * @param mean        posterior expected observation mean
 * @param variance    posterior expected observation variance
 * @since 0.23.1
 */
public record RunLengthPosterior(int runLength, Num probability, Num mean, Num variance) {

    /**
     * Creates a validated, factory-coherent posterior summary.
     *
     * @since 0.23.1
     */
    public RunLengthPosterior {
        if (runLength < 0) {
            throw new IllegalArgumentException("runLength must be >= 0");
        }
        Num sourceMean = Objects.requireNonNull(mean, "mean must not be null");
        if (!Num.isFinite(sourceMean)) {
            throw new IllegalArgumentException("mean must be finite");
        }
        NumFactory numFactory = sourceMean.getNumFactory();
        mean = normalize(sourceMean, numFactory, "mean");
        probability = normalize(probability, numFactory, "probability");
        variance = normalize(variance, numFactory, "variance");
        if (!probability.isPositive() || probability.isGreaterThan(numFactory.one())) {
            throw new IllegalArgumentException("probability must be in (0, 1]");
        }
        if (variance.isNegative()) {
            throw new IllegalArgumentException("variance must be >= 0");
        }
    }

    private static Num normalize(Num value, NumFactory numFactory, String fieldName) {
        Num input = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(input)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(input.bigDecimalValue());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be finite in the mean factory");
        }
        if (normalized.isZero() && !input.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows the mean factory");
        }
        return normalized;
    }
}
