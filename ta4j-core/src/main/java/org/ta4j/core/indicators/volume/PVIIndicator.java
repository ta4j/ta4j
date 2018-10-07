package org.ta4j.core.indicators.volume;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Positive Volume Index (PVI) indicator.
 * </p>
 * @see <a href="http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92">
 *     http://www.metastock.com/Customer/Resources/TAAZ/Default.aspx?p=92</a>
 * @see <a href="http://www.investopedia.com/terms/p/pvi.asp">
 *     http://www.investopedia.com/terms/p/pvi.asp</a>
 */
public class PVIIndicator extends RecursiveCachedIndicator<Num> {

    public PVIIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(1000);
        }

        Bar currentBar = getTimeSeries().getBar(index);
        Bar previousBar = getTimeSeries().getBar(index - 1);
        Num previousValue = getValue(index - 1);

        if (currentBar.getVolume().isGreaterThan(previousBar.getVolume())) {
            Num currentPrice = currentBar.getClosePrice();
            Num previousPrice = previousBar.getClosePrice();
            Num priceChangeRatio = currentPrice.minus(previousPrice).dividedBy(previousPrice);
            return previousValue.plus(priceChangeRatio.multipliedBy(previousValue));
        }
        return previousValue;
    }

}