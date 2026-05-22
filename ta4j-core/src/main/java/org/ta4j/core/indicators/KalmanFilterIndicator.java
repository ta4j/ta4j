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
    private final double processNoise;
    private final double measurementNoise;
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
        super(indicator);
        this.indicator = indicator;
        this.processNoise = processNoise;
        this.measurementNoise = measurementNoise;
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
        if (!state.validMeasurement() || Double.isNaN(state.estimate())) {
            return NaN.NaN;
        }

        return getBarSeries().numFactory().numOf(state.estimate());
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
        return indicator.getCountOfUnstableBars();
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

    private boolean isInvalidMeasurement(Num measurement, double primitiveMeasurement) {
        // Kalman filtering is intentionally primitive-backed to preserve the previous
        // Commons Math based behavior and avoid Num precision changes in this
        // performance-only optimization.
        return measurement == null || measurement.isNaN() || Double.isNaN(primitiveMeasurement)
                || Double.isInfinite(primitiveMeasurement);
    }

    private KalmanState initialState(double measurement, boolean validMeasurement) {
        double estimate = validMeasurement ? measurement : 0.0;
        return new KalmanState(estimate, 1.0, validMeasurement);
    }

    private KalmanState correct(KalmanState previous, double measurement) {
        double predictedErrorCovariance = previous.errorCovariance() + processNoise;
        double kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise);
        double estimate = previous.estimate() + kalmanGain * (measurement - previous.estimate());
        double errorCovariance = (1.0 - kalmanGain) * predictedErrorCovariance;
        return new KalmanState(estimate, errorCovariance, true);
    }

    private final class StateIndicator extends RecursiveCachedIndicator<KalmanState> {

        private StateIndicator() {
            super(KalmanFilterIndicator.this.indicator);
        }

        @Override
        protected KalmanState calculate(int index) {
            Num current = KalmanFilterIndicator.this.indicator.getValue(index);
            double measurement = current == null ? Double.NaN : current.doubleValue();
            boolean validMeasurement = !isInvalidMeasurement(current, measurement);
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                KalmanState initial = initialState(measurement, validMeasurement);
                return validMeasurement ? correct(initial, measurement) : initial;
            }

            KalmanState previous = getValue(index - 1);
            if (!validMeasurement) {
                return new KalmanState(previous.estimate(), previous.errorCovariance(), false);
            }
            return correct(previous, measurement);
        }

        @Override
        public int getCountOfUnstableBars() {
            return KalmanFilterIndicator.this.getCountOfUnstableBars();
        }
    }

    private record KalmanState(double estimate, double errorCovariance, boolean validMeasurement) {
    }
}
