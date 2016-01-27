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
 * The Class TenkanSenIndicator.
 */
public class TenkanSenIndicator extends CachedIndicator<Decimal> {

    /** The period hight. */
    private final Indicator<Decimal> periodHight;
    
    /** The period low. */
    private final Indicator<Decimal> periodLow;
    /**
     * Instantiates a new tenkan sen indicator.
     *
     * @param series the series
     */
    public TenkanSenIndicator(TimeSeries series) {
        this(series, 9);
    }
    
    /**
     * Instantiates a new tenkan sen indicator.
     *
     * @param series the series
     * @param timeFrame the time frame
     */
    public TenkanSenIndicator(TimeSeries series, int timeFrame) {
        super(series);
        periodHight = new HighestValueIndicator(new MaxPriceIndicator(series), timeFrame);
        periodLow = new LowestValueIndicator(new MinPriceIndicator(series), timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return periodHight.getValue(index).plus(periodLow.getValue(index).dividedBy(Decimal.TWO));
    }

}
