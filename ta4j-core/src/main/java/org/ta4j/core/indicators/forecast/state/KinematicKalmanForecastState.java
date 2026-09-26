/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.math.BigDecimal;
import java.util.Objects;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable constant-velocity Kalman state for price-level projections.
 *
 * <p>
 * The covariance is symmetric, so only its position variance, shared
 * position/velocity covariance, and velocity variance are published. Stable
 * states also retain the process and measurement noise observed at the decision
 * index so causal N-bar projections can hold those values constant without
 * reading future indicators.
 *
 * @param index                      source decision index
 * @param observationCount           valid measurements incorporated
 * @param isStable                   whether the state is available
 * @param position                   corrected position estimate
 * @param velocity                   corrected velocity estimate per bar
 * @param positionVariance           corrected position variance
 * @param positionVelocityCovariance corrected position/velocity covariance
 * @param velocityVariance           corrected velocity variance
 * @param processNoise               decision-index process-noise variance
 * @param measurementNoise           decision-index measurement-noise variance
 * @since 0.23.1
 */
public record KinematicKalmanForecastState(int index, int observationCount, boolean isStable, Num position,
        Num velocity, Num positionVariance, Num positionVelocityCovariance, Num velocityVariance, Num processNoise,
        Num measurementNoise) implements ForecastState {

    /**
     * Creates and validates a kinematic state.
     *
     * @since 0.23.1
     */
    public KinematicKalmanForecastState {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be >= 0");
        }
        position = Objects.requireNonNull(position, "position must not be null");
        velocity = Objects.requireNonNull(velocity, "velocity must not be null");
        positionVariance = Objects.requireNonNull(positionVariance, "positionVariance must not be null");
        positionVelocityCovariance = Objects.requireNonNull(positionVelocityCovariance,
                "positionVelocityCovariance must not be null");
        velocityVariance = Objects.requireNonNull(velocityVariance, "velocityVariance must not be null");
        processNoise = Objects.requireNonNull(processNoise, "processNoise must not be null");
        measurementNoise = Objects.requireNonNull(measurementNoise, "measurementNoise must not be null");
        if (isStable) {
            if (observationCount == 0) {
                throw new IllegalArgumentException("stable state must include at least one observation");
            }
            NumFactory numFactory = position.getNumFactory();
            position = normalize(position, numFactory, "position");
            velocity = normalize(velocity, numFactory, "velocity");
            positionVariance = requireNonNegative(normalize(positionVariance, numFactory, "positionVariance"),
                    "positionVariance");
            positionVelocityCovariance = normalize(positionVelocityCovariance, numFactory,
                    "positionVelocityCovariance");
            velocityVariance = requireNonNegative(normalize(velocityVariance, numFactory, "velocityVariance"),
                    "velocityVariance");
            BigDecimal varianceProduct = positionVariance.bigDecimalValue()
                    .multiply(velocityVariance.bigDecimalValue());
            BigDecimal covarianceSquare = positionVelocityCovariance.bigDecimalValue()
                    .multiply(positionVelocityCovariance.bigDecimalValue());
            if (varianceProduct.compareTo(covarianceSquare) < 0) {
                throw new IllegalArgumentException("covariance must be positive semidefinite");
            }
            processNoise = requirePositive(normalize(processNoise, numFactory, "processNoise"), "processNoise");
            measurementNoise = requirePositive(normalize(measurementNoise, numFactory, "measurementNoise"),
                    "measurementNoise");
        } else if (!position.isNaN() || !velocity.isNaN() || !positionVariance.isNaN()
                || !positionVelocityCovariance.isNaN() || !velocityVariance.isNaN() || !processNoise.isNaN()
                || !measurementNoise.isNaN()) {
            throw new IllegalArgumentException("unstable state must use NaN numeric values");
        }
    }

    /**
     * Creates a stable kinematic state.
     *
     * @return validated stable state
     * @since 0.23.1
     */
    public static KinematicKalmanForecastState stable(int index, int observationCount, Num position, Num velocity,
            Num positionVariance, Num positionVelocityCovariance, Num velocityVariance, Num processNoise,
            Num measurementNoise) {
        return new KinematicKalmanForecastState(index, observationCount, true, position, velocity, positionVariance,
                positionVelocityCovariance, velocityVariance, processNoise, measurementNoise);
    }

    /**
     * Creates an unavailable state while preserving observation provenance.
     *
     * @param index            source decision index
     * @param observationCount valid measurements incorporated
     * @return unavailable state
     * @since 0.23.1
     */
    public static KinematicKalmanForecastState unstable(int index, int observationCount) {
        return new KinematicKalmanForecastState(index, observationCount, false, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN,
                NaN.NaN, NaN.NaN, NaN.NaN);
    }

    private static Num normalize(Num value, NumFactory numFactory, String fieldName) {
        if (!Num.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(value.bigDecimalValue());
        if (!Num.isFinite(normalized) || normalized.isZero() && !value.isZero()) {
            throw new IllegalArgumentException(fieldName + " cannot be represented by the position NumFactory");
        }
        return normalized;
    }

    private static Num requirePositive(Num value, String fieldName) {
        if (!value.isPositive()) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return value;
    }

    private static Num requireNonNegative(Num value, String fieldName) {
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return value;
    }
}
