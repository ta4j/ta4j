/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.ta4j.core.Trade.Companion.buyAt
import org.ta4j.core.Trade.Companion.sellAt
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.NaN

class TradingRecordTest {
    private var emptyRecord: TradingRecord? = null
    private var openedRecord: TradingRecord? = null
    private var closedRecord: TradingRecord? = null
    @Before
    fun setUp() {
        emptyRecord = BaseTradingRecord()
        openedRecord = BaseTradingRecord(
            buyAt(0, NaN.NaN, NaN.NaN), sellAt(3, NaN.NaN, NaN.NaN),
            buyAt(7, NaN.NaN, NaN.NaN)
        )
        closedRecord = BaseTradingRecord(
            buyAt(0, NaN.NaN, NaN.NaN), sellAt(3, NaN.NaN, NaN.NaN),
            buyAt(7, NaN.NaN, NaN.NaN), sellAt(8, NaN.NaN, NaN.NaN)
        )
    }

    @Test
    fun getCurrentPosition() {
        Assert.assertTrue(emptyRecord!!.getCurrentPosition().isNew)
        Assert.assertTrue(openedRecord!!.getCurrentPosition().isOpened)
        Assert.assertTrue(closedRecord!!.getCurrentPosition().isNew)
    }

    @Test
    fun operate() {
        val record: TradingRecord = BaseTradingRecord()
        record.operate(1)
        Assert.assertTrue(record.getCurrentPosition().isOpened)
        Assert.assertEquals(0, record.getPositionCount().toLong())
        Assert.assertNull(record.getLastPosition())
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), record.getLastTrade())
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), record.getLastTrade(TradeType.BUY))
        Assert.assertNull(record.getLastTrade(TradeType.SELL))
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), record.getLastEntry())
        Assert.assertNull(record.getLastExit())
        record.operate(3)
        Assert.assertTrue(record.getCurrentPosition().isNew)
        Assert.assertEquals(1, record.getPositionCount().toLong())
        Assert.assertEquals(Position(buyAt(1, NaN.NaN, NaN.NaN), sellAt(3, NaN.NaN, NaN.NaN)), record.getLastPosition())
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), record.getLastTrade())
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), record.getLastTrade(TradeType.BUY))
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), record.getLastTrade(TradeType.SELL))
        Assert.assertEquals(buyAt(1, NaN.NaN, NaN.NaN), record.getLastEntry())
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), record.getLastExit())
        record.operate(5)
        Assert.assertTrue(record.getCurrentPosition().isOpened)
        Assert.assertEquals(1, record.getPositionCount().toLong())
        Assert.assertEquals(Position(buyAt(1, NaN.NaN, NaN.NaN), sellAt(3, NaN.NaN, NaN.NaN)), record.getLastPosition())
        Assert.assertEquals(buyAt(5, NaN.NaN, NaN.NaN), record.getLastTrade())
        Assert.assertEquals(buyAt(5, NaN.NaN, NaN.NaN), record.getLastTrade(TradeType.BUY))
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), record.getLastTrade(TradeType.SELL))
        Assert.assertEquals(buyAt(5, NaN.NaN, NaN.NaN), record.getLastEntry())
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), record.getLastExit())
    }

    @Test
    fun isClosed() {
        Assert.assertTrue(emptyRecord!!.isClosed)
        Assert.assertFalse(openedRecord!!.isClosed)
        Assert.assertTrue(closedRecord!!.isClosed)
    }

    @Test
    fun getPositionCount() {
        Assert.assertEquals(0, emptyRecord!!.getPositionCount().toLong())
        Assert.assertEquals(1, openedRecord!!.getPositionCount().toLong())
        Assert.assertEquals(2, closedRecord!!.getPositionCount().toLong())
    }

    @Test
    fun getLastPosition() {
        Assert.assertNull(emptyRecord!!.getLastPosition())
        Assert.assertEquals(
            Position(buyAt(0, NaN.NaN, NaN.NaN), sellAt(3, NaN.NaN, NaN.NaN)),
            openedRecord!!.getLastPosition()
        )
        Assert.assertEquals(
            Position(buyAt(7, NaN.NaN, NaN.NaN), sellAt(8, NaN.NaN, NaN.NaN)),
            closedRecord!!.getLastPosition()
        )
    }

    @Test
    fun getLastTrade() {
        // Last trade
        Assert.assertNull(emptyRecord!!.getLastTrade())
        Assert.assertEquals(buyAt(7, NaN.NaN, NaN.NaN), openedRecord!!.getLastTrade())
        Assert.assertEquals(sellAt(8, NaN.NaN, NaN.NaN), closedRecord!!.getLastTrade())
        // Last BUY trade
        Assert.assertNull(emptyRecord!!.getLastTrade(TradeType.BUY))
        Assert.assertEquals(buyAt(7, NaN.NaN, NaN.NaN), openedRecord!!.getLastTrade(TradeType.BUY))
        Assert.assertEquals(buyAt(7, NaN.NaN, NaN.NaN), closedRecord!!.getLastTrade(TradeType.BUY))
        // Last SELL trade
        Assert.assertNull(emptyRecord!!.getLastTrade(TradeType.SELL))
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), openedRecord!!.getLastTrade(TradeType.SELL))
        Assert.assertEquals(sellAt(8, NaN.NaN, NaN.NaN), closedRecord!!.getLastTrade(TradeType.SELL))
    }

    @Test
    fun getLastEntryExit() {
        // Last entry
        Assert.assertNull(emptyRecord!!.getLastEntry())
        Assert.assertEquals(buyAt(7, NaN.NaN, NaN.NaN), openedRecord!!.getLastEntry())
        Assert.assertEquals(buyAt(7, NaN.NaN, NaN.NaN), closedRecord!!.getLastEntry())
        // Last exit
        Assert.assertNull(emptyRecord!!.getLastExit())
        Assert.assertEquals(sellAt(3, NaN.NaN, NaN.NaN), openedRecord!!.getLastExit())
        Assert.assertEquals(sellAt(8, NaN.NaN, NaN.NaN), closedRecord!!.getLastExit())
    }
}