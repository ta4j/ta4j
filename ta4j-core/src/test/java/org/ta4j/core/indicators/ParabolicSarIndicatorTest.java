/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class ParabolicSarIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ParabolicSarIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void growingBarSeriesTest() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(now, 74.5, 75.1, 75.11, 74.06, 0, 0, 0, numFunction));

        MockBarSeries mockBarSeries = new MockBarSeries(bars);
        mockBarSeries.setMaximumBarCount(3);

        mockBarSeries.addBar(new MockBar(now.plusSeconds(1), 75.09, 75.9, 76.030000, 74.640000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(2), 79.99, 75.24, 76.269900, 75.060000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(3), 75.30, 75.17, 75.280000, 74.500000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(4), 75.16, 74.6, 75.310000, 74.540000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(5), 74.58, 74.1, 75.467000, 74.010000, 0, 0, 0, numFunction));

        ParabolicSarIndicator sar = new ParabolicSarIndicator(mockBarSeries);

        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getBeginIndex()));
        assertEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount()));

        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getRemovedBarsCount() + 1));
        assertNotEquals(NaN.NaN, sar.getValue(mockBarSeries.getEndIndex()));
    }

    @Test
    public void startUpAndDownTrendTest() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Bar> bars = new ArrayList<>();
        // values of removable bars are much higher to be sure that they will not affect
        // the result.
        bars.add(new MockBar(now.plusSeconds(1), 165.5, 175.1, 180.10, 170.1, 0, 0, 0, numFunction));
        bars.add(new MockBar(now.plusSeconds(2), 175.1, 185.1, 190.20, 180.2, 0, 0, 0, numFunction));
        bars.add(new MockBar(now.plusSeconds(3), 185.1, 195.1, 200.30, 190.3, 0, 0, 0, numFunction));

        MockBarSeries mockBarSeries = new MockBarSeries(bars);
        mockBarSeries.setMaximumBarCount(21);

        mockBarSeries.addBar(new MockBar(now.plusSeconds(4), 74.5, 75.1, 75.11, 74.06, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(5), 75.09, 75.9, 76.030000, 74.640000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(6), 79.99, 75.24, 76.269900, 75.060000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(7), 75.30, 75.17, 75.280000, 74.500000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(8), 75.16, 74.6, 75.310000, 74.540000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(9), 74.58, 74.1, 75.467000, 74.010000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(10), 74.01, 73.740000, 74.700000, 73.546000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(11), 73.71, 73.390000, 73.830000, 72.720000, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(12), 73.35, 73.25, 73.890000, 72.86, 0, 0, 0, numFunction));
        mockBarSeries.addBar(new MockBar(now.plusSeconds(13), 73.24, 74.36, 74.410000, 73, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(14), 74.36, 76.510000, 76.830000, 74.820000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(15), 76.5, 75.590000, 76.850000, 74.540000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(16), 75.60, 75.910000, 76.960000, 75.510000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(17), 75.82, 74.610000, 77.070000, 74.560000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(18), 74.75, 75.330000, 75.530000, 74.010000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(19), 75.33, 75.010000, 75.500000, 74.510000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(20), 75.0, 75.620000, 76.210000, 75.250000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(21), 75.63, 76.040000, 76.460000, 75.092800, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(22), 76.0, 76.450000, 76.450000, 75.435000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(23), 76.45, 76.260000, 76.470000, 75.840000, 0, 0, 0, numFunction));
        mockBarSeries
                .addBar(new MockBar(now.plusSeconds(24), 76.30, 76.850000, 77.000000, 76.190000, 0, 0, 0, numFunction));

        ParabolicSarIndicator sar = new ParabolicSarIndicator(mockBarSeries);

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
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(4261.48, 4285.08, 4485.39, 4200.74, numFunction)); // The first daily candle of BTCUSDT in
        // the Binance cryptocurrency exchange.
        // 17 Aug 2017
        bars.add(new MockBar(4285.08, 4108.37, 4371.52, 3938.77, numFunction)); // starting with down trend
        bars.add(new MockBar(4108.37, 4139.98, 4184.69, 3850.00, numFunction)); // hold trend...
        bars.add(new MockBar(4120.98, 4086.29, 4211.08, 4032.62, numFunction));
        bars.add(new MockBar(4069.13, 4016.00, 4119.62, 3911.79, numFunction));
        bars.add(new MockBar(4016.00, 4040.00, 4104.82, 3400.00, numFunction));
        bars.add(new MockBar(4040.00, 4114.01, 4265.80, 4013.89, numFunction));
        bars.add(new MockBar(4147.00, 4316.01, 4371.68, 4085.01, numFunction)); // switch to up trend
        bars.add(new MockBar(4316.01, 4280.68, 4453.91, 4247.48, numFunction)); // hold trend
        bars.add(new MockBar(4280.71, 4337.44, 4367.00, 4212.41, numFunction));

        ParabolicSarIndicator sar = new ParabolicSarIndicator(new MockBarSeries(bars));

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
        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(4261.48, 4285.08, 4485.39, 4200.74, numFunction));
        bars.add(new MockBar(4285.08, 4108.37, 4371.52, 3938.77, numFunction));
        bars.add(new MockBar(4108.37, 4139.98, 4184.69, 3850.00, numFunction));
        bars.add(new MockBar(4120.98, 4086.29, 4211.08, 4032.62, numFunction));
        bars.add(new MockBar(4069.13, 4016.00, 4119.62, 3911.79, numFunction));
        bars.add(new MockBar(4016.00, 4040.00, 4104.82, 3400.00, numFunction));
        bars.add(new MockBar(4040.00, 4114.01, 4265.80, 4013.89, numFunction));
        bars.add(new MockBar(4147.00, 4316.01, 4371.68, 4085.01, numFunction));
        bars.add(new MockBar(4316.01, 4280.68, 4453.91, 4247.48, numFunction));
        bars.add(new MockBar(4280.71, 4337.44, 4367.00, 4212.41, numFunction));

        final ParabolicSarIndicator parabolicSarIndicator = new ParabolicSarIndicator(new MockBarSeries(bars));

        final List<Num> values = IntStream.range(0, bars.size())
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