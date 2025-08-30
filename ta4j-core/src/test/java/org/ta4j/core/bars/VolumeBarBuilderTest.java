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
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

public class VolumeBarBuilderTest {

    @Test
    public void add() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(4)).build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1: aggregated volume = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3).add();

        // add bar 2: aggregated volume = 1 + 1 = 2
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();

        // add bar 3: aggregated volume = 1 + 1 + 1 = 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(7)
                .add();

        // add bar 4: aggregated volume = 1 + 1 + 1 + 2= 5
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(4)
                .volume(2) // sum is 5 and 1 moved to next bar (= remainder)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume());
        assertNumEquals(1, bar1.getOpenPrice());
        assertNumEquals(4, bar1.getClosePrice());
        assertNumEquals(5, bar1.getHighPrice());
        assertNumEquals(1, bar1.getLowPrice());
        assertEquals(oneDay.multipliedBy(4), bar1.getTimePeriod());
        final var beginTime0 = now.minus(oneDay);
        final var endTime4 = now.plus(Duration.ofDays(3));
        assertEquals(beginTime0, bar1.getBeginTime());
        assertEquals(endTime4, bar1.getEndTime());
        final var numFactory = DecimalNumFactory.getInstance();
        assertEquals(numFactory.numOf(16), bar1.getAmount()); // 1 * 1 + 1 * 2 + 1 * 5 + 2 * 4 = 16
        assertEquals(10, bar1.getTrades());

        // add bar 5: aggregated volume = 1 + 1= 2
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(2)
                .volume(1)
                .amount(12)
                .add();

        // add bar 6: aggregated volume = 1 + 1 + 1= 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .trades(5)
                .add();

        // add bar 7: aggregated volume = 1 + 1 + 1+ 1 = 4
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(6).volume(1).add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(4, bar2.getVolume());
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount());
        assertEquals(5, bar2.getTrades());
    }
}
