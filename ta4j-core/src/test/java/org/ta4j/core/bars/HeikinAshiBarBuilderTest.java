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

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeikinAshiBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public HeikinAshiBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    private final HeikinAshiBarBuilder unit = new HeikinAshiBarBuilder(numFactory);

    @Test
    public void testBuild() {
        var inputBar = new BaseBar(Duration.ofHours(1), null, Instant.parse("2024-01-01T01:00:00Z"),
                numFactory.numOf(100), numFactory.numOf(110), numFactory.numOf(95), numFactory.numOf(105),
                numFactory.numOf(10), numFactory.numOf(1000), 1);

        // No previous HA data: should return bar as-is.
        var resultBar = unit.timePeriod(inputBar.getTimePeriod())
                .endTime(inputBar.getEndTime())
                .openPrice(inputBar.getOpenPrice())
                .highPrice(inputBar.getHighPrice())
                .lowPrice(inputBar.getLowPrice())
                .closePrice(inputBar.getClosePrice())
                .volume(inputBar.getVolume())
                .amount(inputBar.getAmount())
                .trades(inputBar.getTrades())
                .build();

        assertEquals(inputBar.getOpenPrice(), resultBar.getOpenPrice());
        assertEquals(inputBar.getHighPrice(), resultBar.getHighPrice());
        assertEquals(inputBar.getLowPrice(), resultBar.getLowPrice());
        assertEquals(inputBar.getClosePrice(), resultBar.getClosePrice());

        // Setup for second bar with previous HA data
        var builderWithPrevious = new HeikinAshiBarBuilder().previousHeikinAshiOpenPrice(numFactory.numOf(100))
                .previousHeikinAshiClosePrice(numFactory.numOf(105))
                .timePeriod(inputBar.getTimePeriod())
                .endTime(inputBar.getEndTime())
                .openPrice(inputBar.getOpenPrice())
                .highPrice(inputBar.getHighPrice())
                .lowPrice(inputBar.getLowPrice())
                .closePrice(inputBar.getClosePrice())
                .volume(inputBar.getVolume())
                .amount(inputBar.getAmount())
                .trades(inputBar.getTrades());

        var haBar = builderWithPrevious.build();

        // Heikin-Ashi formula checks
        var haCloseExpected = (numFactory.numOf(100)
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(95))
                .plus(numFactory.numOf(105))).dividedBy(numFactory.numOf(4));
        var haOpenExpected = (numFactory.numOf(100).plus(numFactory.numOf(105))).dividedBy(numFactory.numOf(2));

        assertEquals(haOpenExpected, haBar.getOpenPrice());
        assertEquals(haCloseExpected, haBar.getClosePrice());
    }

}