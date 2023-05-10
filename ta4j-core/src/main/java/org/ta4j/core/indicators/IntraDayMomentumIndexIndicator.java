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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.candles.RealBodyIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

public class IntraDayMomentumIndexIndicator extends CachedIndicator<Num> {
    private final SMAIndicator averageCloseOpenDiff;
    private final SMAIndicator averageOpenCloseDiff;

    private final int barCount;

    public IntraDayMomentumIndexIndicator(BarSeries series, int barCount) {
        super(series);

        RealBodyIndicator realBody = new RealBodyIndicator(series);
        TransformIndicator closeOpenDiff = TransformIndicator.max(realBody, 0);
        TransformIndicator openCloseDiff = TransformIndicator.abs(TransformIndicator.min(realBody, 0));
        this.averageCloseOpenDiff = new SMAIndicator(closeOpenDiff, barCount);
        this.averageOpenCloseDiff = new SMAIndicator(openCloseDiff, barCount);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index < this.getUnstableBars()) {
            return NaN;
        }

        Num avgCloseOpenValue = this.averageCloseOpenDiff.getValue(index);
        Num avgOpenCloseValue = this.averageOpenCloseDiff.getValue(index);

        return avgCloseOpenValue.dividedBy((avgCloseOpenValue.plus(avgOpenCloseValue))).multipliedBy(hundred());
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }
}
