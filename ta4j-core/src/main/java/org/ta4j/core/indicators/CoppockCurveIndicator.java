/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
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
    public int getCountOfUnstableBars() {
        return 0;
    }
}
