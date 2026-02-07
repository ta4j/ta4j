/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bollinger BandWidth indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width</a>
 */
public class BollingerBandWidthIndicator extends CachedIndicator<Num> {

    private final BollingerBandsUpperIndicator bbu;
    private final BollingerBandsMiddleIndicator bbm;
    private final BollingerBandsLowerIndicator bbl;

    /**
     * Constructor.
     *
     * @param bbu the upper band Indicator.
     * @param bbm the middle band Indicator. Typically an {@code SMAIndicator} is
     *            used.
     * @param bbl the lower band Indicator.
     */
    public BollingerBandWidthIndicator(BollingerBandsUpperIndicator bbu, BollingerBandsMiddleIndicator bbm,
            BollingerBandsLowerIndicator bbl) {
        super(bbm.getBarSeries());
        this.bbu = bbu;
        this.bbm = bbm;
        this.bbl = bbl;
    }

    @Override
    protected Num calculate(int index) {
        return bbu.getValue(index)
                .minus(bbl.getValue(index))
                .dividedBy(bbm.getValue(index))
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        int unstableBars = Math.max(bbu.getCountOfUnstableBars(), bbm.getCountOfUnstableBars());
        return Math.max(unstableBars, bbl.getCountOfUnstableBars());
    }
}
