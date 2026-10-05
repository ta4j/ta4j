/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

public class CovarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private Indicator<Num> close, volume;

    public CovarianceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        int i = 20;
        var now = Instant.now();
        BarSeries data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(6).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(7).volume(105).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(10).volume(130).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(13).volume(120).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(15).volume(180).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(12).volume(160).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(200).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(150).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(4).volume(85).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(3).volume(70).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(5).volume(90).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(8).volume(100).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(9).volume(95).add();
        data.barBuilder().endTime(now.minusSeconds(i--)).closePrice(11).volume(110).add();
        data.barBuilder().endTime(now.minusSeconds(i)).closePrice(10).volume(95).add();
        close = new ClosePriceIndicator(data);
        volume = new VolumeIndicator(data, 2);
    }

    @Test
    public void usingBarCount5UsingClosePriceAndVolume() {
        var covar = new CovarianceIndicator(close, volume, 5);

        assertNumEquals(0, covar.getValue(0));
        assertNumEquals(26.25, covar.getValue(1));
        assertNumEquals(63.3333, covar.getValue(2));
        assertNumEquals(143.75, covar.getValue(3));
        assertNumEquals(156, covar.getValue(4));
        assertNumEquals(60.8, covar.getValue(5));
        assertNumEquals(15.2, covar.getValue(6));
        assertNumEquals(-17.6, covar.getValue(7));
        assertNumEquals(4, covar.getValue(8));
        assertNumEquals(11.6, covar.getValue(9));
        assertNumEquals(-14.4, covar.getValue(10));
        assertNumEquals(-100.2, covar.getValue(11));
        assertNumEquals(-70.0, covar.getValue(12));
        assertNumEquals(24.6, covar.getValue(13));
        assertNumEquals(35.0, covar.getValue(14));
        assertNumEquals(-19.0, covar.getValue(15));
        assertNumEquals(-47.8, covar.getValue(16));
        assertNumEquals(11.4, covar.getValue(17));
        assertNumEquals(55.8, covar.getValue(18));
        assertNumEquals(33.4, covar.getValue(19));
    }

    @Test
    public void firstValueShouldBeZero() {
        var covar = new CovarianceIndicator(close, volume, 5);
        assertNumEquals(0, covar.getValue(0));
    }

    @Test
    public void shouldBeZeroWhenBarCountIs1() {
        var covar = new CovarianceIndicator(close, volume, 1);
        assertNumEquals(0, covar.getValue(3));
        assertNumEquals(0, covar.getValue(8));
    }

    @Test
    public void preservesFiniteCovarianceWhenAnchorDifferencesOverflow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> large = new FixedIndicator<>(series, numFactory.numOf(-1e308), numFactory.numOf(1e308));
        Indicator<Num> small = new FixedIndicator<>(series, numFactory.numOf(-1e-308), numFactory.numOf(1e-308));

        Num expected = numFactory.numOf(1e308).multipliedBy(numFactory.numOf(1e-308));
        assertNumEquals(expected, new CovarianceIndicator(large, small, 2).getValue(1));
        assertNumEquals(expected, new CovarianceIndicator(small, large, 2).getValue(1));

        // Dividing the tiny source before multiplication would underflow for
        // DoubleNum even though its covariance with the large source is finite.
        small = new FixedIndicator<>(series, numFactory.numOf(-Double.MIN_VALUE), numFactory.numOf(Double.MIN_VALUE));
        Num expectedTiny = numFactory.numOf(1e308).multipliedBy(numFactory.numOf(Double.MIN_VALUE));
        assertNumEquals(1, new CovarianceIndicator(large, small, 2).getValue(1).dividedBy(expectedTiny));
        assertNumEquals(1, new CovarianceIndicator(small, large, 2).getValue(1).dividedBy(expectedTiny));

        BarSeries threeBars = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        large = new FixedIndicator<>(threeBars, numFactory.numOf(-1e308), numFactory.zero(), numFactory.numOf(1e308));
        small = new FixedIndicator<>(threeBars, numFactory.numOf(-1e-308), numFactory.zero(), numFactory.numOf(1e-308));
        Num expectedThree = expected.multipliedBy(numFactory.two()).dividedBy(numFactory.numOf(3));
        assertNumEquals(expectedThree, new CovarianceIndicator(large, small, 3).getValue(2), 1e-14);
        assertNumEquals(expectedThree, new CovarianceIndicator(small, large, 3).getValue(2), 1e-14);
    }

    @Test
    public void missingSingletonAtRetainedIndexIsUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        series.setMaximumBarCount(1);
        Indicator<Num> missing = new FixedIndicator<>(series, NaN.NaN, NaN.NaN);
        Indicator<Num> valid = new FixedIndicator<>(series, numFactory.one(), numFactory.two());

        assertThat(new CovarianceIndicator(missing, valid, 1).getValue(1).isNaN()).isTrue();
        assertThat(new CovarianceIndicator(valid, missing, 1).getValue(1).isNaN()).isTrue();
    }

    @Test
    public void missingSingletonDuringZeroOriginWarmupIsUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1).build();
        Indicator<Num> missing = new FixedIndicator<>(series, NaN.NaN);
        Indicator<Num> valid = new FixedIndicator<>(series, numFactory.one());

        assertThat(new CovarianceIndicator(missing, valid, 2).getValue(0).isNaN()).isTrue();
        assertThat(new CovarianceIndicator(valid, missing, 2).getValue(0).isNaN()).isTrue();
    }

    @Test
    public void anchorsWindowAtBeginIndexAfterRemoval() {
        // Evict the first four bars so beginIndex = 4; the retained (close, volume)
        // pairs sit at absolute indices 4..9: (5,10) (6,5) (7,14) (8,7) (9,18) (10,9).
        int i = 10;
        var now = Instant.now();
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] closes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        double[] volumes = { 4, 1, 6, 3, 10, 5, 14, 7, 18, 9 };
        for (int j = 0; j < closes.length; j++) {
            pruned.barBuilder().endTime(now.minusSeconds(i--)).closePrice(closes[j]).volume(volumes[j]).add();
        }
        pruned.setMaximumBarCount(6);

        var covar = new CovarianceIndicator(new ClosePriceIndicator(pruned), new VolumeIndicator(pruned, 1), 6);

        // Four retained pairs are insufficient for the six-bar window.
        assertThat(covar.getValue(7).isNaN()).isTrue();
        // Five pairs remain unavailable; the complete window starts at index nine.
        assertThat(covar.getValue(8).isNaN()).isTrue();
        assertThat(covar.getCountOfUnstableBars()).isEqualTo(5);
        // Window [4..9]: sum of products = 13.5 over 6 observations
        assertNumEquals(2.25, covar.getValue(9));
    }

    @Test
    public void acceptsAlignedInputsFromSeparateSeries() {
        BarSeries xSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries ySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        for (int j = 0; j < 5; j++) {
            xSeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(2 * (j + 1)).add();
            ySeries.barBuilder().endTime(now.minusSeconds(2L * (4 - j))).closePrice(5 * (j + 1)).add();
        }

        var covar = new CovarianceIndicator(new ClosePriceIndicator(xSeries), new ClosePriceIndicator(ySeries), 5);

        // Perfectly linear pairs (2,5), (4,10), (6,15), (8,20), (10,25):
        // population covariance = sum((x - 6)(y - 15)) / 5 = 100 / 5
        assertNumEquals(20, covar.getValue(4));
    }

    @Test
    public void retainedWindowBoundaryRequiresFullStableHistory() {
        BarSeries retained = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        retained.setMaximumBarCount(4);
        ClosePriceIndicator source = new ClosePriceIndicator(retained);
        CovarianceIndicator metric = new CovarianceIndicator(source, source, 3);
        assertThat(metric.getCountOfUnstableBars()).isEqualTo(2);
        assertThat(metric.getValue(3).isNaN()).isTrue();
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
        CovarianceIndicator metric = new CovarianceIndicator(source, source, 1);
        assertNumEquals(0, metric.calculate(Integer.MAX_VALUE));
    }

    @Test
    public void retainedBoundaryAlsoHonorsSourceWarmup() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        series.setMaximumBarCount(8);
        Indicator<Num> source = new ClosePriceIndicator(series) {
            @Override
            public int getCountOfUnstableBars() {
                return 4;
            }
        };
        CovarianceIndicator metric = new CovarianceIndicator(source, source, 3);
        assertThat(metric.getCountOfUnstableBars()).isEqualTo(6);
        assertThat(metric.getValue(7).isNaN()).isTrue();
        assertNumEquals(2.0 / 3, metric.getValue(8));
    }
}
