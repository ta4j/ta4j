/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Positive Volume Index (PVI) indicator.
 *
 * @see <a href=
 *      "http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92">
 *      http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92</a>
 * @see <a href="http://www.investopedia.com/terms/p/pvi.asp">
 *      http://www.investopedia.com/terms/p/pvi.asp</a>
 */
public class PVIIndicator extends RecursiveCachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public PVIIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().thousand();
        }

        Bar currentBar = getBarSeries().getBar(index);
        Bar previousBar = getBarSeries().getBar(index - 1);
        Num previousValue = getValue(index - 1);

        if (currentBar.getVolume().isGreaterThan(previousBar.getVolume())) {
            Num currentPrice = currentBar.getClosePrice();
            Num previousPrice = previousBar.getClosePrice();
            Num priceChangeRatio = currentPrice.minus(previousPrice).dividedBy(previousPrice);
            return previousValue.plus(priceChangeRatio.multipliedBy(previousValue));
        }
        return previousValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
