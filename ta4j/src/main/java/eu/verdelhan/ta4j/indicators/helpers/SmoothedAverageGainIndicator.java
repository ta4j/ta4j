package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.RecursiveCachedIndicator;

/**
 * Average gain indicator calculated using smoothing
 * <p>
 */
public class SmoothedAverageGainIndicator extends RecursiveCachedIndicator<Decimal> {

    private final AverageGainIndicator averageGains;
    private final Indicator<Decimal> indicator;

    private final int timeFrame;

    public SmoothedAverageGainIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.averageGains = new AverageGainIndicator(indicator, timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        if(index > timeFrame) {
            return getValue(index - 1)
                .multipliedBy(Decimal.valueOf(timeFrame - 1))
                .plus(calculateGain(index))
                .dividedBy(Decimal.valueOf(timeFrame));
        }
        return averageGains.getValue(index);
    }

    private Decimal calculateGain(int index){
        Decimal gain = indicator.getValue(index).minus(indicator.getValue(index - 1));
        return gain.isPositive() ? gain : Decimal.ZERO;
    }
}
