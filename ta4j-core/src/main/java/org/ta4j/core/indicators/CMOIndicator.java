package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;
import org.ta4j.core.num.Num;

/**
 * Chande Momentum Oscillator indicator.
 * </p>
 * @see <a href="http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/">
 *     http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/</a>
 * @see <a href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp">
 *     href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp"</a>
 */
public class CMOIndicator extends CachedIndicator<Num> {

    private final GainIndicator gainIndicator;
    private final LossIndicator lossIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator a price indicator
     * @param barCount the time frame
     */
    public CMOIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.gainIndicator = new GainIndicator(indicator);
        this.lossIndicator = new LossIndicator(indicator);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sumOfGains = numOf(0);
        for (int i = Math.max(1, index - barCount + 1); i <= index; i++) {
            sumOfGains = sumOfGains.plus(gainIndicator.getValue(i));
        }
        Num sumOfLosses = numOf(0);
        for (int i = Math.max(1, index - barCount + 1); i <= index; i++) {
            sumOfLosses = sumOfLosses.plus(lossIndicator.getValue(i));
        }
        return sumOfGains.minus(sumOfLosses)
                .dividedBy(sumOfGains.plus(sumOfLosses))
                .multipliedBy(numOf(100));
    }
}
