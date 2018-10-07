package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Percentage price oscillator (PPO) indicator. <br/>
 * Aka. MACD Percentage Price Oscillator (MACD-PPO).
 * </p>
 */
public class PPOIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = -4337731034816094765L;
    
    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with shortBarCount "12" and longBarCount "26".
     * 
     * @param indicator the indicator
     */
    public PPOIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26);
    }
    
    /**
     * Constructor.
     * 
     * @param indicator the indicator
     * @param shortBarCount the short time frame
     * @param longBarCount the long time frame
     */
    public PPOIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMAIndicator(indicator, shortBarCount);
        longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortEmaValue = shortTermEma.getValue(index);
        Num longEmaValue = longTermEma.getValue(index);
        return shortEmaValue.minus(longEmaValue)
                .dividedBy(longEmaValue)
                .multipliedBy(numOf(100));
    }
}
