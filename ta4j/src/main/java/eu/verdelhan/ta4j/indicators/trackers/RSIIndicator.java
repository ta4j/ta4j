/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.indicators.helpers.AverageGainIndicator;
import eu.verdelhan.ta4j.indicators.helpers.AverageLossIndicator;

/**
 * Relative strength index indicator.
 * <p>
 */
public class RSIIndicator implements Indicator<TADecimal> {

    private AverageGainIndicator averageGainIndicator;

    private AverageLossIndicator averageLossIndicator;

    private final int timeFrame;

    public RSIIndicator(Indicator<? extends TADecimal> indicator, int timeFrame) {
        this.timeFrame = timeFrame;
        averageGainIndicator = new AverageGainIndicator(indicator, timeFrame);
        averageLossIndicator = new AverageLossIndicator(indicator, timeFrame);
    }

    @Override
    public TADecimal getValue(int index) {
        return TADecimal.HUNDRED
                .minus(TADecimal.HUNDRED.dividedBy(TADecimal.ONE.plus(relativeStrength(index))));
    }

    @Override
    public String toString() {
        return getClass().getName() + " timeFrame: " + timeFrame;
    }

    /**
     * @param index
     * @return the relative strength
     */
    private TADecimal relativeStrength(int index) {
        if (index == 0) {
            return TADecimal.ZERO;
        }
        TADecimal averageGain = averageGainIndicator.getValue(index);
        TADecimal averageLoss = averageLossIndicator.getValue(index);
        if (averageLoss.isZero()) {
            // Should be replaced by a POSITIVE_INFINITY-like
            return TADecimal.HUNDRED;
        } else {
            return averageGain.dividedBy(averageLoss);
        }
    }
}
