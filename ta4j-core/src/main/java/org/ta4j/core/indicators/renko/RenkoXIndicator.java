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
 * Detects exhaustion moves where price prints a configured number of Renko
 * bricks consecutively in either direction.
 *
 * <p>
 * Renko traders often look for three or more bricks in the same direction to
 * confirm momentum before anticipating a potential reversal (see <a href=
 * "https://www.investopedia.com/terms/r/renkochart.asp">Investopedia</a>). This
 * indicator signals {@code true} when either bullish or bearish bricks meet or
 * exceed the configured count.
 *
 * @since 0.19
 */
public class RenkoXIndicator extends CachedIndicator<Boolean> {

    private final RenkoCounter counter;
    private final int brickCount;

    /**
     * Creates an indicator that looks for three consecutive bricks (Renko "X").
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     *
     * @since 0.19
     */
    public RenkoXIndicator(Indicator<Num> priceIndicator, double pointSize) {
        this(priceIndicator, pointSize, 3);
    }

    /**
     * Creates an indicator that requires a custom number of consecutive bricks in
     * either direction before signalling.
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     * @param brickCount     number of bricks required to signal
     *
     * @since 0.19
     */
    public RenkoXIndicator(Indicator<Num> priceIndicator, double pointSize, int brickCount) {
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
        var state = counter.stateAt(index);
        return Math.max(state.getConsecutiveUp(), state.getConsecutiveDown()) >= brickCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
