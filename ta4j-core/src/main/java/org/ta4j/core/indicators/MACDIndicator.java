package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Moving average convergence divergence (MACDIndicator) indicator. <br/>
 * Aka. MACD Absolute Price Oscillator (APO).
 * </p>
 * see
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_average_convergence_divergence_macd
 */
public class MACDIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = -6899062131135971403L;

    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with shortBarCount "12" and longBarCount "26".
     *
     * @param indicator the indicator
     */
    public MACDIndicator(Indicator<Num> indicator) {
       this(indicator, 12, 26);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount the long time frame (normally 26)
     */
    public MACDIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
        shortTermEma = new EMAIndicator(indicator, shortBarCount);
        longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    @Override
    protected Num calculate(int index) {
        return shortTermEma.getValue(index).minus(longTermEma.getValue(index));
    }
}
