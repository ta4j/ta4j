/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * The Stochastic RSI Indicator.
 *
 * <pre>
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 * </pre>
 */
public class StochasticRSIIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final StochasticIndicator stochasticIndicator;

    /**
     * Constructor.
     *
     * <p>
     * <b>Note:</b> In most cases, this constructor should be used to avoid
     * confusion about which indicator parameters to use.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public StochasticRSIIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator the Indicator (usually a {@link ClosePriceIndicator})
     * @param barCount  the time frame
     */
    public StochasticRSIIndicator(Indicator<Num> indicator, int barCount) {
        this(new RSIIndicator(indicator, barCount), barCount);
    }

    /**
     * Constructor.
     *
     * @param rsiIndicator the {@link RSIIndicator}
     * @param barCount     the time frame
     */
    public StochasticRSIIndicator(RSIIndicator rsiIndicator, int barCount) {
        super(rsiIndicator);
        this.barCount = barCount;
        this.stochasticIndicator = new StochasticIndicator(rsiIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return this.stochasticIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.barCount;
    }

}
