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
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.HFTBar;
import org.ta4j.core.num.DoubleNumFactory;

public class HFTBarBuilderTest {

    @Test
    public void testBuildHFTBar() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new HFTBarBuilderFactory()).build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        series.barBuilder().timePeriod(oneDay).endTime(now).openPrice(1).highPrice(1).lowPrice(1).closePrice(1).volume(1).amount(1).trades(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).openPrice(2).highPrice(2).lowPrice(2).closePrice(2).volume(1).amount(2).trades(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(2))).openPrice(5).highPrice(5).lowPrice(5).closePrice(5).volume(1).amount(5).trades(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(3))).openPrice(1).highPrice(1).lowPrice(1).closePrice(1).volume(1).amount(1).trades(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(4))).openPrice(4).highPrice(4).lowPrice(4).closePrice(4).volume(2).amount(8).trades(1).add();

        assertEquals(5, series.getBarCount());

        // Verify that all bars are HFTBar instances
        for (int i = 0; i < series.getBarCount(); i++) {
            assertTrue(series.getBar(i) instanceof HFTBar);
        }

        // Verify the properties of the first bar
        final var bar0 = series.getBar(0);
        assertNumEquals(1, bar0.getVolume());
        assertNumEquals(1, bar0.getOpenPrice());
        assertNumEquals(1, bar0.getClosePrice());
        assertNumEquals(1, bar0.getHighPrice());
        assertNumEquals(1, bar0.getLowPrice());
        assertEquals(oneDay, bar0.getTimePeriod());
        assertEquals(now, bar0.getEndTime());

        // Verify the properties of the last bar
        final var bar4 = series.getBar(4);
        assertNumEquals(2, bar4.getVolume());
        assertNumEquals(4, bar4.getOpenPrice());
        assertNumEquals(4, bar4.getClosePrice());
        assertNumEquals(4, bar4.getHighPrice());
        assertNumEquals(4, bar4.getLowPrice());
        assertEquals(oneDay, bar4.getTimePeriod());
        assertEquals(now.plus(Duration.ofDays(4)), bar4.getEndTime());
    }
}
