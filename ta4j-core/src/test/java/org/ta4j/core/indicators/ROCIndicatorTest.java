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

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ROCIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final double[] closePriceValues = new double[] { 11045.27, 11167.32, 11008.61, 11151.83, 10926.77, 10868.12,
            10520.32, 10380.43, 10785.14, 10748.26, 10896.91, 10782.95, 10620.16, 10625.83, 10510.95, 10444.37,
            10068.01, 10193.39, 10066.57, 10043.75 };

    public ROCIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValueWhenBarCountIs12() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closePriceValues).build();
        var closePrice = new ClosePriceIndicator(series);
        var roc = new ROCIndicator(closePrice, 12);

        // Incomplete time frame
        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(1.105, roc.getValue(1));
        assertNumEquals(-0.3319, roc.getValue(2));
        assertNumEquals(0.9648, roc.getValue(3));

        // Complete time frame
        double[] results13to20 = { -3.8488, -4.8489, -4.5206, -6.3439, -7.8592, -6.2083, -4.3131, -3.2434 };
        for (int i = 0; i < results13to20.length; i++) {
            assertNumEquals(results13to20[i], roc.getValue(i + 12));
        }
    }

    @Test
    public void getValueWhenBarCountIs1WithOtherPreviousIndicator() {
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
        var roc = new ROCIndicator(openPrice, closePrice, 1);

        // index: 0: currentOpenPrice = 2.8, previousClosePrice (from same bar) = 2.9
        assertNumEquals(-3.448275862068969, roc.getValue(0));

        // index: 1: currentOpenPrice = 2.5, previousClosePrice = 2.9
        assertNumEquals(-13.79310344827586, roc.getValue(1));

        // index: 2: currentOpenPrice = 2.0, previousClosePrice = 2.4
        assertNumEquals(-16.666666666666666666666666666667, roc.getValue(2));

        // index: 3: currentOpenPrice = 3.0, previousClosePrice = 3.0
        assertNumEquals(0, roc.getValue(3));

        // index: 4: currentOpenPrice = 4.0, previousClosePrice = 3.2
        assertNumEquals(24.999999999999993, roc.getValue(4));

    }
}
