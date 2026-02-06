/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.keltner;

import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (upper line) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelUpperIndicator extends CachedIndicator<Num> {

    private final KeltnerChannelMiddleIndicator keltnerMiddleIndicator;
    private transient ATRIndicator averageTrueRangeIndicator;
    private final int atrBarCount;
    private final Num ratio;

    /**
     * Constructor.
     *
     * @param middle      the {@link #keltnerMiddleIndicator}
     * @param ratio       the {@link #ratio}
     * @param barCountATR the bar count for the {@link ATRIndicator}
     */
    public KeltnerChannelUpperIndicator(KeltnerChannelMiddleIndicator middle, double ratio, int barCountATR) {
        this(middle, new ATRIndicator(middle.getBarSeries(), barCountATR), ratio);
    }

    /**
     * Constructor.
     *
     * @param middle the {@link #keltnerMiddleIndicator}
     * @param atr    the {@link ATRIndicator}
     * @param ratio  the {@link #ratio}
     */
    public KeltnerChannelUpperIndicator(KeltnerChannelMiddleIndicator middle, ATRIndicator atr, double ratio) {
        super(middle.getBarSeries());
        this.keltnerMiddleIndicator = middle;
        this.averageTrueRangeIndicator = atr;
        this.atrBarCount = atr.getBarCount();
        this.ratio = getBarSeries().numFactory().numOf(ratio);
    }

    @Override
    protected Num calculate(int index) {
        return keltnerMiddleIndicator.getValue(index)
                .plus(ratio.multipliedBy(getAverageTrueRangeIndicator().getValue(index)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(keltnerMiddleIndicator.getCountOfUnstableBars(),
                getAverageTrueRangeIndicator().getCountOfUnstableBars());
    }

    /** @return the bar count of {@link #keltnerMiddleIndicator} */
    public int getBarCount() {
        return keltnerMiddleIndicator.getBarCount();
    }

    private ATRIndicator getAverageTrueRangeIndicator() {
        if (averageTrueRangeIndicator == null) {
            averageTrueRangeIndicator = new ATRIndicator(getBarSeries(), atrBarCount);
        }
        return averageTrueRangeIndicator;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }
}
