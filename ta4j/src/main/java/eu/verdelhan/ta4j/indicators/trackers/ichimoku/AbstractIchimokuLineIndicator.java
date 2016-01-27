package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.HighestValueIndicator;
import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;

/**
 * The Class AbstractIchimokuLineIndicator.
 */
public abstract class AbstractIchimokuLineIndicator extends CachedIndicator<Decimal>{

    /** The period high. */
    private final Indicator<Decimal> periodHigh;

    /** The period low. */
    private final Indicator<Decimal> periodLow;

    /**
     * Instantiates a new tenkan sen indicator.
     *
     * @param series the series
     * @param timeFrame the time frame
     */
    public AbstractIchimokuLineIndicator(TimeSeries series, int timeFrame) {
        super(series);
        periodHigh = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
        periodLow = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return periodHigh.getValue(index).plus(periodLow.getValue(index).dividedBy(Decimal.TWO));
    }
}
