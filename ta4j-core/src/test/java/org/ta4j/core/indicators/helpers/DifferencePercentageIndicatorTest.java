/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.StochasticIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@SuppressWarnings("deprecation")
public class DifferencePercentageIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DifferencePercentageIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void retainedHeadAdvanceReanchorsThresholdState() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        DifferencePercentageIndicator existing = new DifferencePercentageIndicator(source, numOf(10));

        assertThat(Num.isFinite(existing.getValue(series.getEndIndex()))).isTrue();
        series.setMaximumBarCount(3);
        series.barBuilder().openPrice(60).closePrice(60).highPrice(60).lowPrice(60).volume(0).add();

        StochasticIndicator freshSource = new StochasticIndicator(new ClosePriceIndicator(series), 3);
        DifferencePercentageIndicator fresh = new DifferencePercentageIndicator(freshSource, numOf(10));
        int endIndex = series.getEndIndex();

        assertThat(fresh.getValue(endIndex).isNaN()).isTrue();
        assertThat(existing.getValue(endIndex).isNaN()).isTrue();
    }

    @Test
    public void sequentialEvaluationScansEachBarOnce() {
        double[] closes = new double[2_000];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + (i % 7) * 3;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closes).build();
        java.util.concurrent.atomic.AtomicLong reads = new java.util.concurrent.atomic.AtomicLong();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        org.ta4j.core.Indicator<Num> counting = new org.ta4j.core.indicators.AbstractIndicator<>(series) {
            @Override
            public Num getValue(int index) {
                reads.incrementAndGet();
                return close.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        DifferencePercentageIndicator incremental = new DifferencePercentageIndicator(counting, numOf(5));

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num value = incremental.getValue(i);
            Num fresh = new DifferencePercentageIndicator(close, numOf(5)).getValue(i);
            assertThat(value.isNaN() ? fresh.isNaN() : value.isEqual(fresh)).as("index %s", i).isTrue();
        }

        assertThat(reads.get()).isLessThan(5L * closes.length);
    }

    @Test
    public void dependencySeriesChangeRescansEarlierBars() {
        BarSeries backing = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        BarSeries dependency = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        ClosePriceIndicator dependencyClose = new ClosePriceIndicator(dependency);
        Indicator<Num> source = new org.ta4j.core.indicators.CachedIndicator<>(backing, dependencyClose) {
            @Override
            protected Num calculate(int index) {
                return dependencyClose.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        DifferencePercentageIndicator indicator = new DifferencePercentageIndicator(source, numFactory.zero());

        assertThat(indicator.getValue(2).isZero()).isTrue();

        org.ta4j.core.Bar original = dependency.getBar(1);
        ((org.ta4j.core.BaseBarSeries) dependency).replaceBar(1,
                new org.ta4j.core.bars.TimeBarBuilder(numFactory).timePeriod(original.getTimePeriod())
                        .endTime(original.getEndTime())
                        .openPrice(200)
                        .highPrice(200)
                        .lowPrice(200)
                        .closePrice(200)
                        .volume(0)
                        .build());

        assertThat(indicator.getValue(2).isEqual(numOf(-50))).as("value %s", indicator.getValue(2)).isTrue();
    }
}
