/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core;

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;

public class SeriesBuilderTest {

    private final BaseBarSeriesBuilder seriesBuilder = new BaseBarSeriesBuilder()
            .withNumFactory(DecimalNumFactory.getInstance());

    @Test
    public void testBuilder() {

        // build a new empty unnamed bar series
        BarSeries defaultSeries = seriesBuilder.build();

        // build a new empty bar series using BigDecimal as delegate
        BarSeries defaultSeriesName = seriesBuilder.withName("default").build();

        BarSeries doubleSeries = seriesBuilder.withMaxBarCount(100)
                .withNumFactory(DoubleNumFactory.getInstance())
                .withName("useDoubleNum")
                .build();
        BarSeries precisionSeries = seriesBuilder.withMaxBarCount(100)
                .withNumFactory(DecimalNumFactory.getInstance())
                .withName("usePrecisionNum")
                .build();

        var now = Instant.now();
        for (int i = 1000; i >= 0; i--) {
            defaultSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            defaultSeriesName.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            doubleSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
            precisionSeries.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(now.minusSeconds(i))
                    .openPrice(i)
                    .closePrice(i)
                    .highPrice(i)
                    .lowPrice(i)
                    .volume(i)
                    .add();
        }

        assertNumEquals(0, defaultSeries.getBar(1000).getClosePrice());
        assertNumEquals(1000, defaultSeries.getBar(0).getClosePrice());
        assertEquals(defaultSeriesName.getName(), "default");
        assertNumEquals(99, doubleSeries.getBar(0).getClosePrice());
        assertNumEquals(99, precisionSeries.getBar(0).getClosePrice());
    }

    @Test
    public void testNumFunctions() {
        BarSeries series = seriesBuilder.withNumFactory(DoubleNumFactory.getInstance()).build();
        assertNumEquals(series.numFactory().numOf(12), DoubleNum.valueOf(12));
    }

    @Test
    public void testWrongNumType() {
        BarSeries series = seriesBuilder.withNumFactory(DecimalNumFactory.getInstance()).build();
        assertNumEquals(series.numFactory().numOf(12), DecimalNum.valueOf(12));
    }
}
