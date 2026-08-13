/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Oscillator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator</a>
 */
public class ChaikinOscillatorIndicator extends CachedIndicator<Num> {

    private final int shortBarCount;
    private final int longBarCount;
    private final transient AccumulationDistributionIndicator accumulationDistributionIndicator;
    private final transient EMAIndicator emaShort;
    private final transient EMAIndicator emaLong;

    /**
     * Constructor.
     *
     * @param series        the {@link BarSeries}
     * @param shortBarCount the bar count for {@link #emaShort} (usually 3)
     * @param longBarCount  the bar count for {@link #emaLong} (usually 10)
     */
    public ChaikinOscillatorIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(series);
        this.shortBarCount = shortBarCount;
        this.longBarCount = longBarCount;
        this.accumulationDistributionIndicator = new AccumulationDistributionIndicator(series);
        this.emaShort = new EMAIndicator(accumulationDistributionIndicator, shortBarCount);
        this.emaLong = new EMAIndicator(accumulationDistributionIndicator, longBarCount);
    }

    /**
     * Constructor with {@code shortBarCount} = 3 and {@code longBarCount} = 10.
     *
     * @param series the {@link BarSeries}
     */
    public ChaikinOscillatorIndicator(BarSeries series) {
        this(series, 3, 10);
    }

    @Override
    protected Num calculate(int index) {
        return emaShort.getValue(index).minus(emaLong.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        int emaUnstableBars = Math.max(emaShort.getCountOfUnstableBars(), emaLong.getCountOfUnstableBars());
        return accumulationDistributionIndicator.getCountOfUnstableBars() + emaUnstableBars;
    }
}
