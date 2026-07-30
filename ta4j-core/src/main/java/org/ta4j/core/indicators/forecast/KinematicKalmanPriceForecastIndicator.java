/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.ForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Projects constant-velocity Kalman state into an N-bar price distribution.
 *
 * <p>
 * Each instance has one fixed positive horizon, matching
 * {@link ForecastProjectionIndicator#getHorizon()}. Multiple horizons can share
 * one cached {@link KinematicKalmanForecastStateIndicator}. The projection
 * reads no future source values: process and measurement noise observed at the
 * decision index are held constant through the configured horizon.
 *
 * <p>
 * The returned analytic Gaussian distribution describes the future observed
 * value. Its variance includes propagated corrected-state covariance,
 * accumulated diagonal process noise, and one measurement-noise term at
 * maturity.
 *
 * @since 0.23.1
 */
public final class KinematicKalmanPriceForecastIndicator extends CachedIndicator<Forecast>
        implements ForecastProjectionIndicator {

    private static final String SUPPORT_ASSUMPTION = "linear-gaussian-kalman-observation";
    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution(0d, 1d);

    private final ForecastStateIndicator<KinematicKalmanForecastState> stateIndicator;
    private final int horizon;

    /**
     * Creates a one-bar price forecast.
     *
     * @param stateIndicator kinematic state source
     * @since 0.23.1
     */
    public KinematicKalmanPriceForecastIndicator(ForecastStateIndicator<KinematicKalmanForecastState> stateIndicator) {
        this(stateIndicator, 1);
    }

    /**
     * Creates an N-bar price forecast.
     *
     * @param stateIndicator kinematic state source
     * @param horizon        positive horizon in bars
     * @since 0.23.1
     */
    public KinematicKalmanPriceForecastIndicator(ForecastStateIndicator<KinematicKalmanForecastState> stateIndicator,
            int horizon) {
        super(Objects.requireNonNull(stateIndicator, "stateIndicator must not be null"));
        if (horizon < 1) {
            throw new IllegalArgumentException("horizon must be > 0");
        }
        this.stateIndicator = stateIndicator;
        this.horizon = horizon;
    }

    @Override
    protected Forecast calculate(int index) {
        KinematicKalmanForecastState state = stateIndicator.getValue(index);
        if (state == null || !state.isStable() || state.index() != index) {
            return Forecast.unstable(index, horizon);
        }
        NumFactory numFactory = getBarSeries().numFactory();
        Num horizonValue = numFactory.numOf(horizon);
        Num mean = state.position().plus(state.velocity().multipliedBy(horizonValue));
        Num variance = projectedObservationVariance(state, horizonValue, numFactory);
        if (!Num.isFinite(mean) || !Num.isFinite(variance) || variance.isNegative()) {
            return Forecast.unstable(index, horizon);
        }
        Num standardDeviation = variance.isZero() ? numFactory.zero() : variance.sqrt();
        if (!Num.isFinite(standardDeviation)) {
            return Forecast.unstable(index, horizon);
        }

        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (double probability : Forecast.DEFAULT_QUANTILE_PROBABILITIES) {
            Num zScore = numFactory.numOf(STANDARD_NORMAL.inverseCumulativeProbability(probability));
            Num quantile = mean.plus(standardDeviation.multipliedBy(zScore));
            if (Num.isFinite(quantile)) {
                quantiles.put(probability, quantile);
            }
        }
        return Forecast.builder(index, horizon, numFactory, ForecastSupport.analytic(SUPPORT_ASSUMPTION))
                .mean(mean)
                .median(mean)
                .standardDeviation(standardDeviation)
                .quantiles(quantiles)
                .build();
    }

    @Override
    public Forecast getValue(int index) {
        if (index >= 0 && index < getBarSeries().getRemovedBarsCount()) {
            return Forecast.unstable(index, horizon);
        }
        return super.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return stateIndicator.getCountOfUnstableBars();
    }

    @Override
    public int getHorizon() {
        return horizon;
    }

    private Num projectedObservationVariance(KinematicKalmanForecastState state, Num horizonValue,
            NumFactory numFactory) {
        Num twoHorizon = horizonValue.multipliedBy(numFactory.two());
        Num propagatedStateVariance = state.positionVariance()
                .plus(state.positionVelocityCovariance().multipliedBy(twoHorizon))
                .plus(state.velocityVariance().multipliedBy(horizonValue.multipliedBy(horizonValue)));

        Num horizonMinusOne = horizonValue.minus(numFactory.one());
        Num twoHorizonMinusOne = twoHorizon.minus(numFactory.one());
        Num squareSum = horizonMinusOne.multipliedBy(horizonValue)
                .multipliedBy(twoHorizonMinusOne)
                .dividedBy(numFactory.numOf(6));
        Num processMultiplier = horizonValue.plus(squareSum);
        return propagatedStateVariance.plus(state.processNoise().multipliedBy(processMultiplier))
                .plus(state.measurementNoise());
    }
}
