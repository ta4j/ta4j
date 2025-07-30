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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

class AmountBarBuilderTest {

    @Test
    void createBarsWithSetAmountByVolume() {

        // setAmountByVolume = true:
        // => AmountBar.amount can only be built from closePrice*volume
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        final var bar = series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3);

        // should throw an exception as the amount cannot be explicitly set due to
        // "setAmountByVolume = true"
        Assertions.assertThrows(IllegalArgumentException.class, () -> bar.amount(1));
    }

    @Test
    void addWithSetAmountByVolume() {
        // setAmountByVolume = true
        // => amount is added by "volume*closePrice"
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3).add();

        // add bar 2:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 1 * 1 + 2 * 1 = 3
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();

        // add bar 3:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 = 8
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(7)
                .add();

        // add bar 4:
        // aggregated volume = 1 + 1 + 1 + 2 = 5
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 + 4 * 2 = 16
        // => sum of volume is 5 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 16 and 4 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 5 - 4 / 4 = 4
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(3))).closePrice(4).volume(2).add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume()); // adapted aggregated volume = 4
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
        assertEquals(numFactory.numOf(12), bar1.getAmount()); // amountThreshold = 12
        assertEquals(10, bar1.getTrades());

        // add bar 5:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 4 + 2 * 1 = 6
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(4))).closePrice(2).volume(1).add();

        // add bar 6:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 4 + 2 * 1 + 3 * 1 = 9
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .trades(5)
                .add();

        // add bar 7:
        // aggregated volume = 1 + 1 + 1 + 1 = 4
        // aggregated amount = 4 + 2 * 1 + 3 * 1 + 6 * 1 = 15
        // => sum of volume is 4 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 15 and 3 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 4 - 3 / 6 = 3.5
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(6).volume(1).add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume()); // adapted aggregated volume = 3.5
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount()); // amountThreshold = 12
        assertEquals(5, bar2.getTrades());
    }

    @Test
    void addWithoutSetAmountByVolume() {
        // setAmountByVolume = false:
        // => amount is added by provided "amount"-field
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, false))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).amount(1).trades(3).add();

        // add bar 2:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 1 * 1 + 2 * 1 = 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(1)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();

        // add bar 3:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 = 8
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .amount(5)
                .trades(7)
                .add();

        // add bar 4:
        // aggregated volume = 1 + 1 + 1 + 2 = 5
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 + 4 * 2 = 16
        // => sum of volume is 5 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 16 and 4 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 5 - 4 / 4 = 4
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(4)
                .volume(2)
                .amount(8)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume()); // adapted aggregated volume = 4
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
        assertEquals(numFactory.numOf(12), bar1.getAmount()); // amountThreshold = 12
        assertEquals(10, bar1.getTrades());

        // add bar 5:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 4 + 2 * 1 = 6
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();

        // add bar 6:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 4 + 2 * 1 + 3 * 1 = 9
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .amount(3)
                .trades(5)
                .add();

        // add bar 7:
        // aggregated volume = 1 + 1 + 1 + 1 = 4
        // aggregated amount = 4 + 2 * 1 + 3 * 1 + 6 * 1 = 15
        // => sum of volume is 4 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 15 and 3 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 4 - 3 / 6 = 3.5
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(6)))
                .closePrice(6)
                .volume(1)
                .amount(6)
                .add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume()); // adapted aggregated volume = 3.5
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount()); // amountThreshold = 12
        assertEquals(5, bar2.getTrades());
    }
}
