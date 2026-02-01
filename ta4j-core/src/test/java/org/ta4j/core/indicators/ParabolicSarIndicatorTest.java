/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ParabolicSarIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ParabolicSarIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void growingBarSeriesTest() {
        var now = Instant.now();
        var mockBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        mockBarSeries.barBuilder()
                .endTime(now)
                .openPrice(74.5)
                .closePrice(75.1)
                .highPrice(75.11)
                .lowPrice(74.06)
                .build();

        mockBarSeries.setMaximumBarCount(3);

        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(1))
                .openPrice(75.09)
                .closePrice(75.9)
                .highPrice(76.030000)
                .lowPrice(74.640000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(2))
                .openPrice(79.99)
                .closePrice(75.24)
                .highPrice(76.269900)
                .lowPrice(75.060000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(3))
                .openPrice(75.30)
                .closePrice(75.17)
                .highPrice(75.280000)
                .lowPrice(74.500000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(4))
                .openPrice(75.16)
                .closePrice(74.6)
                .highPrice(75.310000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(5))
                .openPrice(74.58)
                .closePrice(74.1)
                .highPrice(75.467000)
                .lowPrice(74.010000)
                .add();

        var sar = new ParabolicSarIndicator(mockBarSeries);

        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getBeginIndex()));
        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount()));

        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount() + 1));
        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getEndIndex()));
    }

    @Test
    public void startUpAndDownTrendTest() {
        var now = Instant.now();
        var mockBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // values of removable bars are much higher to be sure that they will not affect
        // the result.
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(1))
                .openPrice(165.5)
                .closePrice(175.1)
                .highPrice(180.10)
                .lowPrice(170.1)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(2))
                .openPrice(175.1)
                .closePrice(185.1)
                .highPrice(190.20)
                .lowPrice(180.2)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(3))
                .openPrice(185.1)
                .closePrice(195.1)
                .highPrice(200.30)
                .lowPrice(190.3)
                .add();

        mockBarSeries.setMaximumBarCount(21);

        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(4))
                .openPrice(74.5)
                .closePrice(75.1)
                .highPrice(75.11)
                .lowPrice(74.06)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(5))
                .openPrice(75.09)
                .closePrice(75.9)
                .highPrice(76.030000)
                .lowPrice(74.640000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(6))
                .openPrice(79.99)
                .closePrice(75.24)
                .highPrice(76.269900)
                .lowPrice(75.060000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(7))
                .openPrice(75.30)
                .closePrice(75.17)
                .highPrice(75.280000)
                .lowPrice(74.500000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(8))
                .openPrice(75.16)
                .closePrice(74.6)
                .highPrice(75.310000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(9))
                .openPrice(74.58)
                .closePrice(74.1)
                .highPrice(75.467000)
                .lowPrice(74.010000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(10))
                .openPrice(74.01)
                .closePrice(73.740000)
                .highPrice(74.700000)
                .lowPrice(73.546000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(11))
                .openPrice(73.71)
                .closePrice(73.390000)
                .highPrice(73.830000)
                .lowPrice(72.720000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(12))
                .openPrice(73.35)
                .closePrice(73.25)
                .highPrice(73.890000)
                .lowPrice(72.86)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(13))
                .openPrice(73.24)
                .closePrice(74.36)
                .highPrice(74.410000)
                .lowPrice(73)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(14))
                .openPrice(74.36)
                .closePrice(76.510000)
                .highPrice(76.830000)
                .lowPrice(74.820000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(15))
                .openPrice(76.5)
                .closePrice(75.590000)
                .highPrice(76.850000)
                .lowPrice(74.540000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(16))
                .openPrice(75.60)
                .closePrice(75.910000)
                .highPrice(76.960000)
                .lowPrice(75.510000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(17))
                .openPrice(75.82)
                .closePrice(74.610000)
                .highPrice(77.070000)
                .lowPrice(74.560000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(18))
                .openPrice(74.75)
                .closePrice(75.330000)
                .highPrice(75.530000)
                .lowPrice(74.010000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(19))
                .openPrice(75.33)
                .closePrice(75.010000)
                .highPrice(75.500000)
                .lowPrice(74.510000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(20))
                .openPrice(75.0)
                .closePrice(75.620000)
                .highPrice(76.210000)
                .lowPrice(75.250000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(21))
                .openPrice(75.63)
                .closePrice(76.040000)
                .highPrice(76.460000)
                .lowPrice(75.092800)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(22))
                .openPrice(76.0)
                .closePrice(76.450000)
                .highPrice(76.450000)
                .lowPrice(75.435000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(23))
                .openPrice(76.45)
                .closePrice(76.260000)
                .highPrice(76.470000)
                .lowPrice(75.840000)
                .add();
        mockBarSeries.barBuilder()
                .endTime(now.plusSeconds(24))
                .openPrice(76.30)
                .closePrice(76.850000)
                .highPrice(77.000000)
                .lowPrice(76.190000)
                .add();

        var sar = new ParabolicSarIndicator(mockBarSeries);

        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount())); // first bar in series
        assertNumEquals(74.06, sar.getValue(mockBarSeries.getRemovedBarsCount() + 1));
        assertNumEquals(74.06, sar.getValue(mockBarSeries.getRemovedBarsCount() + 2)); // start with up trend
        assertNumEquals(74.148396, sar.getValue(mockBarSeries.getRemovedBarsCount() + 3)); // switch to downtrend
        assertNumEquals(74.23325616000001, sar.getValue(mockBarSeries.getRemovedBarsCount() + 4)); // hold trend...
        assertNumEquals(76.2699, sar.getValue(mockBarSeries.getRemovedBarsCount() + 5));
        assertNumEquals(76.22470200000001, sar.getValue(mockBarSeries.getRemovedBarsCount() + 6));
        assertNumEquals(76.11755392, sar.getValue(mockBarSeries.getRemovedBarsCount() + 7));
        assertNumEquals(75.9137006848, sar.getValue(mockBarSeries.getRemovedBarsCount() + 8));
        assertNumEquals(75.72207864371201, sar.getValue(mockBarSeries.getRemovedBarsCount() + 9)); // switch to up trend
        assertNumEquals(72.72, sar.getValue(mockBarSeries.getRemovedBarsCount() + 10));// hold trend
        assertNumEquals(72.8022, sar.getValue(mockBarSeries.getRemovedBarsCount() + 11)); // ??? trend changed?
        assertNumEquals(72.964112, sar.getValue(mockBarSeries.getRemovedBarsCount() + 12));
        assertNumEquals(73.20386528, sar.getValue(mockBarSeries.getRemovedBarsCount() + 13));
        assertNumEquals(73.5131560576, sar.getValue(mockBarSeries.getRemovedBarsCount() + 14));
        assertNumEquals(73.797703572992, sar.getValue(mockBarSeries.getRemovedBarsCount() + 15));
        assertNumEquals(74.01, sar.getValue(mockBarSeries.getRemovedBarsCount() + 16));
        assertNumEquals(74.2548, sar.getValue(mockBarSeries.getRemovedBarsCount() + 17));
        assertNumEquals(74.480016, sar.getValue(mockBarSeries.getRemovedBarsCount() + 18));
        assertNumEquals(74.68721472, sar.getValue(mockBarSeries.getRemovedBarsCount() + 19));
        assertNumEquals(74.8778375424, sar.getValue(mockBarSeries.getRemovedBarsCount() + 20));
    }

    @Test
    public void startWithDownAndUpTrendTest() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // The first daily candle of BTCUSDT in the Binance cryptocurrency exchange. 17
        // Aug 2017..
        series.barBuilder().openPrice(4261.48).closePrice(4285.08).highPrice(4485.39).lowPrice(4200.74).add();
        // starting with down trend..
        series.barBuilder().openPrice(4285.08).closePrice(4108.37).highPrice(4371.52).lowPrice(3938.77).add();
        // hold trend...
        series.barBuilder().openPrice(4108.37).closePrice(4139.98).highPrice(4184.69).lowPrice(3850.00).add();
        series.barBuilder().openPrice(4120.98).closePrice(4086.29).highPrice(4211.08).lowPrice(4032.62).add();
        series.barBuilder().openPrice(4069.13).closePrice(4016.00).highPrice(4119.62).lowPrice(3911.79).add();
        series.barBuilder().openPrice(4016.00).closePrice(4040.00).highPrice(4104.82).lowPrice(3400.00).add();
        series.barBuilder().openPrice(4040.00).closePrice(4114.01).highPrice(4265.80).lowPrice(4013.89).add();
        // switch to up trend
        series.barBuilder().openPrice(4147.00).closePrice(4316.01).highPrice(4371.68).lowPrice(4085.01).add();
        // hold trend
        series.barBuilder().openPrice(4316.01).closePrice(4280.68).highPrice(4453.91).lowPrice(4247.48).add();
        series.barBuilder().openPrice(4280.71).closePrice(4337.44).highPrice(4367.00).lowPrice(4212.41).add();

        var sar = new ParabolicSarIndicator(series);

        assertEquals(NaN.NaN, sar.getValue(0));
        assertNumEquals(4485.39000000, sar.getValue(1));
        assertNumEquals(4485.39000000, sar.getValue(2));
        assertNumEquals(4459.97440000, sar.getValue(3));
        assertNumEquals(4435.57542400, sar.getValue(4));
        assertNumEquals(4412.15240704, sar.getValue(5));
        assertNumEquals(4351.42326262, sar.getValue(6));
        assertNumEquals(3400.00000000, sar.getValue(7));
        assertNumEquals(3419.43360000, sar.getValue(8));
        assertNumEquals(3460.81265600, sar.getValue(9));
    }

    @Test
    public void testSameValueForSameIndex() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(4261.48).closePrice(4285.08).highPrice(4485.39).lowPrice(4200.74).add();
        series.barBuilder().openPrice(4285.08).closePrice(4108.37).highPrice(4371.52).lowPrice(3938.77).add();
        series.barBuilder().openPrice(4108.37).closePrice(4139.98).highPrice(4184.69).lowPrice(3850.00).add();
        series.barBuilder().openPrice(4120.98).closePrice(4086.29).highPrice(4211.08).lowPrice(4032.62).add();
        series.barBuilder().openPrice(4069.13).closePrice(4016.00).highPrice(4119.62).lowPrice(3911.79).add();
        series.barBuilder().openPrice(4016.00).closePrice(4040.00).highPrice(4104.82).lowPrice(3400.00).add();
        series.barBuilder().openPrice(4040.00).closePrice(4114.01).highPrice(4265.80).lowPrice(4013.89).add();
        series.barBuilder().openPrice(4147.00).closePrice(4316.01).highPrice(4371.68).lowPrice(4085.01).add();
        series.barBuilder().openPrice(4316.01).closePrice(4280.68).highPrice(4453.91).lowPrice(4247.48).add();
        series.barBuilder().openPrice(4280.71).closePrice(4337.44).highPrice(4367.00).lowPrice(4212.41).add();

        final ParabolicSarIndicator parabolicSarIndicator = new ParabolicSarIndicator(series);

        final List<Num> values = IntStream.range(0, series.getBarCount())
                .mapToObj(parabolicSarIndicator::getValue)
                .collect(Collectors.toList());

        assertNumEquals(values.get(0), parabolicSarIndicator.getValue(0));
        assertNumEquals(values.get(5), parabolicSarIndicator.getValue(5));
        assertNumEquals(values.get(1), parabolicSarIndicator.getValue(1));
        assertNumEquals(values.get(4), parabolicSarIndicator.getValue(4));
        assertNumEquals(values.get(2), parabolicSarIndicator.getValue(2));
        assertNumEquals(values.get(3), parabolicSarIndicator.getValue(3));
        assertNumEquals(values.get(1), parabolicSarIndicator.getValue(1));
        assertNumEquals(values.get(9), parabolicSarIndicator.getValue(9));
        assertNumEquals(values.get(9), parabolicSarIndicator.getValue(9));
        assertNumEquals(values.get(8), parabolicSarIndicator.getValue(8));
        assertNumEquals(values.get(7), parabolicSarIndicator.getValue(7));
        assertNumEquals(values.get(6), parabolicSarIndicator.getValue(6));
    }

}
