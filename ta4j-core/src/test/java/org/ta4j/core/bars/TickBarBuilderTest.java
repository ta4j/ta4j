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
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;

public class TickBarBuilderTest {

    @Test
    public void add() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(5)).build();
        final var now = Instant.now();
        series.barBuilder().timePeriod(Duration.ofDays(1)).endTime(now).closePrice(1).volume(1).add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(1)))
                .closePrice(2)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(1)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(4)
                .volume(2)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(6, bar.getVolume());
        assertNumEquals(1, bar.getOpenPrice());
        assertNumEquals(4, bar.getClosePrice());
        assertNumEquals(5, bar.getHighPrice());
        assertNumEquals(1, bar.getLowPrice());

        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(2)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(6)))
                .closePrice(3)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(7)))
                .closePrice(6)
                .volume(2)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(8)))
                .closePrice(2)
                .volume(1)
                .add();
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(now.plus(Duration.ofDays(9)))
                .closePrice(5)
                .volume(2)
                .add();
        assertEquals(2, series.getBarCount());

        final var bar2 = series.getBar(1);
        assertNumEquals(7, bar2.getVolume());
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(5, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
    }
}
