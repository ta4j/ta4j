/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.renko;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
        this(validatedConfig(priceIndicator, pointSize, 1));
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
        this(validatedConfig(priceIndicator, pointSize, brickCount));
    }

    private RenkoUpIndicator(Config config) {
        super(config.priceIndicator());
        this.brickCount = config.brickCount();
        this.counter = config.counter();
    }

    private static Config validatedConfig(Indicator<Num> priceIndicator, double pointSize, int brickCount) {
        Indicator<Num> validatedPriceIndicator = Objects.requireNonNull(priceIndicator,
                "priceIndicator must not be null");
        NumFactory numFactory = validatedPriceIndicator.getBarSeries().numFactory();
        Num resolvedPointSize = numFactory.numOf(pointSize);
        if (resolvedPointSize.isLessThanOrEqual(numFactory.zero())) {
            throw new IllegalArgumentException("pointSize must be strictly positive");
        }
        if (brickCount < 1) {
            throw new IllegalArgumentException("brickCount must be at least 1");
        }
        return new Config(validatedPriceIndicator, new RenkoCounter(validatedPriceIndicator, resolvedPointSize),
                brickCount);
    }

    @Override
    protected Boolean calculate(int index) {
        return counter.stateAt(index).getConsecutiveUp() >= brickCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }

    private record Config(Indicator<Num> priceIndicator, RenkoCounter counter, int brickCount) {
    }
}
