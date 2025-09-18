/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
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
 */
public class KalmanFilterIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final double processNoise;
    private final double measurementNoise;
    private KalmanFilter filter;
    private final List<Num> stateEstimates;
    private int lastProcessedIndex;

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
        this.stateEstimates = new ArrayList<>();
        this.lastProcessedIndex = -1;
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

        ensureProcessedUpTo(index);

        return stateEstimates.get(index);
    }

    /**
     * Returns the number of unstable bars for this indicator. Since the Kalman
     * filter is a stateful algorithm, there are no unstable bars.
     *
     * @return 0, as there are no unstable bars for this indicator
     */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    private void ensureProcessedUpTo(int index) {
        if (filter == null || lastProcessedIndex < 0) {
            initializeFilter();
        }

        if (index <= lastProcessedIndex && index < stateEstimates.size() && stateEstimates.get(index) != null) {
            return;
        }

        final var numFactory = getBarSeries().numFactory();
        for (int i = Math.max(0, lastProcessedIndex + 1); i <= index; i++) {
            filter.predict();
            double measurement = this.indicator.getValue(i).doubleValue();
            filter.correct(new double[] { measurement });

            double kalmanValue = filter.getStateEstimation()[0];
            Num value = numFactory.numOf(kalmanValue);
            ensureCapacity(i);
            stateEstimates.set(i, value);
            lastProcessedIndex = i;
        }
    }

    private void ensureCapacity(int index) {
        while (stateEstimates.size() <= index) {
            stateEstimates.add(null);
        }
    }

    private void initializeFilter() {
        double initialEstimate = 0.0;
        if (indicator.getBarSeries().getBarCount() > 0) {
            initialEstimate = indicator.getValue(0).doubleValue();
            if (Double.isNaN(initialEstimate) || Double.isInfinite(initialEstimate)) {
                initialEstimate = 0.0;
            }
        }

        RealMatrix A = new Array2DRowRealMatrix(new double[] { 1 });
        RealMatrix B = new Array2DRowRealMatrix(new double[] { 0 });
        RealMatrix H = new Array2DRowRealMatrix(new double[] { 1 });
        RealVector x = new ArrayRealVector(new double[] { initialEstimate });
        RealMatrix Q = new Array2DRowRealMatrix(new double[] { processNoise });
        RealMatrix P = new Array2DRowRealMatrix(new double[] { 1 });
        RealMatrix R = new Array2DRowRealMatrix(new double[] { measurementNoise });

        this.filter = new KalmanFilter(new DefaultProcessModel(A, B, Q, x, P), new DefaultMeasurementModel(H, R));
        stateEstimates.clear();
        lastProcessedIndex = -1;
    }
}
