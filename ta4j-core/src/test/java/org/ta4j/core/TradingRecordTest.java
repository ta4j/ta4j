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
        openedRecord = new BaseTradingRecord(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN),
                Order.buyAt(7, NaN, NaN));
        closedRecord = new BaseTradingRecord(Order.buyAt(0, NaN, NaN), Order.sellAt(3, NaN, NaN),
                Order.buyAt(7, NaN, NaN), Order.sellAt(8, NaN, NaN));
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
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastOrder());
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastOrder(Order.OrderType.BUY));
        assertNull(record.getLastOrder(Order.OrderType.SELL));
        assertEquals(Order.buyAt(1, NaN, NaN), record.getLastEntry());
        assertNull(record.getLastExit());
        
        record.operate(3);
        assertTrue(record.getCurrentTrade().isNew());
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
