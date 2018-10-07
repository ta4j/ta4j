package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Difference Change Indicator.
 * <p>
 * Get the difference in percentage from the last time the threshold was reached.
 * <p>
 * Or if you don't pass the threshold you will always just get the difference percentage from the precious value.
 * </p>
 */
public class DifferencePercentage extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num percentageThreshold;
    private final Num hundred;
    private Num lastNotification;

    public DifferencePercentage(Indicator<Num> indicator) {
        this(indicator, indicator.numOf(0));
    }

    public DifferencePercentage(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, indicator.numOf(percentageThreshold));
    }

    public DifferencePercentage(Indicator<Num> indicator, Num percentageThreshold) {
        super(indicator);
        this.indicator = indicator;
        this.percentageThreshold = percentageThreshold;
        hundred = numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue((index));
        if (lastNotification == null) {
            lastNotification = value;
            return NaN.NaN;
        }

        Num changeFraction = value.dividedBy(lastNotification);
        Num changePercentage = fractionToPercentage(changeFraction);

        if (changePercentage.abs().isGreaterThanOrEqual(percentageThreshold)) {
            lastNotification = value;
        }

        return changePercentage;
    }

    private Num fractionToPercentage(Num changeFraction) {
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }
}
