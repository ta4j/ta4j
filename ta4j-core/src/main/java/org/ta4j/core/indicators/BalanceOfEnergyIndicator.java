/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Relative strength index energy indicator.
 * <p>
 * 7
 */
public class BalanceOfEnergyIndicator extends CachedIndicator<Num> {

    private final RunningTotalIndicator runningTotalIndicator;
    private final Indicator<Num> oscillatingIndicator;

    /**
     * Constructor.
     *
     * @param oscillatingIndicator the {@link Indicator}
     * @param timeFrame            the time frame to calculate over (-N bars to present)
     */
    public BalanceOfEnergyIndicator(Indicator<Num> oscillatingIndicator, int timeFrame, int neutralPivotValue) {
        super(oscillatingIndicator);
        this.oscillatingIndicator = oscillatingIndicator;

        var smoothedSignalIndicator = new KalmanFilterIndicator(oscillatingIndicator);
        BinaryOperationIndicator deltaFromNeutralIndicator = BinaryOperationIndicator.difference(smoothedSignalIndicator, neutralPivotValue);

        this.runningTotalIndicator = new RunningTotalIndicator(deltaFromNeutralIndicator, timeFrame);
    }

    /**
     *
     */
    public BalanceOfEnergyIndicator(RSIIndicator rsiIndicator, int timeFrame) {
        this(rsiIndicator, timeFrame, 50);
    }

    @Override
    protected Num calculate(int index) {
        return this.runningTotalIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.oscillatingIndicator.getCountOfUnstableBars();
    }
}
