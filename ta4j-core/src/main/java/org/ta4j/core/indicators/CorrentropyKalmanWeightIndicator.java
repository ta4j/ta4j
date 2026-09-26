/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Measurement-side correntropy kernel weight indicator.
 * <p>
 * At each index this indicator returns the kernel weight
 *
 * <pre>
 * c_y = exp(-e_y^2 / (2 * sigma^2))  with  e_y = (y_t - x*) / sqrt(R_t)
 * </pre>
 *
 * evaluated at the accepted fixed-point candidate {@code x*} of the
 * {@link CorrentropyKalmanFilterIndicator}. The weight is one at the first
 * valid observation and otherwise a finite value in {@code [0, 1]} describing
 * how much the measurement contributed to the robust estimate; a zero weight
 * means the measurement was rejected by the correntropy kernel.
 * <p>
 * The view delegates series and unstable-bar count to the filter, shares the
 * filter's private recursive state and does not rerun the fixed-point
 * iteration. Whenever the filter's current index is unavailable (invalid
 * source/Q/R, non-convergence or numerical failure), this indicator returns
 * {@link NaN NaN}.
 *
 * @see CorrentropyKalmanFilterIndicator
 * @since 0.24.2
 */
public class CorrentropyKalmanWeightIndicator extends CachedIndicator<Num> {

    private final CorrentropyKalmanFilterIndicator filter;

    /**
     * Constructs the filter-owned measurement-weight view.
     *
     * @param filter the correntropy Kalman filter whose shared private state is
     *               read
     */
    CorrentropyKalmanWeightIndicator(CorrentropyKalmanFilterIndicator filter) {
        super(filter.getBarSeries());
        this.filter = filter;
    }

    /**
     * Calculates the measurement weight of the correntropy Kalman filter at the
     * given index.
     *
     * @param index the index for which to calculate the measurement weight
     * @return the measurement weight at the given index, or {@link NaN NaN} when
     *         the filter's current index is unavailable
     */
    @Override
    protected Num calculate(int index) {
        return filter.measurementWeightAt(index);
    }

    /**
     * Delegates the unstable-bar count to the filter.
     *
     * @return the number of unstable bars of the filter
     */
    @Override
    public int getCountOfUnstableBars() {
        return filter.getCountOfUnstableBars();
    }
}