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
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DifferencePercentageIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private DifferencePercentageIndicator percentageChangeIndicator;

    public DifferencePercentageIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValueWithoutThreshold() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var mockIndicator = new FixedIndicator<>(series, numOf(100), numOf(101), numOf(98.98), numOf(102.186952),
                numOf(91.9682568), numOf(100.5213046824), numOf(101.526517729224));

        percentageChangeIndicator = new DifferencePercentageIndicator(mockIndicator);
        assertNumEquals(NaN.NaN, percentageChangeIndicator.getValue(0));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(1));
        assertNumEquals(numOf(-2), percentageChangeIndicator.getValue(2));
        assertNumEquals(numOf(3.24), percentageChangeIndicator.getValue(3));
        assertNumEquals(numOf(-10), percentageChangeIndicator.getValue(4));
        assertNumEquals(numOf(9.3), percentageChangeIndicator.getValue(5));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(6));
    }

    @Test
    public void getValueWithNumThreshold() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1010), numOf(1020), numOf(1050),
                numOf(1060.5), numOf(1081.5), numOf(1102.5), numOf(1091.475), numOf(1113.525), numOf(1036.35),
                numOf(1067.4405));

        percentageChangeIndicator = new DifferencePercentageIndicator(mockIndicator, numOf(5));
        assertNumEquals(NaN.NaN, percentageChangeIndicator.getValue(0));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(1));
        assertNumEquals(numOf(2), percentageChangeIndicator.getValue(2));
        assertNumEquals(numOf(5), percentageChangeIndicator.getValue(3));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(4));
        assertNumEquals(numOf(3), percentageChangeIndicator.getValue(5));
        assertNumEquals(numOf(5), percentageChangeIndicator.getValue(6));
        assertNumEquals(numOf(-1), percentageChangeIndicator.getValue(7));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(8));
        assertNumEquals(numOf(-6), percentageChangeIndicator.getValue(9));
        assertNumEquals(numOf(3), percentageChangeIndicator.getValue(10));
    }

    @Test
    public void getValueWithNumberThreshold() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1000), numOf(1010), numOf(1025),
                numOf(1038.325));

        percentageChangeIndicator = new DifferencePercentageIndicator(mockIndicator, 1.5);
        assertNumEquals(NaN.NaN, percentageChangeIndicator.getValue(0));
        assertNumEquals(numOf(0), percentageChangeIndicator.getValue(1));
        assertNumEquals(numOf(1), percentageChangeIndicator.getValue(2));
        assertNumEquals(numOf(2.5), percentageChangeIndicator.getValue(3));
        assertNumEquals(numOf(1.3), percentageChangeIndicator.getValue(4));
    }

    @Test
    public void getValueWithOtherPreviousIndicator() {

        var endTime = Instant.now();
        var duration = Duration.ofSeconds(1);
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(2.8)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(2.9)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2.5)
                .highPrice(2)
                .lowPrice(2)
                .closePrice(2.4)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(2.0)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(3.0)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(3))
                .openPrice(3)
                .highPrice(3)
                .lowPrice(3)
                .closePrice(3.2)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(4))
                .openPrice(4)
                .highPrice(4)
                .lowPrice(4)
                .closePrice(3.5)
                .add();

        var openPrice = new OpenPriceIndicator(series);
        var closePrice = new ClosePriceIndicator(series);
        var diff = new DifferencePercentageIndicator(openPrice, closePrice);

        // index: 0: beginIndex <= index
        assertNumEquals(NaN.NaN, diff.getValue(0));

        // index: 1: currentOpenPrice = 2.5, previousClosePrice = 2.9
        assertNumEquals(-13.79310344827586, diff.getValue(1));

        // index: 2: currentOpenPrice = 2.0, previousClosePrice = 2.4
        assertNumEquals(-16.666666666666666666666666666667, diff.getValue(2));

        // index: 3: currentOpenPrice = 3.0, previousClosePrice = 3.0
        assertNumEquals(0, diff.getValue(3));

        // index: 4: currentOpenPrice = 4.0, previousClosePrice = 3.2
        assertNumEquals(24.999999999999993, diff.getValue(4));
    }

    @Test
    public void getValueWithOtherPreviousIndicatorAndThreshold() {

        var endTime = Instant.now();
        var duration = Duration.ofSeconds(1);
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(2.8)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(2.9)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2.5)
                .highPrice(2)
                .lowPrice(2)
                .closePrice(2.4)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(2.0)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(3.0)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(3))
                .openPrice(3)
                .highPrice(3)
                .lowPrice(3)
                .closePrice(3.2)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(4))
                .openPrice(4)
                .highPrice(4)
                .lowPrice(4)
                .closePrice(3.5)
                .add();

        var openPrice = new OpenPriceIndicator(series);
        var closePrice = new ClosePriceIndicator(series);
        var diff = new DifferencePercentageIndicator(openPrice, closePrice, numOf(100));

        // index: 0: beginIndex <= index
        assertNumEquals(NaN.NaN, diff.getValue(0));

        // index: 1: currentOpenPrice = 2.5, previousClosePrice = 2.9
        assertNumEquals(-13.79310344827586, diff.getValue(1));

        // index: 2: currentOpenPrice = 2.0, previousClosePrice = 2.4
        assertNumEquals(-31.034482758620683, diff.getValue(2));

        // index: 3: currentOpenPrice = 3.0, previousClosePrice = 3.0
        assertNumEquals(3.448275862068968, diff.getValue(3));

        // index: 4: currentOpenPrice = 4.0, previousClosePrice = 3.2
        assertNumEquals(37.93103448275863, diff.getValue(4));
    }
}
