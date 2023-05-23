/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static org.junit.Assert.*;

public class TrailingATRStopLossRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingATRStopLossRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void givenTradingRecordIsNull_whenIsSatisfiedCalled_thenReturnFalse() {
        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(new MockBarSeries(numFunction, 1, 2, 3, 4, 5), 2, 1);

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
        assertFalse(rule.isSatisfied(3, null));
        assertFalse(rule.isSatisfied(4, null));
    }

    @Test
    public void givenTradingRecordIsClosed_whenIsSatisfiedCalled_thenReturnFalse() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(0);
        tradingRecord.exit(0);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(new MockBarSeries(numFunction, 1, 2, 3, 4, 5), 2, 1);

        assertFalse(rule.isSatisfied(0, tradingRecord));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void givenBuyTradingRecordWithOpenPosition_whenIsSatisfiedCalled_thenReturnCorrectValue() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 11, 11, 10, 1, numFunction)); // 0
        bars.add(new MockBar(12, 13, 13, 12, 1, numFunction)); // 1
        bars.add(new MockBar(13, 14, 15, 12, 1, numFunction)); // 2
        bars.add(new MockBar(14, 15, 16, 14, 1, numFunction)); // 3
        bars.add(new MockBar(15, 15, 16, 12, 1, numFunction)); // 4
        bars.add(new MockBar(14, 15, 15, 14, 1, numFunction)); // 5 initial entry
        bars.add(new MockBar(13, 13, 13, 13, 1, numFunction)); // 6
        bars.add(new MockBar(13, 15, 16, 12, 1, numFunction)); // 7
        bars.add(new MockBar(13, 15, 15, 12, 1, numFunction)); // 8
        bars.add(new MockBar(14, 11, 14, 11, 1, numFunction)); // 9
        bars.add(new MockBar(13, 11, 13, 10, 1, numFunction)); // 10
        bars.add(new MockBar(12, 10, 12, 10, 1, numFunction)); // 11
        BarSeries series = new MockBarSeries(bars);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 2, 1);

        // Enter at 15
        tradingRecord.enter(5, numOf(15), numOf(1));

        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));

        assertTrue(rule.isSatisfied(9, tradingRecord));
        assertTrue(rule.isSatisfied(10, tradingRecord));
        assertTrue(rule.isSatisfied(11, tradingRecord));

    }

    @Test
    public void givenBuyTradingRecordWithOpenPositionAndATRBarCountOf2_whenIsSatisfiedCalled_thenReturnCorrectValue() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 11, 11, 10, 1, numFunction)); // 0 initial entry
        bars.add(new MockBar(12, 13, 13, 12, 1, numFunction)); // 1
        bars.add(new MockBar(13, 14, 15, 12, 1, numFunction)); // 2
        bars.add(new MockBar(14, 15, 16, 14, 1, numFunction)); // 3
        bars.add(new MockBar(15, 15, 16, 12, 1, numFunction)); // 4
        bars.add(new MockBar(14, 15, 15, 14, 1, numFunction)); // 5
        bars.add(new MockBar(13, 13, 13, 13, 1, numFunction)); // 6
        bars.add(new MockBar(13, 15, 16, 12, 1, numFunction)); // 7
        bars.add(new MockBar(13, 15, 15, 12, 1, numFunction)); // 8
        bars.add(new MockBar(14, 11, 14, 11, 1, numFunction)); // 9
        bars.add(new MockBar(13, 11, 13, 10, 1, numFunction)); // 10
        bars.add(new MockBar(12, 10, 12, 10, 1, numFunction)); // 11
        BarSeries series = new MockBarSeries(bars);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 2, 2);

        tradingRecord.enter(0, numOf(11), numOf(1));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));
        assertFalse(rule.isSatisfied(10, tradingRecord));

        assertTrue(rule.isSatisfied(11, tradingRecord));
    }

    @Test
    public void givenSellTradingRecordWithOpenPosition_whenIsSatisfiedCalled_thenReturnCorrectValue() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 11, 11, 10, 1, numFunction)); // 0
        bars.add(new MockBar(12, 13, 13, 12, 1, numFunction)); // 1
        bars.add(new MockBar(13, 14, 15, 12, 1, numFunction)); // 2
        bars.add(new MockBar(14, 15, 16, 14, 1, numFunction)); // 3
        bars.add(new MockBar(15, 15, 16, 12, 1, numFunction)); // 4
        bars.add(new MockBar(14, 15, 15, 14, 1, numFunction)); // 5
        bars.add(new MockBar(13, 13, 13, 13, 1, numFunction)); // 6
        bars.add(new MockBar(13, 15, 16, 12, 1, numFunction)); // 7
        bars.add(new MockBar(13, 15, 15, 12, 1, numFunction)); // 8
        bars.add(new MockBar(14, 11, 14, 11, 1, numFunction)); // 9
        bars.add(new MockBar(13, 11, 13, 10, 1, numFunction)); // 10
        bars.add(new MockBar(12, 10, 12, 10, 1, numFunction)); // 11
        BarSeries series = new MockBarSeries(bars);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 1, 1);

        tradingRecord.enter(0, numOf(11), numOf(1));

        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));

        assertFalse(rule.isSatisfied(9, tradingRecord));
        assertFalse(rule.isSatisfied(10, tradingRecord));
        assertFalse(rule.isSatisfied(11, tradingRecord));
    }

    @Test
    public void givenZeroATRCoefficientAndOpenTradingRecord_whenIsSatisfiedCalled_thenReturnTrue() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 11, 11, 10, 1, numFunction)); // 0
        bars.add(new MockBar(12, 13, 13, 12, 1, numFunction)); // 1
        bars.add(new MockBar(13, 14, 15, 12, 1, numFunction)); // 2
        bars.add(new MockBar(14, 15, 16, 14, 1, numFunction)); // 3
        bars.add(new MockBar(15, 15, 16, 12, 1, numFunction)); // 4
        bars.add(new MockBar(14, 15, 15, 14, 1, numFunction)); // 5
        bars.add(new MockBar(13, 13, 13, 13, 1, numFunction)); // 6
        bars.add(new MockBar(13, 15, 16, 12, 1, numFunction)); // 7
        bars.add(new MockBar(13, 15, 15, 12, 1, numFunction)); // 8
        bars.add(new MockBar(14, 11, 14, 11, 1, numFunction)); // 9
        bars.add(new MockBar(13, 11, 13, 10, 1, numFunction)); // 10
        bars.add(new MockBar(12, 10, 12, 10, 1, numFunction)); // 11
        BarSeries series = new MockBarSeries(bars);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 2, 0);
        tradingRecord.enter(0, numOf(11), numOf(1));

        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
        assertTrue(rule.isSatisfied(8, tradingRecord));
        assertTrue(rule.isSatisfied(9, tradingRecord));
        assertTrue(rule.isSatisfied(10, tradingRecord));
        assertTrue(rule.isSatisfied(11, tradingRecord));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenNegativeATRCoefficient_whenInstantiated_thenThrowException() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        BarSeries series = new MockBarSeries(numFunction, 1, 2, 3, 4, 5);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 2, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenNegativeATRBarCount_whenInstantiated_thenThrowException() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        BarSeries series = new MockBarSeries(numFunction, 1, 2, 3, 4, 5);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, -2, 1);
    }

    @Test
    public void givenIncreasingPrices_whenIsSatisfiedCalled_thenReturnFalse() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        List<Bar> bars = new ArrayList<>();
        bars.add(new MockBar(10, 10, 10, 10, 1, numFunction)); // 0
        bars.add(new MockBar(11, 11, 11, 11, 1, numFunction)); // 1
        bars.add(new MockBar(12, 12, 12, 12, 1, numFunction)); // 2
        bars.add(new MockBar(13, 13, 13, 13, 1, numFunction)); // 3
        bars.add(new MockBar(14, 14, 14, 14, 1, numFunction)); // 4
        bars.add(new MockBar(15, 15, 15, 15, 1, numFunction)); // 5
        bars.add(new MockBar(16, 16, 16, 16, 1, numFunction)); // 6
        bars.add(new MockBar(17, 17, 17, 17, 1, numFunction)); // 7
        bars.add(new MockBar(18, 18, 18, 18, 1, numFunction)); // 8
        bars.add(new MockBar(19, 19, 19, 19, 1, numFunction)); // 9
        bars.add(new MockBar(20, 20, 20, 20, 1, numFunction)); // 10
        bars.add(new MockBar(21, 21, 21, 21, 1, numFunction)); // 11
        BarSeries series = new MockBarSeries(bars);

        TrailingATRStopLossRule rule = new TrailingATRStopLossRule(series, 2, 1);
        tradingRecord.enter(0, numOf(10), numOf(1));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));
        assertFalse(rule.isSatisfied(9, tradingRecord));
        assertFalse(rule.isSatisfied(10, tradingRecord));
        assertFalse(rule.isSatisfied(11, tradingRecord));
    }

}