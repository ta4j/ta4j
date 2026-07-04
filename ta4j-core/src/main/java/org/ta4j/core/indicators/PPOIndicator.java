/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

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

    private final Indicator<Num> indicator;
    private final transient EMAIndicator shortTermEma;
    private final transient EMAIndicator longTermEma;

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
        this(validatedConfig(indicator, 12, 26));
    }

    /**
     * Constructor.
     *
     * @param indicator     the indicator
     * @param shortBarCount the short time frame
     * @param longBarCount  the long time frame
     */
    public PPOIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        this(validatedConfig(indicator, shortBarCount, longBarCount));
    }

    private PPOIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.shortTermEma = config.shortTermEma();
        this.longTermEma = config.longTermEma();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        Indicator<Num> validatedIndicator = Objects.requireNonNull(indicator, "indicator must not be null");
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term barCount must be greater than short term barCount");
        }
        EMAIndicator shortTermEma = new EMAIndicator(validatedIndicator, shortBarCount);
        EMAIndicator longTermEma = new EMAIndicator(validatedIndicator, longBarCount);
        return new Config(validatedIndicator, shortTermEma, longTermEma);
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
        int emaUnstableBars = Math.max(shortTermEma.getCountOfUnstableBars(), longTermEma.getCountOfUnstableBars());
        return indicator.getCountOfUnstableBars() + emaUnstableBars;
    }

    private record Config(Indicator<Num> indicator, EMAIndicator shortTermEma, EMAIndicator longTermEma) {
    }
}
