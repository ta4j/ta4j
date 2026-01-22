/*
 * SPDX-License-Identifier: MIT
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
