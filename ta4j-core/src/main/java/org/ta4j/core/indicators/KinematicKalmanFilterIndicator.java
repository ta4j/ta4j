/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanForecastStateIndicator;
import org.ta4j.core.indicators.forecast.KinematicKalmanPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.state.ForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Corrected-position view of a constant-velocity Kalman state estimator.
 *
 * <p>
 * {@link #getValue(int)} matches the same-bar semantics of
 * {@link KalmanFilterIndicator}: it returns the position estimate after
 * observing the source value at that index. Use {@link #forecast(int)} for a
 * causal N-bar price distribution that participates in ta4j's forecast
 * projection framework.
 *
 * @since 0.23.1
 */
public final class KinematicKalmanFilterIndicator extends CachedIndicator<Num> {

    private final ForecastStateIndicator<KinematicKalmanForecastState> stateIndicator;

    /**
     * Creates a filter using the scalar Kalman defaults.
     *
     * @param indicator observed numeric source
     * @since 0.23.1
     */
    public KinematicKalmanFilterIndicator(Indicator<Num> indicator) {
        this(new KinematicKalmanForecastStateIndicator(indicator));
    }

    /**
     * Creates a filter using constant noise variances.
     *
     * @param indicator        observed numeric source
     * @param processNoise     finite, positive process-noise variance
     * @param measurementNoise finite, positive measurement-noise variance
     * @since 0.23.1
     */
    public KinematicKalmanFilterIndicator(Indicator<Num> indicator, double processNoise, double measurementNoise) {
        this(new KinematicKalmanForecastStateIndicator(indicator, processNoise, measurementNoise));
    }

    /**
     * Creates a filter using dynamic noise variances.
     *
     * @param indicator                 observed numeric source
     * @param processNoiseIndicator     dynamic process-noise variance
     * @param measurementNoiseIndicator dynamic measurement-noise variance
     * @since 0.23.1
     */
    public KinematicKalmanFilterIndicator(Indicator<Num> indicator, KalmanNoiseIndicator processNoiseIndicator,
            KalmanNoiseIndicator measurementNoiseIndicator) {
        this(new KinematicKalmanForecastStateIndicator(indicator, processNoiseIndicator, measurementNoiseIndicator));
    }

    /**
     * Creates a corrected-position view over a reusable state source.
     *
     * @param stateIndicator kinematic state source
     * @since 0.23.1
     */
    public KinematicKalmanFilterIndicator(ForecastStateIndicator<KinematicKalmanForecastState> stateIndicator) {
        super(Objects.requireNonNull(stateIndicator, "stateIndicator must not be null"));
        this.stateIndicator = stateIndicator;
    }

    @Override
    protected Num calculate(int index) {
        KinematicKalmanForecastState state = stateIndicator.getValue(index);
        return state != null && state.isStable() && state.index() == index ? state.position() : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return stateIndicator.getCountOfUnstableBars();
    }

    /**
     * Creates a one-bar price forecast that reuses this filter's cached state.
     *
     * @return one-bar price forecast
     * @since 0.23.1
     */
    public KinematicKalmanPriceForecastIndicator forecast() {
        return forecast(1);
    }

    /**
     * Creates an N-bar price forecast that reuses this filter's cached state.
     *
     * @param horizon positive horizon in bars
     * @return configured price forecast
     * @since 0.23.1
     */
    public KinematicKalmanPriceForecastIndicator forecast(int horizon) {
        return new KinematicKalmanPriceForecastIndicator(stateIndicator, horizon);
    }
}
