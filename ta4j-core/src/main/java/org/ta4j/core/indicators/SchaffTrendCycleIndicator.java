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

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
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
 * @since 0.19
 */
public class SchaffTrendCycleIndicator extends CachedIndicator<Num> {

    private final MACDIndicator macd;
    private final StochasticIndicator macdStochastic;
    private final SmoothingIndicator macdStochasticSmoothed;
    private final StochasticIndicator cycleStochastic;
    private final SmoothingIndicator stcSmoothed;
    private final int slowPeriod;
    private final int cycleLength;
    private final int smoothingPeriod;

    /**
     * Constructor with common parameterization ({@code fast}=23, {@code slow}=50,
     * {@code cycleLength}=10, {@code smoothingPeriod}=3).
     *
     * @param indicator the base {@link Indicator}
     * @since 0.19
     */
    public SchaffTrendCycleIndicator(Indicator<Num> indicator) {
        this(indicator, 23, 50, 10, 3);
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
     * @since 0.19
     */
    public SchaffTrendCycleIndicator(Indicator<Num> indicator, int fastPeriod, int slowPeriod, int cycleLength,
            int smoothingPeriod) {
        super(indicator);
        if (fastPeriod < 1 || slowPeriod < 1 || cycleLength < 1 || smoothingPeriod < 1) {
            throw new IllegalArgumentException("All Schaff Trend Cycle periods must be positive integers");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Slow period must be greater than fast period for MACD calculation");
        }
        this.slowPeriod = slowPeriod;
        this.cycleLength = cycleLength;
        this.smoothingPeriod = smoothingPeriod;

        this.macd = new MACDIndicator(indicator, fastPeriod, slowPeriod);
        this.macdStochastic = new StochasticIndicator(macd, cycleLength);
        this.macdStochasticSmoothed = new SmoothingIndicator(macdStochastic, smoothingPeriod);
        this.cycleStochastic = new StochasticIndicator(macdStochasticSmoothed, cycleLength);
        this.stcSmoothed = new SmoothingIndicator(cycleStochastic, smoothingPeriod);
    }

    @Override
    protected Num calculate(int index) {
        return stcSmoothed.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return slowPeriod + cycleLength + smoothingPeriod;
    }

    private static final class StochasticIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final HighestValueIndicator highest;
        private final LowestValueIndicator lowest;
        private final int lookback;

        private StochasticIndicator(Indicator<Num> indicator, int lookback) {
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
            Num value = indicator.getValue(index);
            if (isNaN(value)) {
                return NaN;
            }
            Num highestValue = highest.getValue(index);
            Num lowestValue = lowest.getValue(index);
            if (isNaN(highestValue) || isNaN(lowestValue)) {
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

    private static final class SmoothingIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final Num multiplier;
        private final int period;

        private SmoothingIndicator(Indicator<Num> indicator, int period) {
            super(indicator);
            if (period < 1) {
                throw new IllegalArgumentException("Smoothing period must be a positive integer");
            }
            this.indicator = indicator;
            this.multiplier = getBarSeries().numFactory().numOf(2.0 / (period + 1));
            this.period = period;
        }

        @Override
        protected Num calculate(int index) {
            Num current = indicator.getValue(index);
            if (isNaN(current)) {
                return NaN;
            }
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return current;
            }
            Num previous = getValue(index - 1);
            if (isNaN(previous)) {
                return current;
            }
            return previous.plus(current.minus(previous).multipliedBy(multiplier));
        }

        @Override
        public int getCountOfUnstableBars() {
            return period;
        }
    }

    private static boolean isNaN(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
