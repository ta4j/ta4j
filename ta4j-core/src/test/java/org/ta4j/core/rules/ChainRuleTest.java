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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.helper.ChainLink;

public class ChainRuleTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().withName(ChainRule.class.getSimpleName()).build();

        var endTime = Instant.now();
        var duration = Duration.ofSeconds(1);
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(2.9)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2)
                .highPrice(2)
                .lowPrice(2)
                .closePrice(4.3)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(1)
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
    }

    @Test
    public void testOnlyChainRules() {

        var closePriceIndicator = new ClosePriceIndicator(series);
        var overIndicatorRule = new OverIndicatorRule(closePriceIndicator, 2.9);

        // remain above the value for a number of bars
        var rule = new ChainRule(new ChainLink(overIndicatorRule, 2));

        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(0));

        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(1));

        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(2));

        // OverIndicator (index:3) satisfied: first 3.2, second = 2.9
        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        assertTrue(rule.isSatisfied(3));

        // OverIndicator (index:4) satisfied: first 3.5, second = 3.0
        // OverIndicator (index:3) satisfied: first 3.2, second = 3.0
        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        assertTrue(rule.isSatisfied(4));
    }

    @Test
    public void testInitialRule() {

        var closePriceIndicator = new ClosePriceIndicator(series);
        var crossUpIndicatorRule = new CrossedUpIndicatorRule(closePriceIndicator, 3);
        var overIndicatorRule = new OverIndicatorRule(closePriceIndicator, 2.8);

        // cross up a value and then remain above the value for a number of bars
        var rule = new ChainRule(crossUpIndicatorRule, new ChainLink(overIndicatorRule, 2));

        // we need at least 2 bars to test the crossUpRule
        assertFalse(rule.isSatisfied(0));

        // we need at least 2 bars to test the crossUpRule
        assertFalse(rule.isSatisfied(1));

        // CrossedUpIndicator (index:0): low 2.9, up = 3, crossed = false
        assertFalse(rule.isSatisfied(2));

        // CrossedUpIndicator (index:1): low 4.3, up = 3, crossed = true
        // OverIndicator (index:3) satsfied: first 3.2, second = 2.8
        // OverIndicator (index:2) satisfied: first 3.0, second = 2.8
        // OverIndicator (index:1) satisfied: first 4.3, second = 2.8
        assertTrue(rule.isSatisfied(3));

        // CrossedUpIndicator (index:2) not satisfied: low 3.0, up = 3, crossed = false
        assertFalse(rule.isSatisfied(4));
    }

    @Test
    public void testInitialAndCurrrentRule() {

        var closePriceIndicator = new ClosePriceIndicator(series);
        var overIndicatorRule = new OverIndicatorRule(closePriceIndicator, 2.9);
        var underIndicatorRule = new UnderIndicatorRule(closePriceIndicator, 3.5);

        // remain above the value for a number of bars but the current index should be
        // below a value
        var rule = new ChainRule(null, underIndicatorRule, new ChainLink(overIndicatorRule, 2));

        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(0));

        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(1));

        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        // OverIndicator (index:0) not satisfied: first 2.9, second = 2.9
        assertFalse(rule.isSatisfied(2));

        // OverIndicator (index:3) satisfied: first 3.2, second = 2.9
        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        // OverIndicator (index:1) satisfied: first 4.3, second = 2.9
        assertTrue(rule.isSatisfied(3));

        // UnderIndicator (index:4) not satisfied: first 3.5, second = 3.5
        // OverIndicator (index:4) satisfied: first 3.5, second = 3.0
        // OverIndicator (index:3) satisfied: first 3.2, second = 3.0
        // OverIndicator (index:2) satisfied: first 3.0, second = 2.9
        assertFalse(rule.isSatisfied(4));
    }

}
