/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * The volume-weighted average price (VWAP) Indicator.
 *
 * @see <a href=
 *      "http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *      http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:vwap_intraday</a>
 * @see <a href="https://en.wikipedia.org/wiki/Volume-weighted_average_price">
 *      https://en.wikipedia.org/wiki/Volume-weighted_average_price</a>
 */
public class VWAPIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> typicalPrice;
    private final Indicator<Num> volume;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public VWAPIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.typicalPrice = new TypicalPriceIndicator(series);
        this.volume = new VolumeIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index <= 0) {
            return typicalPrice.getValue(index);
        }
        int startIndex = Math.max(0, index - barCount + 1);
        final var zero = getBarSeries().numFactory().zero();
        Num cumulativeTPV = zero;
        Num cumulativeVolume = zero;
        for (int i = startIndex; i <= index; i++) {
            Num currentVolume = volume.getValue(i);
            cumulativeTPV = cumulativeTPV.plus(typicalPrice.getValue(i).multipliedBy(currentVolume));
            cumulativeVolume = cumulativeVolume.plus(currentVolume);
        }
        return cumulativeTPV.dividedBy(cumulativeVolume);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
