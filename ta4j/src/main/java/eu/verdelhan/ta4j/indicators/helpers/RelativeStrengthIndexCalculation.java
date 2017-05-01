package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Relative Strength Index calculation
 * <p>
 */
public class RelativeStrengthIndexCalculation extends CachedIndicator<Decimal> {

    private Indicator<Decimal> averageGainIndicator;
    private Indicator<Decimal> averageLossIndicator;

    public RelativeStrengthIndexCalculation(Indicator<Decimal> avgGain, Indicator<Decimal> avgLoss) {
        // TODO: check if up series is equal to low series
        super(avgGain);
        averageGainIndicator = avgGain;
        averageLossIndicator = avgLoss;
    }

    @Override
    protected Decimal calculate(int index) {
        if (index == 0) {
            return Decimal.ZERO;
        }

        // Relative strength
        Decimal averageLoss = averageLossIndicator.getValue(index);
        if (averageLoss.isZero()) {
            return Decimal.HUNDRED;
        }
        Decimal averageGain = averageGainIndicator.getValue(index);
        Decimal relativeStrength = averageGain.dividedBy(averageLoss);

        // Nominal case
        Decimal ratio = Decimal.HUNDRED.dividedBy(Decimal.ONE.plus(relativeStrength));
        return Decimal.HUNDRED.minus(ratio);
    }
}
