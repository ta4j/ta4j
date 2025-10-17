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
package org.ta4j.core.indicators.renko;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Detects bullish Renko sequences where the price has advanced by a configured
 * number of bricks since the previous close.
 *
 * <p>
 * A Renko brick is produced whenever price moves by the configured point size.
 * According to <a href=
 * "https://www.investopedia.com/terms/r/renkochart.asp">Investopedia</a>,
 * consecutive bullish bricks highlight persistent upward pressure. This
 * indicator signals {@code true} once the number of consecutive bullish bricks
 * equals or exceeds the configured threshold.
 *
 * @since 0.19
 */
public class RenkoUpIndicator extends CachedIndicator<Boolean> {

    private final RenkoCounter counter;
    private final int brickCount;

    /**
     * Creates an indicator that signals after a single bullish Renko brick.
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     *
     * @since 0.19
     */
    public RenkoUpIndicator(Indicator<Num> priceIndicator, double pointSize) {
        this(priceIndicator, pointSize, 1);
    }

    /**
     * Creates an indicator that requires a custom number of consecutive bullish
     * bricks before signalling.
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     * @param brickCount     number of bullish bricks required to signal
     *
     * @since 0.19
     */
    public RenkoUpIndicator(Indicator<Num> priceIndicator, double pointSize, int brickCount) {
        super(priceIndicator);
        var numFactory = getBarSeries().numFactory();
        var resolvedPointSize = numFactory.numOf(pointSize);
        if (resolvedPointSize.isLessThanOrEqual(numFactory.zero())) {
            throw new IllegalArgumentException("pointSize must be strictly positive");
        }
        if (brickCount < 1) {
            throw new IllegalArgumentException("brickCount must be at least 1");
        }
        this.brickCount = brickCount;
        this.counter = new RenkoCounter(priceIndicator, resolvedPointSize);
    }

    @Override
    protected Boolean calculate(int index) {
        return counter.stateAt(index).getConsecutiveUp() >= brickCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
