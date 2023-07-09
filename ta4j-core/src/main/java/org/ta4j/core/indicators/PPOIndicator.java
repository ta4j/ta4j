/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
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
        return shortEmaValue.minus(longEmaValue).dividedBy(longEmaValue).multipliedBy(hundred());
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
