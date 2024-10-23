/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import java.util.function.BiPredicate;

/**
 * Boolean combine indicator.
 *
 * This is based on BooleanTransformIndicator with the same logical operators,
 * but instead of comparing one indicator to a constant, it compares two
 * indicators. Additionally, the indicator supports converting true and false to
 * 1 and 0.
 */
public class BooleanCombineIndicator extends CachedIndicator<Boolean> {
    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final BiPredicate<Num, Num> transform;

    public static BooleanCombineIndicator equals(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::equals);
    }

    public static BooleanCombineIndicator isEqual(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::isEqual);
    }

    public static BooleanCombineIndicator isGreaterThan(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::isGreaterThan);
    }

    public static BooleanCombineIndicator isGreaterThanOrEqual(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::isGreaterThanOrEqual);
    }

    public static BooleanCombineIndicator isLessThan(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::isLessThan);
    }

    public static BooleanCombineIndicator isLessThanOrEqual(Indicator<Num> indicator1, Indicator<Num> indicator2) {
        return new BooleanCombineIndicator(indicator1, indicator2, Num::isLessThanOrEqual);
    }

    public BooleanCombineIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2,
            BiPredicate<Num, Num> transform) {
        super(indicator1);

        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.transform = transform;
    }

    @Override
    protected Boolean calculate(int index) {
        return transform.test(indicator1.getValue(index), indicator2.getValue(index));
    }

    @Override
    public int getUnstableBars() {
        return Math.max(indicator1.getUnstableBars(), indicator2.getUnstableBars());
    }

    public BooleanToNumIndicator asNum() {
        return new BooleanToNumIndicator(this);
    }

    public static class BooleanToNumIndicator extends CachedIndicator<Num> {
        private final BooleanCombineIndicator indicator;

        public BooleanToNumIndicator(BooleanCombineIndicator indicator) {
            super(indicator);

            this.indicator = indicator;
        }

        @Override
        protected Num calculate(int index) {
            return indicator.getValue(index) ? getBarSeries().numFactory().one() : getBarSeries().numFactory().zero();
        }

        @Override
        public int getUnstableBars() {
            return indicator.getUnstableBars();
        }
    }
}
