package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * WMA indicator.
 * </p>
 */
public class WMAIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = -1610206345404758687L;
    private final int barCount;
    private final Indicator<Num> indicator;

    public WMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
        }
        Num value = numOf(0);
        if (index - barCount < 0) {

            for (int i = index + 1; i > 0; i--) {
                value = value.plus(numOf(i).multipliedBy(indicator.getValue(i - 1)));
            }
            return value.dividedBy(numOf(((index + 1) * (index + 2)) / 2));
        }

        int actualIndex = index;
        for (int i = barCount; i > 0; i--) {
            value = value.plus(numOf(i).multipliedBy(indicator.getValue(actualIndex)));
            actualIndex--;
        }
        return value.dividedBy(numOf((barCount * (barCount + 1)) / 2));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
