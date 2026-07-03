/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

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

    private final Indicator<Num> indicator;
    private final transient EMAIndicator shortTermEma;
    private final transient EMAIndicator longTermEma;
    private final int shortBarCount;
    private final int longBarCount;

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
        this(validatedConfig(indicator, 12, 26));
    }

    /**
     * Constructor.
     *
     * @param indicator     the {@link Indicator}
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     */
    public MACDIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        this(validatedConfig(indicator, shortBarCount, longBarCount));
    }

    private MACDIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.shortBarCount = config.shortBarCount();
        this.longBarCount = config.longBarCount();
        this.shortTermEma = config.shortTermEma();
        this.longTermEma = config.longTermEma();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        Indicator<Num> validatedIndicator = Objects.requireNonNull(indicator, "indicator must not be null");
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        EMAIndicator shortTermEma = new EMAIndicator(validatedIndicator, shortBarCount);
        EMAIndicator longTermEma = new EMAIndicator(validatedIndicator, longBarCount);
        return new Config(validatedIndicator, shortTermEma, longTermEma, shortBarCount, longBarCount);
    }

    /**
     * @return the Short term EMA indicator
     */
    public EMAIndicator getShortTermEma() {
        return new EMAIndicator(indicator, shortBarCount);
    }

    /**
     * @return the Long term EMA indicator
     */
    public EMAIndicator getLongTermEma() {
        return new EMAIndicator(indicator, longBarCount);
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
        int emaUnstableBars = Math.max(shortTermEma.getCountOfUnstableBars(), longTermEma.getCountOfUnstableBars());
        return indicator.getCountOfUnstableBars() + emaUnstableBars;
    }

    private record Config(Indicator<Num> indicator, EMAIndicator shortTermEma, EMAIndicator longTermEma,
            int shortBarCount, int longBarCount) {
    }
}
