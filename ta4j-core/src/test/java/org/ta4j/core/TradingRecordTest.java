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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.ta4j.core.num.NaN.NaN;

public class TradingRecordTest {

    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new BaseTradingRecord();
        openedRecord = new BaseTradingRecord(Pos.buyAt(0, NaN, NaN), Pos.sellAt(3, NaN, NaN), Pos.buyAt(7, NaN, NaN));
        closedRecord = new BaseTradingRecord(Pos.buyAt(0, NaN, NaN), Pos.sellAt(3, NaN, NaN), Pos.buyAt(7, NaN, NaN),
                Pos.sellAt(8, NaN, NaN));
    }

    @Test
    public void getCurrentPair() {
        assertTrue(emptyRecord.getCurrentPair().isNew());
        assertTrue(openedRecord.getCurrentPair().isOpened());
        assertTrue(closedRecord.getCurrentPair().isNew());
    }

    @Test
    public void operate() {
        TradingRecord record = new BaseTradingRecord();

        record.operate(1);
        assertTrue(record.getCurrentPair().isOpened());
        assertEquals(0, record.getPairsCount());
        assertNull(record.getLastPair());
        assertEquals(Pos.buyAt(1, NaN, NaN), record.getLastPosition());
        assertEquals(Pos.buyAt(1, NaN, NaN), record.getLastPosition(Pos.PosType.BUY));
        assertNull(record.getLastPosition(Pos.PosType.SELL));
        assertEquals(Pos.buyAt(1, NaN, NaN), record.getLastEntry());
        assertNull(record.getLastExit());

        record.operate(3);
        assertTrue(record.getCurrentPair().isNew());
        assertEquals(1, record.getPairsCount());
        assertEquals(new PosPair(Pos.buyAt(1, NaN, NaN), Pos.sellAt(3, NaN, NaN)), record.getLastPair());
        assertEquals(Pos.sellAt(3, NaN, NaN), record.getLastPosition());
        assertEquals(Pos.buyAt(1, NaN, NaN), record.getLastPosition(Pos.PosType.BUY));
        assertEquals(Pos.sellAt(3, NaN, NaN), record.getLastPosition(Pos.PosType.SELL));
        assertEquals(Pos.buyAt(1, NaN, NaN), record.getLastEntry());
        assertEquals(Pos.sellAt(3, NaN, NaN), record.getLastExit());

        record.operate(5);
        assertTrue(record.getCurrentPair().isOpened());
        assertEquals(1, record.getPairsCount());
        assertEquals(new PosPair(Pos.buyAt(1, NaN, NaN), Pos.sellAt(3, NaN, NaN)), record.getLastPair());
        assertEquals(Pos.buyAt(5, NaN, NaN), record.getLastPosition());
        assertEquals(Pos.buyAt(5, NaN, NaN), record.getLastPosition(Pos.PosType.BUY));
        assertEquals(Pos.sellAt(3, NaN, NaN), record.getLastPosition(Pos.PosType.SELL));
        assertEquals(Pos.buyAt(5, NaN, NaN), record.getLastEntry());
        assertEquals(Pos.sellAt(3, NaN, NaN), record.getLastExit());
    }

    @Test
    public void isClosed() {
        assertTrue(emptyRecord.isClosed());
        assertFalse(openedRecord.isClosed());
        assertTrue(closedRecord.isClosed());
    }

    @Test
    public void getPairsCount() {
        assertEquals(0, emptyRecord.getPairsCount());
        assertEquals(1, openedRecord.getPairsCount());
        assertEquals(2, closedRecord.getPairsCount());
    }

    @Test
    public void getLastPair() {
        assertNull(emptyRecord.getLastPair());
        assertEquals(new PosPair(Pos.buyAt(0, NaN, NaN), Pos.sellAt(3, NaN, NaN)), openedRecord.getLastPair());
        assertEquals(new PosPair(Pos.buyAt(7, NaN, NaN), Pos.sellAt(8, NaN, NaN)), closedRecord.getLastPair());
    }

    @Test
    public void getLastPosition() {
        // Last position
        assertNull(emptyRecord.getLastPosition());
        assertEquals(Pos.buyAt(7, NaN, NaN), openedRecord.getLastPosition());
        assertEquals(Pos.sellAt(8, NaN, NaN), closedRecord.getLastPosition());
        // Last BUY position
        assertNull(emptyRecord.getLastPosition(Pos.PosType.BUY));
        assertEquals(Pos.buyAt(7, NaN, NaN), openedRecord.getLastPosition(Pos.PosType.BUY));
        assertEquals(Pos.buyAt(7, NaN, NaN), closedRecord.getLastPosition(Pos.PosType.BUY));
        // Last SELL position
        assertNull(emptyRecord.getLastPosition(Pos.PosType.SELL));
        assertEquals(Pos.sellAt(3, NaN, NaN), openedRecord.getLastPosition(Pos.PosType.SELL));
        assertEquals(Pos.sellAt(8, NaN, NaN), closedRecord.getLastPosition(Pos.PosType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry position
        assertNull(emptyRecord.getLastEntry());
        assertEquals(Pos.buyAt(7, NaN, NaN), openedRecord.getLastEntry());
        assertEquals(Pos.buyAt(7, NaN, NaN), closedRecord.getLastEntry());
        // Last exit position
        assertNull(emptyRecord.getLastExit());
        assertEquals(Pos.sellAt(3, NaN, NaN), openedRecord.getLastExit());
        assertEquals(Pos.sellAt(8, NaN, NaN), closedRecord.getLastExit());
    }
}
