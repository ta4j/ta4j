/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Moving average convergence divergence (MACDIndicator) indicator (also called
 * "MACD Absolute Price Oscillator (APO)").
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_average_convergence_divergence_macd">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_average_convergence_divergence_macd</a>
 */
public class MACDIndicator extends CachedIndicator<Num> {

    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * </ul>
     *
     * @param indicator the {@link Indicator}
     */
    public MACDIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26);
    }

    /**
     * Constructor.
     *
     * @param indicator     the {@link Indicator}
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     */
    public MACDIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        this.shortTermEma = new EMAIndicator(indicator, shortBarCount);
        this.longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    /**
     * @return the Short term EMA indicator
     */
    public EMAIndicator getShortTermEma() {
        return shortTermEma;
    }

    /**
     * @return the Long term EMA indicator
     */
    public EMAIndicator getLongTermEma() {
        return longTermEma;
    }

    /**
     * @param barCount of signal line
     * @return signal line for this MACD indicator
     */
    public EMAIndicator getSignalLine(int barCount) {
        return new EMAIndicator(this, barCount);
    }

    /**
     * @param barCount of signal line
     * @return histogram of this MACD indicator
     */
    public NumericIndicator getHistogram(int barCount) {
        return NumericIndicator.of(this).minus(getSignalLine(barCount));
    }

    @Override
    protected Num calculate(int index) {
        return shortTermEma.getValue(index).minus(longTermEma.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
