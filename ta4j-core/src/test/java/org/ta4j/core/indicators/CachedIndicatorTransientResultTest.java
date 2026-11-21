/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CachedIndicatorTransientResultTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CachedIndicatorTransientResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries series(int barCount) {
        double[] data = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            data[i] = i + 1;
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    private static final class FlakySource extends CachedIndicator<Num> {
        private final ClosePriceIndicator close;
        private final int failAt;
        private final AtomicInteger failures;
        private final AtomicInteger calculations = new AtomicInteger();
        private final boolean transientFailure;

        private FlakySource(BarSeries series, int failAt, int failures, boolean transientFailure) {
            super(series);
            this.close = new ClosePriceIndicator(series);
            this.failAt = failAt;
            this.failures = new AtomicInteger(failures);
            this.transientFailure = transientFailure;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            if (index == failAt && failures.getAndDecrement() > 0) {
                if (transientFailure) {
                    markResultTransient();
                }
                return NaN.NaN;
            }
            return close.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class RunningSum extends RecursiveCachedIndicator<Num> {
        private final Indicator<Num> source;

        private RunningSum(Indicator<Num> source) {
            super(source);
            this.source = source;
        }

        @Override
        protected Num calculate(int index) {
            Num value = source.getValue(index);
            return index == 0 ? value : getValue(index - 1).plus(value);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class Positive extends CachedIndicator<Boolean> {
        private final Indicator<Num> source;

        private Positive(Indicator<Num> source) {
            super(source);
            this.source = source;
        }

        @Override
        protected Boolean calculate(int index) {
            Num value = source.getValue(index);
            return !value.isNaN() && value.isPositive();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    @Test
    public void transientNaNIsNotCachedBySourceOrWrapper() {
        BarSeries series = series(20);
        FlakySource source = new FlakySource(series, 5, 1, true);
        SMAIndicator sma = new SMAIndicator(source, 3);
        Num expected = new SMAIndicator(new ClosePriceIndicator(series), 3).getValue(7);

        assertTrue(sma.getValue(7).isNaN());
        assertNumEquals(expected, sma.getValue(7));
        assertNumEquals(6, source.getValue(5));
    }

    @Test
    public void deterministicNaNStaysCached() {
        BarSeries series = series(20);
        FlakySource source = new FlakySource(series, 5, 1, false);

        assertTrue(source.getValue(5).isNaN());
        assertTrue(source.getValue(5).isNaN());
        assertEquals(1, source.calculations.get());
    }

    @Test
    public void nonNumWrapperReturnsItsOwnValueAndRecovers() {
        BarSeries series = series(20);
        Positive positive = new Positive(new FlakySource(series, 5, 1, true));

        assertFalse(positive.getValue(5));
        assertTrue(positive.getValue(5));
    }

    @Test
    public void longRecursiveChainOverTransientSourceRecoversWithoutOverflow() {
        int bars = 20_000;
        BarSeries series = series(bars);
        FlakySource source = new FlakySource(series, 100, 1, true);
        RunningSum sum = new RunningSum(source);
        Num expected = new RunningSum(new ClosePriceIndicator(series)).getValue(bars - 1);

        assertTrue(sum.getValue(bars - 1).isNaN());
        assertNumEquals(expected, sum.getValue(bars - 1));
        assertNumEquals(new RunningSum(new ClosePriceIndicator(series)).getValue(99), sum.getValue(99));
    }
}
