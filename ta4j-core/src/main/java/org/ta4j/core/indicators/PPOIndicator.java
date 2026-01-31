/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Percentage price oscillator (PPO) indicator (also called "MACD Percentage
 * Price Oscillator (MACD-PPO)").
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/ppo.asp">https://www.investopedia.com/terms/p/ppo.asp</a>
 */
public class PPOIndicator extends CachedIndicator<Num> {

    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * </ul>
     *
     * @param indicator the indicator
     */
    public PPOIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26);
    }

    /**
     * Constructor.
     *
     * @param indicator     the indicator
     * @param shortBarCount the short time frame
     * @param longBarCount  the long time frame
     */
    public PPOIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term barCount must be greater than short term barCount");
        }
        this.shortTermEma = new EMAIndicator(indicator, shortBarCount);
        this.longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortEmaValue = shortTermEma.getValue(index);
        Num longEmaValue = longTermEma.getValue(index);
        return shortEmaValue.minus(longEmaValue)
                .dividedBy(longEmaValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
