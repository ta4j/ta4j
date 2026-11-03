/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.SinglePrecisionNumFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public WMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculate() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> wmaIndicator = new WMAIndicator(close, 3);

        int unstableBars = wmaIndicator.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(wmaIndicator.getValue(i))).isTrue();
        }
        assertNumEquals(2.3333, wmaIndicator.getValue(2));
        assertNumEquals(3.3333, wmaIndicator.getValue(3));
        assertNumEquals(4.3333, wmaIndicator.getValue(4));
        assertNumEquals(5.3333, wmaIndicator.getValue(5));
    }

    @Test
    public void wmaWithBarCountGreaterThanSeriesSize() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> wmaIndicator = new WMAIndicator(close, 55);

        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(wmaIndicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void historicalReplacementAndExpirationUpdateWeightedAverages() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var close = new ClosePriceIndicator(series);
        var wma = new WMAIndicator(close, 3);
        for (int i = 2; i <= 3; i++) {
            wma.getValue(i);
        }
        Bar old = series.getBar(1);
        series.replaceBar(1,
                series.barBuilder()
                        .timePeriod(old.getTimePeriod())
                        .endTime(old.getEndTime())
                        .openPrice(20)
                        .highPrice(20)
                        .lowPrice(20)
                        .closePrice(20)
                        .volume(old.getVolume())
                        .build());

        assertNumEquals(38.0 / 6, wma.getValue(3));
        assertNumEquals(26.0 / 6, wma.getValue(4));
    }

    @Test
    public void weightedAverageRecoversAfterLargeValuesExpire() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1e100, 1e100, 1, 2, 3, 4)
                .build();
        var close = new ClosePriceIndicator(series);
        Indicator<Num> average = new WMAIndicator(close, 2);
        average.getValue(1);
        average.getValue(2);
        assertNumEquals(5.0 / 3, average.getValue(3));
        assertNumEquals(8.0 / 3, average.getValue(4));
    }

    @Test
    public void weightedAverageRebuildsWhenNonAnchorOutlierExpires() {
        for (double spike : new double[] { 1e100, -1e100 }) {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
            Indicator<Num> source = new MockIndicator(series, 0, numOf(1), numOf(spike), numOf(1), numOf(2), numOf(3),
                    numOf(4));
            Indicator<Num> average = new WMAIndicator(source, 3);
            average.getValue(2);
            average.getValue(3);
            assertNumEquals(14.0 / 6, average.getValue(4));
            assertNumEquals(20.0 / 6, average.getValue(5));
        }
    }

    @Test
    public void weightedAverageRecoversAsBoundedHistoryAdvances() {
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1e100, 1, 2).build();
        series.setMaximumBarCount(4);
        Indicator<Num> average = new WMAIndicator(new ClosePriceIndicator(series), 3);
        average.getValue(2);
        average.getValue(3);
        series.barBuilder().closePrice(3).add();
        assertNumEquals(14.0 / 6, average.getValue(4));
        series.barBuilder().closePrice(4).add();
        assertNumEquals(20.0 / 6, average.getValue(5));
    }

    @Test
    public void weightedAverageRebuildsForSinglePrecisionCancellation() {
        NumFactory floats = SinglePrecisionNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floats).withData(1, 1, 1, 1, 1, 1).build();
        Indicator<Num> source = new MockIndicator(series, 0, floats.numOf(1), floats.numOf(1e8), floats.numOf(1),
                floats.numOf(2), floats.numOf(3), floats.numOf(4));
        Indicator<Num> average = new WMAIndicator(source, 3);
        average.getValue(2);
        average.getValue(3);
        Num expected = floats.numOf(3)
                .multipliedBy(source.getValue(4))
                .plus(floats.numOf(2).multipliedBy(source.getValue(3)))
                .plus(source.getValue(2))
                .dividedBy(floats.numOf(6));
        assertThat(average.getValue(4)).isEqualByComparingTo(expected);
    }

    @Test
    public void weightedAverageUsesScanOrderWhenLargeTermsCancel() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1).build();
        double magnitude = Math.scalb(1.0, 330);
        Indicator<Num> source = new MockIndicator(series, 0, numOf(1), numOf(1), numOf(-3 * magnitude),
                numOf(2 * magnitude), numOf(1));
        Indicator<Num> average = new WMAIndicator(source, 3);
        average.getValue(2);
        Num expected = numOf(3).multipliedBy(source.getValue(3))
                .plus(numOf(2).multipliedBy(source.getValue(2)))
                .plus(source.getValue(1))
                .dividedBy(numOf(6));
        assertThat(average.getValue(3)).isEqualByComparingTo(expected);
    }

    @Test
    public void weightedAverageRebuildsWhenCancellationThresholdWouldUnderflow() {
        double unit = Double.MIN_VALUE;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(unit, 1e-315, unit, unit, 2 * unit, 3 * unit)
                .build();
        Indicator<Num> source = new ClosePriceIndicator(series);
        Indicator<Num> average = new WMAIndicator(source, 3);
        average.getValue(2);
        average.getValue(3);
        Num expected = numOf(3).multipliedBy(source.getValue(4))
                .plus(numOf(2).multipliedBy(source.getValue(3)))
                .plus(source.getValue(2))
                .dividedBy(numOf(6));
        assertThat(average.getValue(4)).isEqualByComparingTo(expected);
    }

    @Test
    public void exactZeroWindowsKeepLinearSourceReads() {
        double[] bars = new double[150];
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(bars).build();
        int[] reads = { 0 };
        Indicator<Num> source = new Indicator<>() {
            @Override
            public Num getValue(int index) {
                reads[0]++;
                return numOf(index % 3 == 1 ? -2 : 1);
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        Indicator<Num> average = new WMAIndicator(source, 30);
        for (int index = 29; index < bars.length; index++) {
            Num value = average.getValue(index);
            if ((index - 29) % 3 == 0) {
                assertThat(value.isZero()).isTrue();
            }
        }
        assertThat(reads[0]).isLessThan(6 * bars.length);
    }

    @Test
    public void wmaUsingBarCount9UsingClosePrice() {
        // Example from
        // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
                        82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87)
                .build();

        WMAIndicator wma = new WMAIndicator(new ClosePriceIndicator(data), 9);
        assertNumEquals(84.4958, wma.getValue(8));
        assertNumEquals(85.0158, wma.getValue(9));
        assertNumEquals(84.6807, wma.getValue(10));
        assertNumEquals(84.5387, wma.getValue(11));
        assertNumEquals(84.4298, wma.getValue(12));
        assertNumEquals(84.1224, wma.getValue(13));
        assertNumEquals(83.1031, wma.getValue(14));
        assertNumEquals(82.1462, wma.getValue(15));
        assertNumEquals(81.1149, wma.getValue(16));
        assertNumEquals(80.0736, wma.getValue(17));
        assertNumEquals(78.6907, wma.getValue(18));
        assertNumEquals(78.1504, wma.getValue(19));
        assertNumEquals(77.6133, wma.getValue(20));
    }
}
