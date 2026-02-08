/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Z-score of price relative to VWAP using volume-weighted standard deviation.
 * Uses the same VWAP window/anchor definition as the supplied VWAP indicators.
 *
 * @since 0.19
 */
public class VWAPZScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> deviationIndicator;
    private final Indicator<Num> standardDeviationIndicator;
    private final transient Indicator<Num> ratio;

    /**
     * Constructor.
     *
     * @param deviationIndicator         indicator measuring price - VWAP
     * @param standardDeviationIndicator indicator providing the VWAP standard
     *                                   deviation
     *
     * @since 0.19
     */
    public VWAPZScoreIndicator(Indicator<Num> deviationIndicator, Indicator<Num> standardDeviationIndicator) {
        super(IndicatorSeriesUtils.requireSameSeries(deviationIndicator, standardDeviationIndicator));
        this.deviationIndicator = deviationIndicator;
        this.standardDeviationIndicator = standardDeviationIndicator;
        this.ratio = BinaryOperationIndicator.quotient(deviationIndicator, standardDeviationIndicator);
    }

    @Override
    protected Num calculate(int index) {
        Num deviation = deviationIndicator.getValue(index);
        Num std = standardDeviationIndicator.getValue(index);
        if (Num.isNaNOrNull(deviation) || Num.isNaNOrNull(std) || std.isZero()) {
            return NaN.NaN;
        }
        return ratio.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(deviationIndicator.getCountOfUnstableBars(),
                standardDeviationIndicator.getCountOfUnstableBars());
    }
}
