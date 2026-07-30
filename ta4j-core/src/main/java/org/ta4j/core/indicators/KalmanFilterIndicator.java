/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * KalmanFilterIndicator is a technical analysis indicator that uses the Kalman
 * filter to smooth the values of an underlying indicator. The Kalman filter is
 * a recursive algorithm that estimates the state of a dynamic system from a
 * series of noisy measurements.
 * <p>
 * This indicator is particularly useful for reducing noise and improving the
 * signal-to-noise ratio of an indicator, which can be beneficial for various
 * trading strategies and analysis.
 *
 * @since 0.17
 */
public class KalmanFilterIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final KalmanNoiseIndicator processNoiseIndicator;
    private final KalmanNoiseIndicator measurementNoiseIndicator;
    private transient volatile StateIndicator stateIndicator;

    /**
     * Constructs a KalmanFilterIndicator with the given indicator and default noise
     * parameters.
     *
     * @param indicator the indicator whose values will be smoothed by the Kalman
     *                  filter
     */
    public KalmanFilterIndicator(Indicator<Num> indicator) {
        this(indicator, 1e-4, 1e-3);
    }

    /**
     * Constructs a KalmanFilterIndicator with the given indicator and custom noise
     * parameters. These parameters control how much the filter trusts the model
     * (process) versus the observations (measurements).
     *
     * @param indicator        the indicator whose values will be smoothed by the
     *                         Kalman filter
     * @param processNoise     the process noise parameter
     * @param measurementNoise the measurement noise parameter
     */
    public KalmanFilterIndicator(Indicator<Num> indicator, double processNoise, double measurementNoise) {
        this(indicator, KalmanNoiseIndicator.constant(indicator.getBarSeries(), processNoise),
                KalmanNoiseIndicator.constant(indicator.getBarSeries(), measurementNoise));
    }

    /**
     * Constructs a KalmanFilterIndicator with dynamic process and measurement
     * noise.
     *
     * <p>
     * Values for both noise indicators are read at the exact source index. An
     * unavailable noise value makes that index unavailable without contaminating
     * later valid state.
     *
     * @param indicator                 indicator whose values will be smoothed
     * @param processNoiseIndicator     dynamic process-noise variance
     * @param measurementNoiseIndicator dynamic measurement-noise variance
     * @since 0.23.1
     */
    public KalmanFilterIndicator(Indicator<Num> indicator, KalmanNoiseIndicator processNoiseIndicator,
            KalmanNoiseIndicator measurementNoiseIndicator) {
        super(IndicatorUtils.requireSameSeries(indicator, processNoiseIndicator, measurementNoiseIndicator));
        this.indicator = indicator;
        this.processNoiseIndicator = processNoiseIndicator;
        this.measurementNoiseIndicator = measurementNoiseIndicator;
    }

    /**
     * Calculates the Kalman-filtered value of the underlying indicator at the given
     * index.
     *
     * @param index the index for which to calculate the Kalman-filtered value
     * @return the Kalman-filtered value of the underlying indicator at the given
     *         index
     */
    @Override
    protected Num calculate(int index) {
        if (this.indicator.getBarSeries().getBarCount() == 0) {
            return NaN.NaN;
        }

        KalmanState state = stateIndicator().getValue(index);
        if (!state.currentValuesValid()) {
            return NaN.NaN;
        }

        return state.estimate();
    }

    /**
     * Returns the number of bars up to which this indicator calculates unstable
     * values. This typically corresponds to the number of bars required for the
     * underlying indicator to produce reliable results.
     *
     * @return the number of unstable bars
     */
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

    private KalmanState initialState(Num measurement, boolean validMeasurement) {
        Num estimate = validMeasurement ? measurement : getBarSeries().numFactory().zero();
        return new KalmanState(estimate, getBarSeries().numFactory().one(), true, validMeasurement);
    }

    private KalmanState correct(KalmanState previous, Num measurement, Num processNoise, Num measurementNoise) {
        Num predictedErrorCovariance = previous.errorCovariance().plus(processNoise);
        Num kalmanGain = predictedErrorCovariance.dividedBy(predictedErrorCovariance.plus(measurementNoise));
        Num estimate = previous.estimate().plus(kalmanGain.multipliedBy(measurement.minus(previous.estimate())));
        Num errorCovariance = getBarSeries().numFactory()
                .one()
                .minus(kalmanGain)
                .multipliedBy(predictedErrorCovariance);
        boolean stateValid = Num.isFinite(estimate) && Num.isFinite(errorCovariance)
                && !errorCovariance.isNegative();
        return new KalmanState(estimate, errorCovariance, stateValid, stateValid);
    }

    private final class StateIndicator extends RecursiveCachedIndicator<KalmanState> {

        private StateIndicator() {
            super(KalmanFilterIndicator.this.indicator);
        }

        @Override
        protected KalmanState calculate(int index) {
            Num current = KalmanFilterIndicator.this.indicator.getValue(index);
            Num processNoise = processNoiseIndicator.getValue(index);
            Num measurementNoise = measurementNoiseIndicator.getValue(index);
            boolean validMeasurement = Num.isFinite(current) && Num.isFinite(processNoise) && processNoise.isPositive()
                    && Num.isFinite(measurementNoise) && measurementNoise.isPositive();
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                KalmanState initial = initialState(current, validMeasurement);
                return validMeasurement ? correct(initial, current, processNoise, measurementNoise) : initial;
            }

            KalmanState previous = getValue(index - 1);
            if (!validMeasurement) {
                return new KalmanState(previous.estimate(), previous.errorCovariance(), previous.stateValid(), false);
            }
            if (!previous.stateValid()) {
                return correct(initialState(current, true), current, processNoise, measurementNoise);
            }
            return correct(previous, current, processNoise, measurementNoise);
        }

        @Override
        public int getCountOfUnstableBars() {
            return KalmanFilterIndicator.this.getCountOfUnstableBars();
        }
    }

    private record KalmanState(Num estimate, Num errorCovariance, boolean stateValid, boolean currentValuesValid) {
    }
}
