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
 * Detects bearish Renko sequences where price has fallen by a configured number
 * of bricks since the previous close.
 *
 * <p>
 * A Renko brick is produced whenever price moves by the configured point size.
 * According to <a href=
 * "https://www.investopedia.com/terms/r/renkochart.asp">Investopedia</a>,
 * consecutive bearish bricks emphasise persistent downward pressure. This
 * indicator signals {@code true} once the number of consecutive bearish bricks
 * equals or exceeds the configured threshold.
 *
 * @since 0.19
 */
public class RenkoDownIndicator extends CachedIndicator<Boolean> {

    private final RenkoCounter counter;
    private final int brickCount;

    /**
     * Creates an indicator that signals after a single bearish Renko brick.
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     *
     * @since 0.19
     */
    public RenkoDownIndicator(Indicator<Num> priceIndicator, double pointSize) {
        this(validatedConfig(priceIndicator, pointSize, 1));
    }

    /**
     * Creates an indicator that requires a custom number of consecutive bearish
     * bricks before signalling.
     *
     * @param priceIndicator price series to build bricks from
     * @param pointSize      the brick size expressed in price units
     * @param brickCount     number of bearish bricks required to signal
     *
     * @since 0.19
     */
    public RenkoDownIndicator(Indicator<Num> priceIndicator, double pointSize, int brickCount) {
        this(validatedConfig(priceIndicator, pointSize, brickCount));
    }

    private RenkoDownIndicator(Config config) {
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
        return counter.stateAt(index).getConsecutiveDown() >= brickCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }

    private record Config(Indicator<Num> priceIndicator, RenkoCounter counter, int brickCount) {
    }
}
