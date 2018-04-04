/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.ta4j.core.num.NaN.NaN;

public class ContextTradingRecordTest {

    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new ContextTradingRecord();
        openedRecord = new ContextTradingRecord(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN),
                Order.buyAt(7, NaN, NaN));
        closedRecord = new ContextTradingRecord(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN),
                Order.buyAt(7, NaN, NaN), Order.sellAt(8, NaN, NaN));
    }

    @Test
    public void outOfOrder() {
        ContextTradingRecord firstRecord = new ContextTradingRecord();
        firstRecord.enter(0);
        firstRecord.enter(3);
        firstRecord.enter(7);
        firstRecord.exit(8);
        ContextTradingRecord secondRecord = new ContextTradingRecord();
        secondRecord.enter(7);
        secondRecord.exit(8);
        //assertRecordNotEquals(firstRecord, secondRecord);
        assertNotEquals(firstRecord.getCurrentTrade(1), secondRecord.getCurrentTrade(1));
        assertEquals(firstRecord.getCurrentTrade(7), secondRecord.getCurrentTrade(7));
        secondRecord.enter(0);
        secondRecord.enter(3);
        //assertRecordEquals(firstRecord, secondRecord);
        assertEquals(firstRecord.getCurrentTrade(1), secondRecord.getCurrentTrade(1));
        assertEquals(firstRecord.getCurrentTrade(7), secondRecord.getCurrentTrade(7));
    }

    @Test
    public void getCurrentContextTrade() {
        assertNull(((ContextTradingRecord) emptyRecord).getCurrentTrade(1));
        assertEquals(new Trade(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN)),
                ((ContextTradingRecord) openedRecord).getCurrentTrade(1));
        assertEquals(new Trade(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN)),
                ((ContextTradingRecord) closedRecord).getCurrentTrade(1));

        assertNull(((ContextTradingRecord) openedRecord).getCurrentTrade(5));
        assertNull(((ContextTradingRecord) closedRecord).getCurrentTrade(5));

        assertTrue(((ContextTradingRecord) openedRecord).getCurrentTrade(7).isOpened());
        assertEquals(Order.buyAt(7, NaN, NaN),
                ((ContextTradingRecord) openedRecord).getCurrentTrade(8).getEntry());

        assertFalse(((ContextTradingRecord) closedRecord).getCurrentTrade(7).isOpened());
        assertEquals(new Trade(Order.buyAt(7, NaN, NaN), Order.sellAt(8, NaN, NaN)),
                ((ContextTradingRecord) closedRecord).getCurrentTrade(8));
    }

    @Test
    public void getCurrentTrade() {
        assertNull(emptyRecord.getCurrentTrade());
        assertTrue(openedRecord.getCurrentTrade().isOpened());
        assertTrue(closedRecord.getCurrentTrade().isClosed());
    }

    @Test
    public void operate() {
        TradingRecord record = new ContextTradingRecord();

        record.operate(1);
        assertTrue(record.getCurrentTrade().isOpened());
        assertEquals(0, record.getTradeCount());
        assertNull(record.getLastTrade());
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastOrder());
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastOrder(Order.OrderType.BUY));
        assertNull(record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastEntry());
        assertNull(record.getLastExit());

        record.operate(3);
        assertFalse(record.getCurrentTrade().isOpened());
        assertEquals(1, record.getTradeCount());
        assertEquals(new Trade(Order.buyAt(1, NaN, NaN), Order.sellAt(3, NaN, NaN)), record.getLastTrade());
        assertEquals(Order.sellAt(3, NaN, NaN), record.getLastOrder());
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.sellAt(3, NaN, NaN), record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastEntry());
        assertEquals(Order.sellAt(3, NaN, NaN), record.getLastExit());

        record.operate(5);
        assertTrue(record.getCurrentTrade().isOpened());
        assertEquals(1, record.getTradeCount());
        assertEquals(new Trade(Order.buyAt(1, NaN, NaN), Order.sellAt(3, NaN, NaN)), record.getLastTrade());
        assertEquals(Order.buyAt(5, NaN, NaN), record.getLastOrder());
        assertEquals(Order.buyAt(5, NaN, NaN), record.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.sellAt(3, NaN, NaN), record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(5, NaN, NaN), record.getLastEntry());
        assertEquals(Order.sellAt(3, NaN, NaN), record.getLastExit());
    }

    @Test
    public void isClosed() {
        assertTrue(emptyRecord.isClosed());
        assertFalse(openedRecord.isClosed());
        assertTrue(closedRecord.isClosed());
    }

    @Test
    public void getTradeCount() {
        assertEquals(0, emptyRecord.getTradeCount());
        assertEquals(1, openedRecord.getTradeCount());
        assertEquals(2, closedRecord.getTradeCount());
    }

    @Test
    public void getLastTrade() {
        assertNull(emptyRecord.getLastTrade());
        assertEquals(new Trade(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN)), openedRecord.getLastTrade());
        assertEquals(new Trade(Order.buyAt(7, NaN, NaN), Order.sellAt(8, NaN, NaN)), closedRecord.getLastTrade());
    }

    @Test
    public void getLastOrder() {
        // Last order
        assertNull(emptyRecord.getLastOrder());
        assertEquals(Order.buyAt(7, NaN, NaN), openedRecord.getLastOrder());
        assertEquals(Order.sellAt(8, NaN, NaN), closedRecord.getLastOrder());
        // Last BUY order
        assertNull(emptyRecord.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.buyAt(7, NaN, NaN), openedRecord.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.buyAt(7, NaN, NaN), closedRecord.getLastOrder(Order.OrderType.BUY));
        // Last SELL order
        assertNull(emptyRecord.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.sellAt(3, NaN, NaN), openedRecord.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.sellAt(8, NaN, NaN), closedRecord.getLastOrder(Order.OrderType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry
        assertNull(emptyRecord.getLastEntry());
        assertEquals(Order.buyAt(7, NaN, NaN), openedRecord.getLastEntry());
        assertEquals(Order.buyAt(7, NaN, NaN), closedRecord.getLastEntry());
        // Last exit
        assertNull(emptyRecord.getLastExit());
        assertEquals(Order.sellAt(3, NaN, NaN), openedRecord.getLastExit());
        assertEquals(Order.sellAt(8, NaN, NaN), closedRecord.getLastExit());
    }
}
