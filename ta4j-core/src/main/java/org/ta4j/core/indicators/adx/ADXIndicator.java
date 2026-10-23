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
    private final transient DXIndicator dxIndicator;
    private final transient MMAIndicator averageDXIndicator;

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param diBarCount  the bar count for {@link DXIndicator}
     * @param adxBarCount the bar count for {@link #averageDXIndicator}
     */
    public ADXIndicator(BarSeries series, int diBarCount, int adxBarCount) {
        this(diBarCount, adxBarCount, new DXIndicator(series, diBarCount));
    }

    private ADXIndicator(int diBarCount, int adxBarCount, DXIndicator dxIndicator) {
        this(diBarCount, adxBarCount, dxIndicator, new MMAIndicator(dxIndicator, adxBarCount));
    }

    private ADXIndicator(int diBarCount, int adxBarCount, DXIndicator dxIndicator, MMAIndicator averageDXIndicator) {
        super(averageDXIndicator);
        this.diBarCount = diBarCount;
        this.adxBarCount = adxBarCount;
        this.dxIndicator = dxIndicator;
        this.averageDXIndicator = averageDXIndicator;
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
        // The outer average does not read DX while its own chain is warming up.
        dxIndicator.getValue(index);
        return averageDXIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return dxIndicator.getCountOfUnstableBars() + averageDXIndicator.getCountOfUnstableBars();
    }

    /**
     * @return {@code true}, because the Wilder averages retain full-history state
     * @since 0.27.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}
