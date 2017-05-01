package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.RecursiveCachedIndicator;

/**
 * Average loss indicator calculated using smoothing
 * <p>
 */
public class SmoothedAverageLossIndicator extends RecursiveCachedIndicator<Decimal> {

    private final AverageLossIndicator averageLosses;
    private final Indicator<Decimal> indicator;

    private final int timeFrame;

    public SmoothedAverageLossIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.indicator = indicator;
        this.averageLosses = new AverageLossIndicator(indicator, timeFrame);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        if (index > timeFrame) {
            return getValue(index - 1)
                .multipliedBy(Decimal.valueOf(timeFrame - 1))
                .plus(calculateLoss(index))
                .dividedBy(Decimal.valueOf(timeFrame));
        }
        return averageLosses.getValue(index);
    }

    private Decimal calculateLoss(int index) {
        Decimal loss = indicator.getValue(index).minus(indicator.getValue(index - 1));
        return loss.isNegative() ? loss.abs() : Decimal.ZERO;
    }
}
