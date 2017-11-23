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
 * Relative strength index indicator.
 * <p>
 * Computed using original Welles Wilder formula.
 */
public class RSIIndicator extends CachedIndicator<Decimal> {

    private final MMAIndicator averageGainIndicator;
    private final MMAIndicator averageLossIndicator;

    public RSIIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.averageGainIndicator = new MMAIndicator(new GainIndicator(indicator), timeFrame);
        this.averageLossIndicator = new MMAIndicator(new LossIndicator(indicator), timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        // compute relative strength
        Decimal averageGain = averageGainIndicator.getValue(index);
        Decimal averageLoss = averageLossIndicator.getValue(index);
        if (averageLoss.isZero()) {
            if (averageGain.isZero()) {
                return Decimal.ZERO;
            } else {
                return Decimal.HUNDRED;
            }
        }
        Decimal relativeStrength = averageGain.dividedBy(averageLoss);
        // compute relative strength index
        return Decimal.HUNDRED.minus(Decimal.HUNDRED.dividedBy(Decimal.ONE.plus(relativeStrength)));
    }
}
