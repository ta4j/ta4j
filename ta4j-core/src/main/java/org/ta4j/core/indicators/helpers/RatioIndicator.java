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
//package org.ta4j.core.indicators.helpers;
//
//import org.ta4j.core.indicators.AbstractIndicator;
//import org.ta4j.core.indicators.Indicator;
//import org.ta4j.core.num.Num;
//
///**
// * Calculates the ratio between the current and the previous indicator value.
// *
// * <pre>
// * ratio = currentBarIndicatorValue / previousBarIndicatorValue
// * </pre>
// */
//public class RatioIndicator extends AbstractIndicator<Num> {
//
//    private final Indicator<Num> indicator;
//    private Num preViousValue;
//    /**
//     * Constructor.                               TRa
//     *
//     * @param indicator the bar indicator
//     */
//    public RatioIndicator(Indicator<Num> indicator) {
//        super(indicator.getBarSeries());
//        this.indicator = indicator;
//    }
//
//    /**
//     * Calculates the ratio between the current and previous close prices.
//     *
//     * @return the ratio between the close prices
//     */
//    protected Num calculate() {
//        if (preViousValue == null) {
//            preViousValue = indicator.getValue();
//            return preViousValue;
//        }
//
//        Num currentValue = indicator.getValue();
//
//        // Calculate the ratio between the values
//        final var ratio = currentValue.dividedBy(preViousValue);
//        preViousValue = currentValue;
//        return ratio;
//    }
//
//
//    @Override
//    public Num getValue() {
//        return calculate();
//    }
//
//
//    /** @return {@code 1} */
//    @Override
//    public int getUnstableBars() {
//        return 1;
//    }
//}
