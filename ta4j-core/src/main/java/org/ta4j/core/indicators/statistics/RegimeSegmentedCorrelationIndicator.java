/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.BooleanTransformIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling Pearson correlation over samples selected by a Boolean regime.
 *
 * <p>
 * Only bars where the supplied regime indicator returns {@code true} contribute
 * samples to the correlation. This is useful when the same signal pair should
 * be evaluated separately in trend, range, volatility, or any caller-defined
 * state. Numeric regimes can be converted to Boolean regimes with
 * {@link BooleanTransformIndicator}.
 * </p>
 *
 * @since 0.22.7
 */
public class RegimeSegmentedCorrelationIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final Indicator<Boolean> regime;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param regime   Boolean regime selector; only {@code true} bars are used
     * @param barCount rolling window length, must be at least 2
     * @throws IllegalArgumentException if {@code barCount < 2} or indicators use
     *                                  different series
     * @throws NullPointerException     if an indicator is null
     * @since 0.22.7
     */
    public RegimeSegmentedCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, Indicator<Boolean> regime,
            int barCount) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second, regime);
        this.first = first;
        this.second = second;
        this.regime = regime;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        double[][] activeValues = CorrelationWindowSupport.activeRegimeWindow(first, second, regime, index, barCount);
        if (activeValues == null || activeValues[0].length < 2) {
            return NaN.NaN;
        }
        return CorrelationWindowSupport.pearson(getBarSeries().numFactory(), activeValues[0], activeValues[1]);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second, regime);
    }
}
