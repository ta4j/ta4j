///**
// * The MIT License (MIT)
// *
// * Copyright (c) 2017-2023 Ta4j Organization & respective
// * authors (see AUTHORS)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of
// * this software and associated documentation files (the "Software"), to deal in
// * the Software without restriction, including without limitation the rights to
// * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
// * the Software, and to permit persons to whom the Software is furnished to do so,
// * subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
// * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
// * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
// * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
// * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//package org.ta4j.core.indicators.statistics;
//
//import org.ta4j.core.indicators.AbstractIndicator;
//import org.ta4j.core.indicators.Indicator;
//import org.ta4j.core.indicators.average.SMAIndicator;
//import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
//import org.ta4j.core.num.Num;
//
///**
// * Variance indicator.
// */
//public class VarianceIndicator extends AbstractIndicator<Num> {
//
//    private final int barCount;
//    private final RunningTotalIndicator variance;
//
//    /**
//     * Constructor.
//     *
//     * @param indicator the indicator
//     * @param barCount  the time frame
//     */
//    public VarianceIndicator(Indicator<Num> indicator, int barCount) {
//        super(indicator.getBarSeries());
//        this.barCount = barCount;
//        this.variance = new RunningTotalIndicator(new PowIndicator(indicator,barCount),barCount);
//    }
//
//    protected Num calculate() {
//        final var numFactory = getBarSeries().numFactory();
//        return variance.getValue().dividedBy(numFactory.numOf(barCount));
//    }
//
//
//    @Override
//    public Num getValue() {
//        return calculate();
//    }
//
//
//    @Override
//    public int getUnstableBars() {
//        return barCount;
//    }
//
//    @Override
//    public String toString() {
//        return getClass().getSimpleName() + " barCount: " + barCount;
//    }
//
//
//    public static class PowIndicator extends AbstractIndicator<Num> {
//        private final Indicator<Num> indicator;
//        private final SMAIndicator sma;
//        private final int barCount;
//
//
//        /**
//         * Constructor.
//         *
//         * @param indicator the bar indicator
//         */
//        public PowIndicator(Indicator<Num> indicator, int barCount) {
//            super(indicator.getBarSeries());
//            this.barCount = barCount;
//            this.indicator = indicator;
//            this.sma = new SMAIndicator(indicator, barCount);
//        }
//
//
//        public Num getValue() {
//            return calculate();
//        }
//
//        /**
//         * Calculates the difference between indicator values of the current bar and the
//         * previous bar.
//         *
//         * @return the difference between the close prices
//         */
//        protected Num calculate() {
//            return pow();
//        }
//
//        private Num pow() {
//            return indicator.getValue().minus(sma.getValue()).pow(2);
//        }
//
//
//        @Override
//        public int getUnstableBars() {
//            return barCount;
//        }
//    }
//}
