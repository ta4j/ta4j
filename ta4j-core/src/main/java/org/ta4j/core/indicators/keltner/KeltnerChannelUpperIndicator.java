/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

    private final transient ATRIndicator averageTrueRangeIndicator;
    private final KeltnerChannelMiddleIndicator keltnerMiddleIndicator;
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
        this.ratio = getBarSeries().numFactory().numOf(ratio);
    }

    @Override
    protected Num calculate(int index) {
        return keltnerMiddleIndicator.getValue(index)
                .plus(ratio.multipliedBy(averageTrueRangeIndicator.getValue(index)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return getBarCount();
    }

    /** @return the bar count of {@link #keltnerMiddleIndicator} */
    public int getBarCount() {
        return keltnerMiddleIndicator.getBarCount();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }
}
