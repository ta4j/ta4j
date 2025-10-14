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
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.MultiTradingRecord.MatchingPolicy;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

class MultiTradingRecordTest {

    private Num one;
    private Num two;
    private Num three;

    @BeforeEach
    void setUp() {
        // Use a simple num factory for creating test numbers
        one = org.ta4j.core.num.DecimalNum.valueOf(1);
        two = org.ta4j.core.num.DecimalNum.valueOf(2);
        three = org.ta4j.core.num.DecimalNum.valueOf(3);
    }

    @Test
    void enterCreatesDistinctOpenPositions() {
        MultiTradingRecord record = new MultiTradingRecord();

        assertTrue(record.enter(0, one, one));
        assertTrue(record.enter(1, two, two));

        List<Position> openPositions = record.getOpenPositions();
        assertEquals(2, openPositions.size());
        assertEquals(one, openPositions.get(0).getEntry().getAmount());
        assertEquals(two, openPositions.get(1).getEntry().getAmount());
        assertTrue(openPositions.get(0).isOpened());
        assertTrue(openPositions.get(1).isOpened());
        assertEquals(two, record.getCurrentPosition().getEntry().getAmount());
    }

    @Test
    void exitMatchesByAmountUsingFifo() {
        MultiTradingRecord record = new MultiTradingRecord();

        record.enter(0, one, one);
        record.enter(1, two, two);

        assertTrue(record.exit(2, three, one));
        assertEquals(1, record.getPositions().size());
        Position closed = record.getPositions().get(0);
        assertEquals(0, closed.getEntry().getIndex());
        assertEquals(one, closed.getEntry().getAmount());
        assertEquals(2, closed.getExit().getIndex());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(two, record.getOpenPositions().get(0).getEntry().getAmount());
    }

    @Test
    void exitFallsBackToPolicyWhenAmountIsNaN() {
        MultiTradingRecord record = new MultiTradingRecord();
        record.enter(0, one, one);
        record.enter(1, two, two);

        assertTrue(record.exit(2, three, NaN));
        Position closed = record.getPositions().get(0);
        assertEquals(0, closed.getEntry().getIndex());
        assertEquals(one, closed.getEntry().getAmount());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(two, record.getOpenPositions().get(0).getEntry().getAmount());
    }

    @Test
    void exitHonorsLifoMatchingPolicy() {
        MultiTradingRecord record = new MultiTradingRecord(Trade.TradeType.BUY, MatchingPolicy.LIFO);
        record.enter(0, one, one);
        record.enter(1, two, one);

        assertTrue(record.exit(2, three, one));
        Position closed = record.getPositions().get(0);
        assertEquals(1, closed.getEntry().getIndex());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(0, record.getOpenPositions().get(0).getEntry().getIndex());
    }

    @Test
    void findOpenPositionByAmountReturnsMatchingPosition() {
        MultiTradingRecord record = new MultiTradingRecord();
        record.enter(0, one, one);
        record.enter(1, two, two);

        Position result = record.findOpenPositionByAmount(two);
        assertEquals(1, result.getEntry().getIndex());
        assertNull(record.findOpenPositionByAmount(NaN));
    }

    @Test
    void operateDelegatesToEnterAndExit() {
        MultiTradingRecord record = new MultiTradingRecord(Trade.TradeType.BUY, MatchingPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel());

        record.operate(0, one, one);
        assertEquals(1, record.getOpenPositions().size());
        record.operate(1, two, one);
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositions().size());
    }

    @Test
    void getOpenPositionsOrderingSupportsLifo() {
        MultiTradingRecord record = new MultiTradingRecord();
        record.enter(0, one, one);
        record.enter(1, two, two);
        record.enter(2, three, three);

        List<Position> lifo = record.getOpenPositions(MatchingPolicy.LIFO);
        assertEquals(3, lifo.size());
        assertEquals(2, lifo.get(0).getEntry().getIndex());
        assertEquals(1, lifo.get(1).getEntry().getIndex());
        assertEquals(0, lifo.get(2).getEntry().getIndex());
    }

    @Test
    void returnsEmptyOpenPositionsWhenClosed() {
        MultiTradingRecord record = new MultiTradingRecord();
        assertTrue(record.getOpenPositions().isEmpty());
        assertTrue(record.isClosed());
    }

    @Test
    void getMatchingPolicyExposesConfiguredPolicy() {
        MultiTradingRecord record = new MultiTradingRecord(Trade.TradeType.SELL, MatchingPolicy.LIFO);
        assertEquals(MatchingPolicy.LIFO, record.getMatchingPolicy());
        assertEquals(Trade.TradeType.SELL, record.getStartingType());

        MultiTradingRecord named = new MultiTradingRecord("demo-record");
        assertEquals("demo-record", named.getName());
        assertEquals(MatchingPolicy.FIFO, named.getMatchingPolicy());
    }

    @Test
    void partialExitCreatesRemainingPosition() {
        MultiTradingRecord record = new MultiTradingRecord();

        // Enter with amount 3
        assertTrue(record.enter(0, one, three));
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(three, record.getOpenPositions().get(0).getEntry().getAmount());

        // Partial exit with amount 1
        assertTrue(record.exit(1, two, one));

        // Should have one closed position and one open position
        assertEquals(1, record.getPositions().size());
        assertEquals(1, record.getOpenPositions().size());

        // Closed position should have entry amount 3 and exit amount 1
        Position closedPosition = record.getPositions().get(0);
        assertEquals(three, closedPosition.getEntry().getAmount());
        assertEquals(one, closedPosition.getExit().getAmount());

        // Open position should have remaining amount 2
        Position remainingPosition = record.getOpenPositions().get(0);
        assertEquals(two, remainingPosition.getEntry().getAmount());
        assertTrue(remainingPosition.isOpened());
    }

    @Test
    void partialExitMaintainsCorrectEntryIndex() {
        MultiTradingRecord record = new MultiTradingRecord();

        // Enter at index 0
        assertTrue(record.enter(0, one, three));

        // Partial exit at index 2
        assertTrue(record.exit(2, two, one));

        // Remaining position should maintain original entry index
        Position remainingPosition = record.getOpenPositions().get(0);
        assertEquals(0, remainingPosition.getEntry().getIndex());
        assertEquals(two, remainingPosition.getEntry().getAmount());
    }

    @Test
    void fullExitRemovesPositionCompletely() {
        MultiTradingRecord record = new MultiTradingRecord();

        // Enter with amount 2
        assertTrue(record.enter(0, one, two));
        assertEquals(1, record.getOpenPositions().size());

        // Full exit with same amount
        assertTrue(record.exit(1, two, two));

        // Should have one closed position and no open positions
        assertEquals(1, record.getPositions().size());
        assertEquals(0, record.getOpenPositions().size());
        assertTrue(record.isClosed());

        // Closed position should have matching entry and exit amounts
        Position closedPosition = record.getPositions().get(0);
        assertEquals(two, closedPosition.getEntry().getAmount());
        assertEquals(two, closedPosition.getExit().getAmount());
    }

    @Test
    void multiplePartialExitsWorkCorrectly() {
        MultiTradingRecord record = new MultiTradingRecord();

        // Enter with amount 10
        Num ten = one.getNumFactory().numOf(10);
        Num seven = one.getNumFactory().numOf(7);
        Num five = one.getNumFactory().numOf(5);

        assertTrue(record.enter(0, one, ten));

        // First partial exit with amount 3
        assertTrue(record.exit(1, two, three));
        assertEquals(1, record.getPositions().size());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(seven, record.getOpenPositions().get(0).getEntry().getAmount());

        // Second partial exit with amount 2
        assertTrue(record.exit(2, three, two));
        assertEquals(2, record.getPositions().size());
        assertEquals(1, record.getOpenPositions().size());
        assertEquals(five, record.getOpenPositions().get(0).getEntry().getAmount());

        // Final exit with remaining amount 5
        assertTrue(record.exit(3, one, five));
        assertEquals(3, record.getPositions().size());
        assertEquals(0, record.getOpenPositions().size());
        assertTrue(record.isClosed());
    }
}
