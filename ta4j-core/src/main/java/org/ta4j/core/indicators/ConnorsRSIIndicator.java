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
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Connors RSI indicator.
 *
 * <p>
 * Combines a short-term RSI, RSI of streak length, and the percentile rank of
 * daily price changes.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/connorsrsi">
 *      StockCharts: ConnorsRSI</a>
 * @since 0.19
 */
public class ConnorsRSIIndicator extends CachedIndicator<Num> {

    private final RSIIndicator priceRsi;
    private final RSIIndicator streakRsi;
    private final PercentRankIndicator percentRankIndicator;
    private final int percentRankPeriod;

    /**
     * Constructor using the original Connors RSI defaults ({@code rsiPeriod}=3,
     * {@code streakRsiPeriod}=2, {@code percentRankPeriod}=100).
     *
     * @param indicator the {@link Indicator} providing the price series
     * @since 0.19
     */
    public ConnorsRSIIndicator(Indicator<Num> indicator) {
        this(indicator, 3, 2, 100);
    }

    /**
     * Constructor.
     *
     * @param indicator         the {@link Indicator} providing the price series
     * @param rsiPeriod         the look-back period for the price RSI
     * @param streakRsiPeriod   the look-back period for the streak RSI
     * @param percentRankPeriod the look-back period for the percentile rank of
     *                          price changes
     * @since 0.19
     */
    public ConnorsRSIIndicator(Indicator<Num> indicator, int rsiPeriod, int streakRsiPeriod, int percentRankPeriod) {
        super(indicator);
        if (rsiPeriod < 1 || streakRsiPeriod < 1 || percentRankPeriod < 1) {
            throw new IllegalArgumentException("Connors RSI periods must be positive integers");
        }
        this.percentRankPeriod = percentRankPeriod;

        PriceChangeIndicator priceChangeIndicator = new PriceChangeIndicator(indicator);
        this.priceRsi = new RSIIndicator(indicator, rsiPeriod);
        this.streakRsi = new RSIIndicator(new StreakIndicator(indicator), streakRsiPeriod);
        this.percentRankIndicator = new PercentRankIndicator(priceChangeIndicator, percentRankPeriod);
    }

    @Override
    protected Num calculate(int index) {
        Num priceRsiValue = priceRsi.getValue(index);
        Num streakRsiValue = streakRsi.getValue(index);
        Num percentRankValue = percentRankIndicator.getValue(index);
        if (isNaN(priceRsiValue) || isNaN(streakRsiValue) || isNaN(percentRankValue)) {
            return NaN;
        }
        Num total = priceRsiValue.plus(streakRsiValue).plus(percentRankValue);
        return total.dividedBy(getBarSeries().numFactory().numOf(3));
    }

    @Override
    public int getCountOfUnstableBars() {
        return percentRankPeriod + 1;
    }

    private static final class PriceChangeIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> indicator;

        private PriceChangeIndicator(Indicator<Num> indicator) {
            super(indicator);
            this.indicator = indicator;
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return NaN;
            }
            Num current = indicator.getValue(index);
            Num previous = indicator.getValue(index - 1);
            if (isNaN(current) || isNaN(previous)) {
                return NaN;
            }
            return current.minus(previous);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 1;
        }
    }

    private static final class StreakIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;

        private StreakIndicator(Indicator<Num> indicator) {
            super(indicator);
            this.indicator = indicator;
        }

        @Override
        protected Num calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            Num current = indicator.getValue(index);
            if (isNaN(current)) {
                return NaN;
            }
            if (index <= beginIndex) {
                return getBarSeries().numFactory().zero();
            }
            Num previousPrice = indicator.getValue(index - 1);
            if (isNaN(previousPrice)) {
                return NaN;
            }
            Num priceChange = current.minus(previousPrice);
            if (priceChange.isZero()) {
                return getBarSeries().numFactory().zero();
            }
            Num previousStreak = getValue(index - 1);
            if (isNaN(previousStreak)) {
                previousStreak = getBarSeries().numFactory().zero();
            }
            Num one = getBarSeries().numFactory().one();
            if (priceChange.isPositive()) {
                return previousStreak.isPositive() ? previousStreak.plus(one) : one;
            }
            Num negativeOne = one.negate();
            return previousStreak.isNegative() ? previousStreak.minus(one) : negativeOne;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 1;
        }
    }

    private static final class PercentRankIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final int period;

        private PercentRankIndicator(Indicator<Num> indicator, int period) {
            super(indicator);
            this.indicator = indicator;
            this.period = period;
        }

        @Override
        protected Num calculate(int index) {
            Num current = indicator.getValue(index);
            if (isNaN(current)) {
                return NaN;
            }
            int beginIndex = getBarSeries().getBeginIndex();
            int startIndex = Math.max(beginIndex, index - period + 1);
            int endIndex = index;
            int valid = 0;
            int lessThanCount = 0;
            for (int i = startIndex; i <= endIndex; i++) {
                Num candidate = indicator.getValue(i);
                if (isNaN(candidate)) {
                    continue;
                }
                valid++;
                if (candidate.isLessThan(current)) {
                    lessThanCount++;
                }
            }
            if (valid == 0) {
                return NaN;
            }
            Num hundred = getBarSeries().numFactory().hundred();
            Num ratio = getBarSeries().numFactory()
                    .numOf(lessThanCount)
                    .dividedBy(getBarSeries().numFactory().numOf(valid));
            return ratio.multipliedBy(hundred);
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
