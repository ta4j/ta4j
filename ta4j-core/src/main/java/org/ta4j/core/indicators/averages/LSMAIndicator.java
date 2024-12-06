/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Least Squares Moving Average (LSMA) Indicator.
 *
 * Least Squares Moving Average (LSMA), also known as the Linear Regression Line
 * or End Point Moving Average, is a unique type of moving average that
 * minimizes the sum of squared differences between data points and a regression
 * line. Unlike other moving averages that calculate a simple or weighted
 * average of prices, the LSMA fits a straight line to the price data over a
 * specific period and uses the end point of that line as the average. This
 * makes LSMA effective at forecasting trends while reducing lag, making it
 * popular in technical analysis.
 *
 * The offset parameter projects the regression line forward or backward.
 */
public class LSMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final int offset;
    private final NumFactory numFactory;
    private final Num avgTime;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the moving average time window
     * @param offset    the offset to apply to the indicator
     */
    public LSMAIndicator(Indicator<Num> indicator, int barCount, int offset) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.barCount = barCount;
        this.offset = offset;
        this.numFactory = indicator.getBarSeries().numFactory();

        Num sumTime = numFactory.zero();
        for (int i = 1; i <= barCount; i++) {
            sumTime = sumTime.plus(numFactory.numOf(i));
        }
        this.avgTime = sumTime.dividedBy(numFactory.numOf(barCount));

    }

    @Override
    protected Num calculate(int index) {
        if (index < barCount - 1) {
            return indicator.getValue(index);
        }

        // Calculate AvgPrice
        RunningTotalIndicator sumPriceIndicator = new RunningTotalIndicator(indicator, barCount);

        Num sumPrice = sumPriceIndicator.getValue(index);
        Num avgPrice = sumPrice.dividedBy(numFactory.numOf(barCount));

        // Calculate sx and sy
        Num zero = numFactory.zero();
        Num sx = zero;
        Num sy = zero;
        for (int i = 1; i <= barCount; i++) {
            Num timeDeviation = numFactory.numOf(i).minus(avgTime);
            Num priceDeviation = indicator.getValue(index - (i - 1)).minus(avgPrice);

            sx = sx.plus(timeDeviation.multipliedBy(priceDeviation));
            sy = sy.plus(timeDeviation.multipliedBy(timeDeviation));
        }

        // Step 4: Calculate slope (m) and intercept (b)
        Num slope = sx.dividedBy(sy);
        Num intercept = avgPrice.minus(slope.multipliedBy(avgTime));

        // Step 5: Calculate LSMA (current bar or projected)
        Num x = numFactory.numOf(barCount + 1);
        return slope.multipliedBy(x).plus(intercept);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " offset: " + offset;
    }
}
