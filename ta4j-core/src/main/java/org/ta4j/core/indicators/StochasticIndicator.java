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
        super(indicator);
        if (lookback < 1) {
            throw new IllegalArgumentException("Stochastic look-back length must be a positive integer");
        }
        this.indicator = indicator;
        this.highest = new HighestValueIndicator(indicator, lookback);
        this.lowest = new LowestValueIndicator(indicator, lookback);
        this.lookback = lookback;
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
        return lookback;
    }

}
