/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * ADX indicator.
 *
 * <p>
 * Part of the Directional Movement System.
 *
 * @see <a href=
 *      "https://www.investopedia.com/articles/trading/07/adx-trend-indicator.asp">https://www.investopedia.com/articles/trading/07/adx-trend-indicator.asp</a>
 */
public class ADXIndicator extends CachedIndicator<Num> {

    private final int diBarCount;
    private final int adxBarCount;
    private final DXIndicator dxIndicator;
    private final MMAIndicator averageDXIndicator;

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param diBarCount  the bar count for {@link DXIndicator}
     * @param adxBarCount the bar count for {@link #averageDXIndicator}
     */
    public ADXIndicator(BarSeries series, int diBarCount, int adxBarCount) {
        super(series);
        this.diBarCount = diBarCount;
        this.adxBarCount = adxBarCount;
        this.dxIndicator = new DXIndicator(series, diBarCount);
        this.averageDXIndicator = new MMAIndicator(dxIndicator, adxBarCount);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the bar count for {@link DXIndicator} and
     *                 {@link #averageDXIndicator}
     */
    public ADXIndicator(BarSeries series, int barCount) {
        this(series, barCount, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageDXIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return dxIndicator.getCountOfUnstableBars() + averageDXIndicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}
