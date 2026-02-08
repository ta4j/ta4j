/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.statistics.ZScoreIndicator;
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
    private final transient ZScoreIndicator zScore;

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
        this.zScore = new ZScoreIndicator(deviationIndicator, standardDeviationIndicator);
    }

    @Override
    protected Num calculate(int index) {
        return zScore.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return zScore.getCountOfUnstableBars();
    }
}
