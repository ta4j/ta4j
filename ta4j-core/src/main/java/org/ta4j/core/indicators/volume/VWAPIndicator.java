/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
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
public class VWAPIndicator extends AbstractVWAPIndicator {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     *
     * @since 0.19
     */
    public VWAPIndicator(BarSeries series, int barCount) {
        this(new TypicalPriceIndicator(series), new VolumeIndicator(series), barCount);
    }

    /**
     * Constructor with explicitly supplied price and volume indicators.
     *
     * @param priceIndicator  the price indicator (for example typical price)
     * @param volumeIndicator the volume indicator
     * @param barCount        the time frame (must be {@code > 0})
     *
     * @since 0.19
     */
    public VWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int barCount) {
        super(priceIndicator, volumeIndicator);
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        this.barCount = barCount;
    }

    /**
     * Resolves window start index.
     */
    @Override
    protected int resolveWindowStartIndex(int index) {
        return index - barCount + 1;
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(priceIndicator.getCountOfUnstableBars(),
                volumeIndicator.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
