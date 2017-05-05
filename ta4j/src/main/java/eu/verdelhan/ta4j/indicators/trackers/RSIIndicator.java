/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.AverageGainIndicator;
import eu.verdelhan.ta4j.indicators.helpers.AverageLossIndicator;

/**
 * Relative strength index indicator.
 * <p>
 * This calculation of RSI uses traditional moving averages
 * as opposed to Wilder's accumulative moving average technique.
 *
 * <p>See reference
 * <a href="https://www.barchart.com/education/technical-indicators#/studies/std_rsi_mod">
 * RSI calculation</a>.
 *
 * @see SmoothedRSIIndicator
 */
public class RSIIndicator extends CachedIndicator<Decimal> {

    private Indicator<Decimal> averageGainIndicator;
    private Indicator<Decimal> averageLossIndicator;
    
    public RSIIndicator(Indicator<Decimal> indicator, int timeFrame) {
        this(new AverageGainIndicator(indicator, timeFrame),
                new AverageLossIndicator(indicator, timeFrame));
    }

    public RSIIndicator(Indicator<Decimal> avgGainIndicator, Indicator<Decimal> avgLossIndicator) {
        super(avgGainIndicator);
        averageGainIndicator = avgGainIndicator;
        averageLossIndicator = avgLossIndicator;
    }

    @Override
    protected Decimal calculate(int index) {
        if (index == 0) {
            return Decimal.ZERO;
        }

        // Relative strength
        Decimal averageLoss = averageLossIndicator.getValue(index);
        if (averageLoss.isZero()) {
            return Decimal.HUNDRED;
        }
        Decimal averageGain = averageGainIndicator.getValue(index);
        Decimal relativeStrength = averageGain.dividedBy(averageLoss);

        // Nominal case
        Decimal ratio = Decimal.HUNDRED.dividedBy(Decimal.ONE.plus(relativeStrength));
        return Decimal.HUNDRED.minus(ratio);
    }

}
