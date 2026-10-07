/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
        CountingIndicator source = new CountingIndicator(series, numFactory.one(), numFactory.two(), numOf(3), numOf(4),
                numOf(5));
        VarianceIndicator variance = VarianceIndicator.ofPopulation(source, 4);

        variance.getValue(3);
        source.resetReadCount();
        assertNumEquals(1.25, variance.getValue(4));

        assertTrue("A sequential variance window reread the source " + source.readCount() + " times",
                source.readCount() <= 6);
    }

    @Test
    public void anchorsWindowAtBeginIndexAfterRemoval() {
        // Evict the first four closes (1..4) so beginIndex = 4; the retained closes
        // [5,6,7,8,9,10] live at absolute indices 4..9. The indicator is constructed
        // after the removal, so its window must anchor at beginIndex rather than 0.
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .withMaxBarCount(6)
                .build();
        var variance = new VarianceIndicator(new ClosePriceIndicator(pruned), 6);

        // Preserve the released partial-window values even before stability.
        assertNumEquals(0, variance.getValue(4));
        // Window [4..7] = {5,6,7,8}: sample variance = 5 / 3.
        assertNumEquals(5.0 / 3, variance.getValue(7));
        // Last unstable index: five observations give sample variance = 10 / 4.
        assertNumEquals(2.5, variance.getValue(8));
        assertThat(variance.getCountOfUnstableBars()).isEqualTo(5);
        assertNumEquals(3.5, variance.getValue(9));
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

    @Test
    public void retainedStabilityCountsRemainRelativeForComposedConsumers() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        series.setMaximumBarCount(6);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        List<Indicator<Num>> indicators = List.of(new VarianceIndicator(close, 6),
                new CovarianceIndicator(close, close, 6), new StandardDeviationIndicator(close, 6),
                new StandardErrorIndicator(close, 6), new CorrelationCoefficientIndicator(close, close, 6));
        assertThat(series.getBeginIndex()).isEqualTo(4);
        assertThat(series.getBarCount()).isEqualTo(6);
        for (Indicator<Num> indicator : indicators) {
            assertThat(indicator.getCountOfUnstableBars()).as(indicator.getClass().getSimpleName()).isEqualTo(5);
            assertThat(indicator.isStable()).as(indicator.getClass().getSimpleName()).isTrue();
            assertThat(Num.isFinite(indicator.getValue(9))).isTrue();
        }
    }

    @Test
    public void retainedHeadKeepsAlreadyStableCachedValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        AtomicInteger calculations = new AtomicInteger();
        VarianceIndicator variance = new VarianceIndicator(new ClosePriceIndicator(series), 3) {
            @Override
            protected Num calculate(int index) {
                calculations.incrementAndGet();
                return super.calculate(index);
            }
        };
        assertNumEquals(1, variance.getValue(8));
        assertThat(calculations).hasValue(1);
        series.setMaximumBarCount(6);
        assertNumEquals(1, variance.getValue(8));
        assertThat(calculations).hasValue(1);
    }

    @Test
    public void retainedWindowBoundaryRequiresFullStableHistory() {
        BarSeries retained = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        retained.setMaximumBarCount(4);
        ClosePriceIndicator source = new ClosePriceIndicator(retained);
        VarianceIndicator metric = VarianceIndicator.ofPopulation(source, 3);
        assertThat(metric.getCountOfUnstableBars()).isEqualTo(2);
        assertNumEquals(0, metric.getValue(0));
        assertNumEquals(0, metric.getValue(2));
        // Last unstable index has the legacy two-observation population value.
        assertNumEquals(0.25, metric.getValue(3));
        assertNumEquals(2.0 / 3, metric.getValue(4));
    }

    @Test
    public void terminalIndexWindowVisitsSourceExactlyOnce() {
        BarSeries fixture = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1).build();
        BarSeries terminalSeries = new org.ta4j.core.BaseBarSeries("terminal", List.of()) {
            @Override
            public org.ta4j.core.Bar getBar(int index) {
                assertThat(index).isEqualTo(Integer.MAX_VALUE);
                return fixture.getBar(0);
            }

            @Override
            public NumFactory numFactory() {
                return numFactory;
            }

            @Override
            public int getBeginIndex() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getBarCount() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public int getMaximumBarCount() {
                return 1;
            }

            @Override
            public synchronized BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(long revision) {
                return new BarSeriesChangeSnapshot(0, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE);
            }
        };
        Indicator<Num> source = new org.ta4j.core.indicators.helpers.ConstantIndicator<>(terminalSeries,
                numFactory.one()) {
            @Override
            public Num getValue(int index) {
                assertThat(index).isEqualTo(Integer.MAX_VALUE);
                return numFactory.one();
            }
        };
        VarianceIndicator metric = VarianceIndicator.ofPopulation(source, 1);
        assertNumEquals(0, metric.calculate(Integer.MAX_VALUE));
    }

    @Test
    public void retainedBoundaryAlsoHonorsSourceWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8)
                .build();
        series.setMaximumBarCount(6);
        Indicator<Num> source = new ClosePriceIndicator(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 4;
            }
        };
        VarianceIndicator metric = VarianceIndicator.ofPopulation(source, 3);
        assertThat(metric.getCountOfUnstableBars()).isEqualTo(6);
        // Available numeric source values do not override the stability metadata.
        assertNumEquals(2.0 / 3, metric.getValue(5));
        assertNumEquals(2.0 / 3, metric.getValue(6));
    }
}
