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
import org.ta4j.core.num.Num;

/**
 * True strength index (TSI) indicator.
 *
 * <p>
 * This oscillator double smooths price changes to quantify trend direction and
 * momentum.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/true-strength-index">
 *      StockCharts: True Strength Index (TSI)</a>
 * @since 0.19
 */
public class TrueStrengthIndexIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final DoubleSmoothedIndicator doubleSmoothedChange;
    private final DoubleSmoothedIndicator doubleSmoothedAbsoluteChange;
    private final int firstSmoothingPeriod;
    private final int secondSmoothingPeriod;

    /**
     * Constructor using the common (25, 13) smoothing periods.
     *
     * @param indicator the {@link Indicator} containing the price series
     * @since 0.19
     */
    public TrueStrengthIndexIndicator(Indicator<Num> indicator) {
        this(indicator, 25, 13);
    }

    /**
     * Constructor.
     *
     * @param indicator             the {@link Indicator} containing the price
     *                              series
     * @param firstSmoothingPeriod  the long look-back period (commonly 25)
     * @param secondSmoothingPeriod the short look-back period (commonly 13)
     * @since 0.19
     */
    public TrueStrengthIndexIndicator(Indicator<Num> indicator, int firstSmoothingPeriod, int secondSmoothingPeriod) {
        super(indicator);
        if (firstSmoothingPeriod < 1) {
            throw new IllegalArgumentException("First smoothing period must be a positive integer");
        }
        if (secondSmoothingPeriod < 1) {
            throw new IllegalArgumentException("Second smoothing period must be a positive integer");
        }
        this.indicator = indicator;
        this.firstSmoothingPeriod = firstSmoothingPeriod;
        this.secondSmoothingPeriod = secondSmoothingPeriod;

        PriceChangeIndicator priceChangeIndicator = new PriceChangeIndicator(indicator);
        this.doubleSmoothedChange = new DoubleSmoothedIndicator(priceChangeIndicator, firstSmoothingPeriod,
                secondSmoothingPeriod);
        this.doubleSmoothedAbsoluteChange = new DoubleSmoothedIndicator(
                new AbsoluteValueIndicator(priceChangeIndicator), firstSmoothingPeriod, secondSmoothingPeriod);
    }

    @Override
    protected Num calculate(int index) {
        Num numerator = doubleSmoothedChange.getValue(index);
        Num denominator = doubleSmoothedAbsoluteChange.getValue(index);
        if (isNaN(numerator) || isNaN(denominator) || denominator.isZero()) {
            return NaN;
        }
        return numerator.dividedBy(denominator).multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return firstSmoothingPeriod + secondSmoothingPeriod;
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

    private static final class AbsoluteValueIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> indicator;

        private AbsoluteValueIndicator(Indicator<Num> indicator) {
            super(indicator);
            this.indicator = indicator;
        }

        @Override
        protected Num calculate(int index) {
            Num value = indicator.getValue(index);
            if (isNaN(value)) {
                return NaN;
            }
            return value.abs();
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }
    }

    private static final class DoubleSmoothedIndicator extends CachedIndicator<Num> {

        private final SmoothingIndicator firstSmoothing;
        private final SmoothingIndicator secondSmoothing;
        private final int unstableBars;

        private DoubleSmoothedIndicator(Indicator<Num> indicator, int firstPeriod, int secondPeriod) {
            super(indicator);
            this.firstSmoothing = new SmoothingIndicator(indicator, firstPeriod);
            this.secondSmoothing = new SmoothingIndicator(firstSmoothing, secondPeriod);
            this.unstableBars = firstPeriod + secondPeriod;
        }

        @Override
        protected Num calculate(int index) {
            return secondSmoothing.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
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
