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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

public class OpenedPositionMinimumBarCountRuleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testAtLeastBarCountRuleForNegativeNumberShouldThrowException() {
        new OpenedPositionMinimumBarCountRule(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAtLeastBarCountRuleForZeroShouldThrowException() {
        new OpenedPositionMinimumBarCountRule(0);
    }

    @Test
    public void testAtLeastOneBarRuleForOpenedTrade() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastMoreThanOneBarRuleForOpenedTrade() {
        final var rule = new OpenedPositionMinimumBarCountRule(2);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastBarCountRuleForClosedTradeShouldAlwaysReturnsFalse() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1, 2, 3, 4)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void testAtLeastBarCountRuleForEmptyTradingRecordShouldAlwaysReturnsFalse() {
        final var rule = new OpenedPositionMinimumBarCountRule(1);
        final var tradingRecord = new BaseTradingRecord();

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }
}
