/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Measurement residual indicator of the {@link CorrentropyKalmanFilterIndicator}.
 * <p>
 * At each index this indicator returns the residual
 *
 * <pre>
 * e_t = y_t - x_t
 * </pre>
 *
 * between the source measurement and the robust estimate accepted by the
 * filter's fixed-point update. Whenever the measurement or the filter's
 * current estimate is unavailable, or their difference is not representable in
 * the series {@link NumFactory}, this indicator returns {@link NaN NaN}.
 * <p>
 * The view delegates series and unstable-bar count to the filter, shares the
 * filter's private recursive state and does not rerun the fixed-point
 * iteration.
 *
 * @see CorrentropyKalmanFilterIndicator
 * @since 0.24.2
 */
public class CorrentropyKalmanResidualIndicator extends CachedIndicator<Num> {

    private final CorrentropyKalmanFilterIndicator filter;

    /**
     * Constructs the filter-owned residual view.
     *
     * @param filter the correntropy Kalman filter whose shared private state is
     *               read
     */
    CorrentropyKalmanResidualIndicator(CorrentropyKalmanFilterIndicator filter) {
        super(filter.getBarSeries());
        this.filter = filter;
    }

    /**
     * Calculates the measurement residual of the correntropy Kalman filter at the
     * given index.
     *
     * @param index the index for which to calculate the residual
     * @return the measurement residual at the given index, or {@link NaN NaN}
     *         when the series is empty or the index is inside the unstable-bar
     *         window
     */
    @Override
    protected Num calculate(int index) {
        if (getBarSeries().getBarCount() == 0 || index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        return filter.residualAt(index);
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