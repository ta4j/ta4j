package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Stochastic RSI Indicator.
 * 
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 */
public class StochasticRSIIndicator extends CachedIndicator<Num> {

    private final RSIIndicator rsi;
    private final LowestValueIndicator minRsi;
    private final HighestValueIndicator maxRsi;

    /**
     * Constructor.  In most cases, this should be used to avoid confusion
     * over what Indicator parameters should be used.
     * @param series the series
     * @param barCount the time frame
     */
    public StochasticRSIIndicator(TimeSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     * @param indicator the Indicator, in practice is always a ClosePriceIndicator.
     * @param barCount the time frame
     */
    public StochasticRSIIndicator(Indicator<Num> indicator, int barCount) {
        this(new RSIIndicator(indicator, barCount), barCount);
    }

    /**
     * Constructor.
     * @param rsiIndicator the rsi indicator
     * @param barCount the time frame
     */
    public StochasticRSIIndicator(RSIIndicator rsiIndicator, int barCount) {
        super(rsiIndicator);
        this.rsi = rsiIndicator;
        minRsi = new LowestValueIndicator(rsiIndicator, barCount);
        maxRsi = new HighestValueIndicator(rsiIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num minRsiValue = minRsi.getValue(index);
        return rsi.getValue(index).minus(minRsiValue)
                .dividedBy(maxRsi.getValue(index).minus(minRsiValue));
    }

}
