/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

@RunWith(Parameterized.class)
public class ConvertibleBaseBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public ConvertibleBaseBarBuilderTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testBuildBigDecimal() {
        new ConvertibleBaseBarBuilder<BigDecimal>(DecimalNum::valueOf);

        final ZonedDateTime beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        final Duration duration = Duration.between(beginTime, endTime);

        final BaseBar bar = new ConvertibleBaseBarBuilder<BigDecimal>(this::numOf).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertNumEquals(numOf(101.0), bar.getOpenPrice());
        assertNumEquals(numOf(103), bar.getHighPrice());
        assertNumEquals(numOf(100), bar.getLowPrice());
        assertNumEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertNumEquals(numOf(40), bar.getVolume());
        assertNumEquals(numOf(4020), bar.getAmount());
    }
}
