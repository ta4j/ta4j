/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * %B indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce</a>
 */
public class PercentBIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final BollingerBandsUpperIndicator bbu;
    private final BollingerBandsLowerIndicator bbl;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator} (usually {@code ClosePriceIndicator})
     * @param barCount  the time frame
     * @param k         the K multiplier (usually 2.0)
     */
    public PercentBIndicator(Indicator<Num> indicator, int barCount, double k) {
        super(indicator);
        this.indicator = indicator;
        BollingerBandsMiddleIndicator bbm = new BollingerBandsMiddleIndicator(new SMAIndicator(indicator, barCount));
        StandardDeviationIndicator sd = new StandardDeviationIndicator(indicator, barCount);
        this.bbu = new BollingerBandsUpperIndicator(bbm, sd, getBarSeries().numFactory().numOf(k));
        this.bbl = new BollingerBandsLowerIndicator(bbm, sd, getBarSeries().numFactory().numOf(k));
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue(index);
        Num upValue = bbu.getValue(index);
        Num lowValue = bbl.getValue(index);
        return value.minus(lowValue).dividedBy(upValue.minus(lowValue));
    }

    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(indicator.getCountOfUnstableBars(), bbu.getCountOfUnstableBars());
        return Math.max(unstableBars, bbl.getCountOfUnstableBars());
    }
}
