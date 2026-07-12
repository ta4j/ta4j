/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Stochastic indicator.
 *
 * <p>
 * Calculates the stochastic value of an indicator by comparing its current
 * value to its highest and lowest values over a look-back period. The result is
 * normalized to a 0-100 scale.
 *
 * <p>
 * Formula: {@code (value - lowest) / (highest - lowest) * 100}
 *
 * <p>
 * This is a generic stochastic calculation that can be applied to any
 * indicator, unlike {@link StochasticOscillatorKIndicator} which compares close
 * prices to high/low prices. This indicator is commonly used in composite
 * indicators like the {@link SchaffTrendCycleIndicator}.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/stochasticoscillator.asp">Investopedia:
 *      Stochastic Oscillator</a>
 * @since 0.20
 */
public class StochasticIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final HighestValueIndicator highest;
    private final LowestValueIndicator lowest;
    private final int lookback;

    /**
     * Constructor.
     *
     * @param indicator the base {@link Indicator} to calculate stochastic values
     *                  for
     * @param lookback  the look-back period for finding highest and lowest values
     * @throws IllegalArgumentException if lookback is less than 1
     */
    public StochasticIndicator(Indicator<Num> indicator, int lookback) {
        this(validatedConfig(indicator, lookback));
    }

    private StochasticIndicator(Config config) {
        super(config.indicator());
        this.indicator = config.indicator();
        this.highest = config.highest();
        this.lowest = config.lowest();
        this.lookback = config.lookback();
    }

    private static Config validatedConfig(Indicator<Num> indicator, int lookback) {
        if (lookback < 1) {
            throw new IllegalArgumentException("Stochastic look-back length must be a positive integer");
        }
        HighestValueIndicator highest = new HighestValueIndicator(indicator, lookback);
        LowestValueIndicator lowest = new LowestValueIndicator(indicator, lookback);
        return new Config(indicator, highest, lowest, lookback);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        Num value = indicator.getValue(index);
        if (Num.isNaNOrNull(value)) {
            return NaN;
        }
        Num highestValue = highest.getValue(index);
        Num lowestValue = lowest.getValue(index);
        if (Num.isNaNOrNull(highestValue) || Num.isNaNOrNull(lowestValue)) {
            return NaN;
        }
        Num range = highestValue.minus(lowestValue);
        if (range.isZero()) {
            int beginIndex = getBarSeries().getBeginIndex();
            return index <= beginIndex ? getBarSeries().numFactory().zero() : getValue(index - 1);
        }
        return value.minus(lowestValue).dividedBy(range).multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(highest.getCountOfUnstableBars(), lowest.getCountOfUnstableBars());
    }

    private record Config(Indicator<Num> indicator, HighestValueIndicator highest, LowestValueIndicator lowest,
            int lookback) {
    }
}
