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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

public class BeforeRuleTest {

    @Test
    public void isSatisfied() {
        var series = new BaseBarSeriesBuilder().withName(BeforeRuleTest.class.getSimpleName()).build();

        var endTime = Instant.now();
        var duration = Duration.ofSeconds(1);
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(1)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2)
                .highPrice(2)
                .lowPrice(2)
                .closePrice(2)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(1)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(3))
                .openPrice(3)
                .highPrice(3)
                .lowPrice(3)
                .closePrice(3)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(4))
                .openPrice(4)
                .highPrice(4)
                .lowPrice(4)
                .closePrice(4)
                .add();

        var constant1dot5 = new ConstantIndicator<Num>(series, series.numFactory().numOf(1.5));
        var closePriceIndicator = new ClosePriceIndicator(series);
        var crossUp = new CrossedUpIndicatorRule(closePriceIndicator, constant1dot5);
        var crossDown = new CrossedDownIndicatorRule(closePriceIndicator, constant1dot5);
        var alwaysTrue = BooleanRule.TRUE;

        var rule = new BeforeRule(series, crossUp, alwaysTrue, crossDown);
        assertFalse(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
    }
}
