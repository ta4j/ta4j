/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Intraday Intensity Index.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/i/intradayintensityindex.asp">https://www.investopedia.com/terms/i/intradayintensityindex.asp</a>
 */
public class IIIIndicator extends CachedIndicator<Num> {

    private final ClosePriceIndicator closePriceIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final VolumeIndicator volumeIndicator;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public IIIIndicator(BarSeries series) {
        super(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.volumeIndicator = new VolumeIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == getBarSeries().getBeginIndex()) {
            return getBarSeries().numFactory().zero();
        }
        final Num doubledClosePrice = getBarSeries().numFactory()
                .two()
                .multipliedBy(closePriceIndicator.getValue(index));
        final Num high = highPriceIndicator.getValue(index);
        final Num low = lowPriceIndicator.getValue(index);
        final Num highMinusLow = high.minus(low);
        final Num highPlusLow = high.plus(low);

        return doubledClosePrice.minus(highPlusLow)
                .dividedBy(highMinusLow.multipliedBy(volumeIndicator.getValue(index)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
