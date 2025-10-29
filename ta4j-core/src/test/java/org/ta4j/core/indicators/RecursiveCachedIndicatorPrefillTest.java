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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.Assert.*;

public class RecursiveCachedIndicatorPrefillTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int TARGET_INDEX = 256;

    private BarSeries series;

    public RecursiveCachedIndicatorPrefillTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        double[] data = new double[TARGET_INDEX + 10];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    @Test
    public void prefillGuardPreventsRecursiveOverflow() {
        ReentrantIndicator indicator = new ReentrantIndicator(series, TARGET_INDEX);
        Num value = indicator.getValue(TARGET_INDEX);
        assertNotNull(value);

        LegacyReentrantIndicator legacy = new LegacyReentrantIndicator(series, TARGET_INDEX);
        assertThrows(StackOverflowError.class, () -> legacy.getValue(TARGET_INDEX));
    }

    private final class ReentrantIndicator extends RecursiveCachedIndicator<Num> {

        private final int triggerIndex;

        private ReentrantIndicator(BarSeries series, int triggerIndex) {
            super(series);
            this.triggerIndex = triggerIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index < triggerIndex) {
                getValue(triggerIndex);
            }
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private abstract static class LegacyRecursiveCachedIndicator<T> extends CachedIndicator<T> {

        private static final int RECURSION_THRESHOLD = 100;

        protected LegacyRecursiveCachedIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public T getValue(int index) {
            BarSeries series = getBarSeries();
            if (series == null || index > series.getEndIndex()) {
                return super.getValue(index);
            }

            int startIndex = Math.max(series.getRemovedBarsCount(), highestResultIndex);
            if (index - startIndex > RECURSION_THRESHOLD) {
                for (int prevIndex = startIndex; prevIndex < index; prevIndex++) {
                    super.getValue(prevIndex);
                }
            }

            return super.getValue(index);
        }
    }

    private final class LegacyReentrantIndicator extends LegacyRecursiveCachedIndicator<Num> {

        private final int triggerIndex;

        private LegacyReentrantIndicator(BarSeries series, int triggerIndex) {
            super(series);
            this.triggerIndex = triggerIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index < triggerIndex) {
                getValue(triggerIndex);
            }
            return numFactory.numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
