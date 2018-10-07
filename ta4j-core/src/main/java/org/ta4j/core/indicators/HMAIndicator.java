package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.MultiplierIndicator;
import org.ta4j.core.num.Num;

/**
 * Hull moving average (HMA) indicator.
 * </p>
 * @see <a href="http://alanhull.com/hull-moving-average">
 *     http://alanhull.com/hull-moving-average</a>
 */
public class HMAIndicator extends CachedIndicator<Num> {

    private final int barCount;

    private final WMAIndicator sqrtWma;
    
    public HMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        
        WMAIndicator halfWma = new WMAIndicator(indicator, barCount / 2);
        WMAIndicator origWma = new WMAIndicator(indicator, barCount);
        
        Indicator<Num> indicatorForSqrtWma = new DifferenceIndicator(new MultiplierIndicator(halfWma, 2), origWma);
        sqrtWma = new WMAIndicator(indicatorForSqrtWma, numOf(barCount).sqrt().intValue());
    }

    @Override
    protected Num calculate(int index) {
        return sqrtWma.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
