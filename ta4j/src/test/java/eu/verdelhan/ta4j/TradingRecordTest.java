/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TradingRecordTest {

    private TradingRecord emptyRecord, openedRecord, closedRecord;

    @Before
    public void setUp() {
        emptyRecord = new BaseTradingRecord();
        openedRecord = new BaseTradingRecord(Order.buyAt(0), Order.sellAt(3),
                Order.buyAt(7));
        closedRecord = new BaseTradingRecord(Order.buyAt(0), Order.sellAt(3),
                Order.buyAt(7), Order.sellAt(8));
    }

    @Test
    public void getCurrentTrade() {
        assertTrue(emptyRecord.getCurrentTrade().isNew());
        assertTrue(openedRecord.getCurrentTrade().isOpened());
        assertTrue(closedRecord.getCurrentTrade().isNew());
    }
    
    @Test
    public void operate() {
        TradingRecord record = new BaseTradingRecord();
        
        record.operate(1);
        assertTrue(record.getCurrentTrade().isOpened());
        assertEquals(0, record.getTradeCount());
        assertNull(record.getLastTrade());
        assertEquals(Order.buyAt(1), record.getLastOrder());
        assertEquals(Order.buyAt(1), record.getLastOrder(Order.OrderType.BUY));
        assertNull(record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(1), record.getLastEntry());
        assertNull(record.getLastExit());
        
        record.operate(3);
        assertTrue(record.getCurrentTrade().isNew());
        assertEquals(1, record.getTradeCount());
        assertEquals(new Trade(Order.buyAt(1), Order.sellAt(3)), record.getLastTrade());
        assertEquals(Order.sellAt(3), record.getLastOrder());
        assertEquals(Order.buyAt(1), record.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.sellAt(3), record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(1), record.getLastEntry());
        assertEquals(Order.sellAt(3), record.getLastExit());
        
        record.operate(5);
        assertTrue(record.getCurrentTrade().isOpened());
        assertEquals(1, record.getTradeCount());
        assertEquals(new Trade(Order.buyAt(1), Order.sellAt(3)), record.getLastTrade());
        assertEquals(Order.buyAt(5), record.getLastOrder());
        assertEquals(Order.buyAt(5), record.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.sellAt(3), record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(5), record.getLastEntry());
        assertEquals(Order.sellAt(3), record.getLastExit());
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
        assertEquals(new Trade(Order.buyAt(0), Order.sellAt(3)), openedRecord.getLastTrade());
        assertEquals(new Trade(Order.buyAt(7), Order.sellAt(8)), closedRecord.getLastTrade());
    }

    @Test
    public void getLastOrder() {
        // Last order
        assertNull(emptyRecord.getLastOrder());
        assertEquals(Order.buyAt(7), openedRecord.getLastOrder());
        assertEquals(Order.sellAt(8), closedRecord.getLastOrder());
        // Last BUY order
        assertNull(emptyRecord.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.buyAt(7), openedRecord.getLastOrder(Order.OrderType.BUY));
        assertEquals(Order.buyAt(7), closedRecord.getLastOrder(Order.OrderType.BUY));
        // Last SELL order
        assertNull(emptyRecord.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.sellAt(3), openedRecord.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.sellAt(8), closedRecord.getLastOrder(Order.OrderType.SELL));
    }

    @Test
    public void getLastEntryExit() {
        // Last entry
        assertNull(emptyRecord.getLastEntry());
        assertEquals(Order.buyAt(7), openedRecord.getLastEntry());
        assertEquals(Order.buyAt(7), closedRecord.getLastEntry());
        // Last exit
        assertNull(emptyRecord.getLastExit());
        assertEquals(Order.sellAt(3), openedRecord.getLastExit());
        assertEquals(Order.sellAt(8), closedRecord.getLastExit());
    }
}
