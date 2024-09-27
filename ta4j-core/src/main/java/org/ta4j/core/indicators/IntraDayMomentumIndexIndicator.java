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
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.candles.RealBodyIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * IntraDay Momentum Index Indicator.
 * <p>
 * The IntraDay Momentum Index is a measure of the security's strength of trend.
 * It uses the difference between the open and close prices of each bar to
 * calculate momentum. For more information, check: <a href=
 * "https://library.tradingtechnologies.com/trade/chrt-ti-intraday-momentum-index.html"></a>
 * </p>
 */
public class IntraDayMomentumIndexIndicator extends CachedIndicator<Num> {
    private final SMAIndicator averageCloseOpenDiff;
    private final SMAIndicator averageOpenCloseDiff;

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public IntraDayMomentumIndexIndicator(BarSeries series, int barCount) {
        super(series);

        // Calculate the real body of the bars (close - open)
        RealBodyIndicator realBody = new RealBodyIndicator(series);

        // Transform the real body into close-open and open-close differences
        TransformIndicator closeOpenDiff = TransformIndicator.max(realBody, 0);
        TransformIndicator openCloseDiff = TransformIndicator.abs(TransformIndicator.min(realBody, 0));

        // Calculate the SMA of the differences
        this.averageCloseOpenDiff = new SMAIndicator(closeOpenDiff, barCount);
        this.averageOpenCloseDiff = new SMAIndicator(openCloseDiff, barCount);

        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN for unstable bars
        if (index < this.getUnstableBars()) {
            return NaN;
        }

        // Calculate the average values of the differences
        Num avgCloseOpenValue = this.averageCloseOpenDiff.getValue(index);
        Num avgOpenCloseValue = this.averageOpenCloseDiff.getValue(index);

        // Calculate the momentum index
        return avgCloseOpenValue.dividedBy((avgCloseOpenValue.plus(avgOpenCloseValue)))
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }
}
