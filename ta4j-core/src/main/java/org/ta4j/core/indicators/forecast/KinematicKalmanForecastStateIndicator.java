/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.KalmanNoiseIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.forecast.state.ForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Estimates an immutable constant-velocity Kalman state from a numeric source.
 *
 * <p>
 * The state vector is {@code [position, velocity]} with one-bar transition
 * {@code position' = position + velocity}. Only position is observed. Process
 * noise is diagonal in position and velocity, and measurement noise applies to
 * the observed position. Invalid source or noise values make only that index
 * unavailable; the last usable state remains available for later recovery.
 *
 * <p>
 * State is cached per index, so late, reverse, and random historical reads do
 * not mutate or replay a single shared filter instance.
 *
 * @since 0.23.1
 */
public final class KinematicKalmanForecastStateIndicator extends CachedIndicator<KinematicKalmanForecastState>
        implements ForecastStateIndicator<KinematicKalmanForecastState> {

    private final Indicator<Num> indicator;
    private final KalmanNoiseIndicator processNoiseIndicator;
    private final KalmanNoiseIndicator measurementNoiseIndicator;
    private transient volatile StateIndicator stateIndicator;

    /**
     * Creates a state estimator using the scalar Kalman defaults.
     *
     * @param indicator observed numeric source
     * @since 0.23.1
     */
    public KinematicKalmanForecastStateIndicator(Indicator<Num> indicator) {
        this(indicator, 1e-4, 1e-3);
    }

    /**
     * Creates a state estimator using constant noise variances.
     *
     * @param indicator        observed numeric source
     * @param processNoise     finite, positive process-noise variance
     * @param measurementNoise finite, positive measurement-noise variance
     * @since 0.23.1
     */
    public KinematicKalmanForecastStateIndicator(Indicator<Num> indicator, double processNoise,
            double measurementNoise) {
        this(indicator, KalmanNoiseIndicator.constant(requireIndicator(indicator).getBarSeries(), processNoise),
                KalmanNoiseIndicator.constant(indicator.getBarSeries(), measurementNoise));
    }

    /**
     * Creates a state estimator using dynamic noise variances.
     *
     * @param indicator                 observed numeric source
     * @param processNoiseIndicator     dynamic process-noise variance
     * @param measurementNoiseIndicator dynamic measurement-noise variance
     * @since 0.23.1
     */
    public KinematicKalmanForecastStateIndicator(Indicator<Num> indicator, KalmanNoiseIndicator processNoiseIndicator,
            KalmanNoiseIndicator measurementNoiseIndicator) {
        super(IndicatorUtils.requireSameSeries(indicator, processNoiseIndicator, measurementNoiseIndicator));
        this.indicator = indicator;
        this.processNoiseIndicator = processNoiseIndicator;
        this.measurementNoiseIndicator = measurementNoiseIndicator;
    }

    @Override
    protected KinematicKalmanForecastState calculate(int index) {
        State state = stateIndicator().getValue(index);
        if (!state.currentValuesValid()) {
            return KinematicKalmanForecastState.unstable(index, state.observationCount());
        }
        return KinematicKalmanForecastState.stable(index, state.observationCount(), state.position(), state.velocity(),
                state.positionVariance(), state.positionVelocityCovariance(), state.velocityVariance(),
                state.processNoise(), state.measurementNoise());
    }

    @Override
    public KinematicKalmanForecastState getValue(int index) {
        if (index >= 0 && index < getBarSeries().getRemovedBarsCount()) {
            return KinematicKalmanForecastState.unstable(index, 0);
        }
        return super.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(indicator.getCountOfUnstableBars(), Math.max(processNoiseIndicator.getCountOfUnstableBars(),
                measurementNoiseIndicator.getCountOfUnstableBars()));
    }

    private StateIndicator stateIndicator() {
        StateIndicator current = stateIndicator;
        if (current == null) {
            synchronized (this) {
                current = stateIndicator;
                if (current == null) {
                    current = new StateIndicator();
                    stateIndicator = current;
                }
            }
        }
        return current;
    }

    private State initialState(Num measurement, Num processNoise, Num measurementNoise) {
        NumFactory numFactory = getBarSeries().numFactory();
        Num one = numFactory.one();
        Num priorVariance = one.plus(processNoise);
        Num innovationVariance = priorVariance.plus(measurementNoise);
        Num positionGain = priorVariance.dividedBy(innovationVariance);
        Num positionVariance = one.minus(positionGain).multipliedBy(priorVariance);
        boolean stateValid = isUsableState(measurement, numFactory.zero(), positionVariance, numFactory.zero(),
                priorVariance);
        return new State(measurement, numFactory.zero(), positionVariance, numFactory.zero(), priorVariance,
                processNoise, measurementNoise, 1, stateValid, stateValid);
    }

    private State update(State previous, Num measurement, Num processNoise, Num measurementNoise) {
        NumFactory numFactory = getBarSeries().numFactory();
        Num one = numFactory.one();
        Num two = numFactory.two();

        Num predictedPosition = previous.position().plus(previous.velocity());
        Num predictedPositionVariance = previous.positionVariance()
                .plus(previous.positionVelocityCovariance().multipliedBy(two))
                .plus(previous.velocityVariance())
                .plus(processNoise);
        Num predictedCovariance = previous.positionVelocityCovariance().plus(previous.velocityVariance());
        Num predictedVelocityVariance = previous.velocityVariance().plus(processNoise);

        Num innovationVariance = predictedPositionVariance.plus(measurementNoise);
        Num positionGain = predictedPositionVariance.dividedBy(innovationVariance);
        Num velocityGain = predictedCovariance.dividedBy(innovationVariance);
        Num innovation = measurement.minus(predictedPosition);
        Num position = predictedPosition.plus(positionGain.multipliedBy(innovation));
        Num velocity = previous.velocity().plus(velocityGain.multipliedBy(innovation));

        Num oneMinusPositionGain = one.minus(positionGain);
        Num positionVariance = oneMinusPositionGain.multipliedBy(oneMinusPositionGain)
                .multipliedBy(predictedPositionVariance)
                .plus(positionGain.multipliedBy(positionGain).multipliedBy(measurementNoise));
        Num covariance = oneMinusPositionGain
                .multipliedBy(predictedCovariance.minus(velocityGain.multipliedBy(predictedPositionVariance)))
                .plus(positionGain.multipliedBy(velocityGain).multipliedBy(measurementNoise));
        Num velocityVariance = velocityGain.multipliedBy(velocityGain)
                .multipliedBy(predictedPositionVariance)
                .minus(velocityGain.multipliedBy(predictedCovariance).multipliedBy(two))
                .plus(predictedVelocityVariance)
                .plus(velocityGain.multipliedBy(velocityGain).multipliedBy(measurementNoise));
        boolean stateValid = isUsableState(position, velocity, positionVariance, covariance, velocityVariance);
        return new State(position, velocity, positionVariance, covariance, velocityVariance, processNoise,
                measurementNoise, previous.observationCount() + 1, stateValid, stateValid);
    }

    private static boolean isUsableState(Num position, Num velocity, Num positionVariance, Num covariance,
            Num velocityVariance) {
        return Num.isFinite(position) && Num.isFinite(velocity) && Num.isFinite(positionVariance)
                && !positionVariance.isNegative() && Num.isFinite(covariance) && Num.isFinite(velocityVariance)
                && !velocityVariance.isNegative();
    }

    private static Indicator<Num> requireIndicator(Indicator<Num> indicator) {
        return Objects.requireNonNull(indicator, "indicator must not be null");
    }

    private final class StateIndicator extends RecursiveCachedIndicator<State> {

        private StateIndicator() {
            super(KinematicKalmanForecastStateIndicator.this.indicator);
        }

        @Override
        protected State calculate(int index) {
            Num measurement = indicator.getValue(index);
            Num processNoise = processNoiseIndicator.getValue(index);
            Num measurementNoise = measurementNoiseIndicator.getValue(index);
            boolean valid = Num.isFinite(measurement) && Num.isFinite(processNoise) && processNoise.isPositive()
                    && Num.isFinite(measurementNoise) && measurementNoise.isPositive();
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                if (valid) {
                    return initialState(measurement, processNoise, measurementNoise);
                }
                NumFactory numFactory = getBarSeries().numFactory();
                return new State(numFactory.zero(), numFactory.zero(), numFactory.one(), numFactory.zero(),
                        numFactory.one(), processNoise, measurementNoise, 0, true, false);
            }

            State previous = getValue(index - 1);
            if (!valid) {
                return new State(previous.position(), previous.velocity(), previous.positionVariance(),
                        previous.positionVelocityCovariance(), previous.velocityVariance(), processNoise,
                        measurementNoise, previous.observationCount(), previous.stateValid(), false);
            }
            if (!previous.stateValid()) {
                return initialState(measurement, processNoise, measurementNoise);
            }
            return update(previous, measurement, processNoise, measurementNoise);
        }

        @Override
        public int getCountOfUnstableBars() {
            return KinematicKalmanForecastStateIndicator.this.getCountOfUnstableBars();
        }
    }

    private record State(Num position, Num velocity, Num positionVariance, Num positionVelocityCovariance,
            Num velocityVariance, Num processNoise, Num measurementNoise, int observationCount, boolean stateValid,
            boolean currentValuesValid) {
    }
}
