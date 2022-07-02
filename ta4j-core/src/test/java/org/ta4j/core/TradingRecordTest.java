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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;

public class TradingRecordTest {

    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new BaseTradingRecord();
        openedRecord = new BaseTradingRecord(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN),
                Trade.buyAt(7, NaN, NaN));
        closedRecord = new BaseTradingRecord(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN),
                Trade.buyAt(7, NaN, NaN), Trade.sellAt(8, NaN, NaN));
    }

    @Test
    public void getCurrentPosition() {
        assertTrue(emptyRecord.getCurrentPosition().isNew());
        assertTrue(openedRecord.getCurrentPosition().isOpened());
        assertTrue(closedRecord.getCurrentPosition().isNew());
    }

    @Test
    public void operate() {
        TradingRecord record = new BaseTradingRecord();

        record.operate(1);
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(0, record.getPositionCount());
        assertNull(record.getLastPosition());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertNull(record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastEntry());
        assertNull(record.getLastExit());

        record.operate(3);
        assertTrue(record.getCurrentPosition().isNew());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(3, NaN, NaN)), record.getLastPosition());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(1, NaN, NaN), record.getLastEntry());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastExit());

        record.operate(5);
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(1, record.getPositionCount());
        assertEquals(new Position(Trade.buyAt(1, NaN, NaN), Trade.sellAt(3, NaN, NaN)), record.getLastPosition());
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastTrade());
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.buyAt(5, NaN, NaN), record.getLastEntry());
        assertEquals(Trade.sellAt(3, NaN, NaN), record.getLastExit());
    }

    @Test
    public void isClosed() {
        assertTrue(emptyRecord.isClosed());
        assertFalse(openedRecord.isClosed());
        assertTrue(closedRecord.isClosed());
    }

    @Test
    public void getPositionCount() {
        assertEquals(0, emptyRecord.getPositionCount());
        assertEquals(1, openedRecord.getPositionCount());
        assertEquals(2, closedRecord.getPositionCount());
    }

    @Test
    public void getLastPosition() {
        assertNull(emptyRecord.getLastPosition());
        assertEquals(new Position(Trade.buyAt(0, NaN, NaN), Trade.sellAt(3, NaN, NaN)), openedRecord.getLastPosition());
        assertEquals(new Position(Trade.buyAt(7, NaN, NaN), Trade.sellAt(8, NaN, NaN)), closedRecord.getLastPosition());
    }

    @Test
    public void getLastTrade() {
        // Last trade
        assertNull(emptyRecord.getLastTrade());
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastTrade());
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastTrade());
        // Last BUY trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastTrade(Trade.TradeType.BUY));
        assertEquals(Trade.buyAt(7, NaN, NaN), closedRecord.getLastTrade(Trade.TradeType.BUY));
        // Last SELL trade
        assertNull(emptyRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.sellAt(3, NaN, NaN), openedRecord.getLastTrade(Trade.TradeType.SELL));
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastTrade(Trade.TradeType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry
        assertNull(emptyRecord.getLastEntry());
        assertEquals(Trade.buyAt(7, NaN, NaN), openedRecord.getLastEntry());
        assertEquals(Trade.buyAt(7, NaN, NaN), closedRecord.getLastEntry());
        // Last exit
        assertNull(emptyRecord.getLastExit());
        assertEquals(Trade.sellAt(3, NaN, NaN), openedRecord.getLastExit());
        assertEquals(Trade.sellAt(8, NaN, NaN), closedRecord.getLastExit());
    }
}
