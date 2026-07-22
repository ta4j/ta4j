/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Schaff Trend Cycle (STC) indicator.
 *
 * <p>
 * Combines MACD momentum with a stochastic calculation to accelerate trend
 * detection.
 *
 * @see <a href=
 *      "https://www.investopedia.com/articles/forex/10/schaff-trend-cycle-indicator.asp">
 *      Investopedia: Schaff Trend Cycle Indicator</a>
 * @since 0.20
 */
public class SchaffTrendCycleIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int fastPeriod;
    private final int slowPeriod;
    private final int cycleLength;
    private final int smoothingPeriod;
    private final transient EMAIndicator stcSmoothed;

    /**
     * Constructor with common parameterization ({@code fast}=23, {@code slow}=50,
     * {@code cycleLength}=10, {@code smoothingPeriod}=3).
     *
     * @param indicator the base {@link Indicator}
     * @since 0.20
     */
    public SchaffTrendCycleIndicator(Indicator<Num> indicator) {
        this(validatedConfig(indicator, 23, 50, 10, 3));
    }

    /**
     * Constructor.
     *
     * @param indicator       the base {@link Indicator}
     * @param fastPeriod      the fast EMA period (MACD short period)
     * @param slowPeriod      the slow EMA period (MACD long period)
     * @param cycleLength     the stochastic look-back length
     * @param smoothingPeriod the EMA smoothing period applied to the stochastic
     *                        calculations
     * @since 0.20
     */
    public SchaffTrendCycleIndicator(Indicator<Num> indicator, int fastPeriod, int slowPeriod, int cycleLength,
            int smoothingPeriod) {
        this(validatedConfig(indicator, fastPeriod, slowPeriod, cycleLength, smoothingPeriod));
    }

    private SchaffTrendCycleIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.fastPeriod = config.fastPeriod();
        this.slowPeriod = config.slowPeriod();
        this.cycleLength = config.cycleLength();
        this.smoothingPeriod = config.smoothingPeriod();
        this.stcSmoothed = config.stcSmoothed();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int fastPeriod, int slowPeriod, int cycleLength,
            int smoothingPeriod) {
        if (fastPeriod < 1 || slowPeriod < 1 || cycleLength < 1 || smoothingPeriod < 1) {
            throw new IllegalArgumentException("All Schaff Trend Cycle periods must be positive integers");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Slow period must be greater than fast period for MACD calculation");
        }

        MACDIndicator macd = new MACDIndicator(indicator, fastPeriod, slowPeriod);
        StochasticIndicator macdStochastic = new StochasticIndicator(macd, cycleLength);
        EMAIndicator macdStochasticSmoothed = new EMAIndicator(macdStochastic, smoothingPeriod);
        StochasticIndicator cycleStochastic = new StochasticIndicator(macdStochasticSmoothed, cycleLength);
        EMAIndicator stcSmoothed = new EMAIndicator(cycleStochastic, smoothingPeriod);
        return new Config(indicator, fastPeriod, stcSmoothed, slowPeriod, cycleLength, smoothingPeriod);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return stcSmoothed.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        // The indicator chain is:
        // MACD (slowPeriod) -> Stochastic (cycleLength) -> EMA (smoothingPeriod) ->
        // Stochastic (cycleLength) -> EMA (smoothingPeriod)
        // Unstable periods are additive through the chain
        return slowPeriod + cycleLength + smoothingPeriod + cycleLength + smoothingPeriod;
    }

    private record Config(Indicator<Num> indicator, int fastPeriod, EMAIndicator stcSmoothed, int slowPeriod,
            int cycleLength, int smoothingPeriod) {
    }
}
