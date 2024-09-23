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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.num.Num;

/**
 * Coppock Curve indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:coppock_curve">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:coppock_curve</a>
 */
public class CoppockCurveIndicator extends CachedIndicator<Num> {

    private final WMAIndicator wma;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code longRoCBarCount} = 14
     * <li>{@code shortRoCBarCount} = 11
     * <li>{@code wmaBarCount} = 10
     * </ul>
     *
     * @param indicator the indicator
     */
    public CoppockCurveIndicator(Indicator<Num> indicator) {
        this(indicator, 14, 11, 10);
    }

    /**
     * Constructor.
     *
     * @param indicator        the indicator (usually close price)
     * @param longRoCBarCount  the time frame for long term RoC
     * @param shortRoCBarCount the time frame for short term RoC
     * @param wmaBarCount      the time frame (for WMA)
     */
    public CoppockCurveIndicator(Indicator<Num> indicator, int longRoCBarCount, int shortRoCBarCount, int wmaBarCount) {
        super(indicator);
        SumIndicator sum = new SumIndicator(new ROCIndicator(indicator, longRoCBarCount),
                new ROCIndicator(indicator, shortRoCBarCount));
        this.wma = new WMAIndicator(sum, wmaBarCount);
    }

    @Override
    protected Num calculate(int index) {
        return wma.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
