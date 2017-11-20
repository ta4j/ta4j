/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.GainIndicator;
import org.ta4j.core.indicators.helpers.LossIndicator;

/**
 * Chande Momentum Oscillator indicator.
 * <p></p>
 * @see <a href="http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/">
 *     http://tradingsim.com/blog/chande-momentum-oscillator-cmo-technical-indicator/</a>
 * @see <a href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp">
 *     href="http://www.investopedia.com/terms/c/chandemomentumoscillator.asp"</a>
 */
public class CMOIndicator extends CachedIndicator<Decimal> {

    private final GainIndicator gainIndicator;

    private final LossIndicator lossIndicator;

    private final int timeFrame;

    /**
     * Constructor.
     *
     * @param indicator a price indicator
     * @param timeFrame the time frame
     */
    public CMOIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.gainIndicator = new GainIndicator(indicator);
        this.lossIndicator = new LossIndicator(indicator);
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal sumOfGains = Decimal.ZERO;
        for (int i = Math.max(1, index - timeFrame + 1); i <= index; i++) {
            sumOfGains = sumOfGains.plus(gainIndicator.getValue(i));
        }
        Decimal sumOfLosses = Decimal.ZERO;
        for (int i = Math.max(1, index - timeFrame + 1); i <= index; i++) {
            sumOfLosses = sumOfLosses.plus(lossIndicator.getValue(i));
        }
        return sumOfGains.minus(sumOfLosses)
                .dividedBy(sumOfGains.plus(sumOfLosses))
                .multipliedBy(Decimal.HUNDRED);
    }
}
