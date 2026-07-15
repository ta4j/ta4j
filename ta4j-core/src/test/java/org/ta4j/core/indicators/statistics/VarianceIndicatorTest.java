/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public VarianceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9).build();
    }

    @Test
    public void varianceUsingBarCount4UsingClosePrice() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 4);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.5, variance.getValue(1));
        assertNumEquals(1.0, variance.getValue(2));
        assertNumEquals(1.66666666666667, variance.getValue(3));
        assertNumEquals(0.66666666666667, variance.getValue(4));
        assertNumEquals(0.33333333333333, variance.getValue(5));
        assertNumEquals(0.66666666666667, variance.getValue(6));
        assertNumEquals(0.66666666666667, variance.getValue(7));
        assertNumEquals(0.66666666666667, variance.getValue(8));
        assertNumEquals(4.66666666666667, variance.getValue(9));
        assertNumEquals(14, variance.getValue(10));
    }

    @Test
    public void firstValueShouldBeZero() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 4);
        assertNumEquals(0, variance.getValue(0));
    }

    @Test
    public void varianceShouldBeZeroWhenBarCountIs1() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, variance.getValue(3));
        assertNumEquals(0, variance.getValue(8));
    }

    @Test
    public void varianceUsingBarCount2UsingClosePrice() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 2);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.5, variance.getValue(1));
        assertNumEquals(0.5, variance.getValue(2));
        assertNumEquals(0.5, variance.getValue(3));
        assertNumEquals(4.5, variance.getValue(9));
        assertNumEquals(40.5, variance.getValue(10));
    }

    @Test
    public void populationVarianceCanStillBeRequestedExplicitly() {
        var variance = VarianceIndicator.ofPopulation(new ClosePriceIndicator(data), 4);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.25, variance.getValue(1));
        assertNumEquals(2.0 / 3, variance.getValue(2));
        assertNumEquals(1.25, variance.getValue(3));
        assertNumEquals(0.5, variance.getValue(4));
        assertNumEquals(0.25, variance.getValue(5));
        assertNumEquals(0.5, variance.getValue(6));
        assertNumEquals(0.5, variance.getValue(7));
        assertNumEquals(0.5, variance.getValue(8));
        assertNumEquals(3.5, variance.getValue(9));
        assertNumEquals(10.5, variance.getValue(10));
    }

    @Test
    public void constantTranscendentalValuesHaveZeroLowPrecisionVariance() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(2);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(lowPrecision).withData(1, 2, 3).build();
        Num value = lowPrecision.numOf("1E-8").log();
        Indicator<Num> source = new FixedIndicator<>(series, value, value, value);
        VarianceIndicator variance = VarianceIndicator.ofPopulation(source, 3);

        assertNumEquals(0, variance.getValue(2));
    }

    @Test
    public void sequentialWindowsDoNotReadEverySourceValueTwice() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        CountingIndicator source = new CountingIndicator(series, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5));
        VarianceIndicator variance = VarianceIndicator.ofPopulation(source, 4);

        variance.getValue(3);
        source.resetReadCount();
        assertNumEquals(1.25, variance.getValue(4));

        assertTrue("A sequential variance window reread the source " + source.readCount() + " times",
                source.readCount() <= 6);
    }

    private static final class CountingIndicator extends FixedIndicator<Num> {

        private int readCount;

        private CountingIndicator(BarSeries series, Num... values) {
            super(series, values);
        }

        @Override
        public Num getValue(int index) {
            readCount++;
            return super.getValue(index);
        }

        private int readCount() {
            return readCount;
        }

        private void resetReadCount() {
            readCount = 0;
        }
    }
}
