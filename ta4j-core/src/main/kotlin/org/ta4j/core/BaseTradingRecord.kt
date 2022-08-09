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

import org.ta4j.core.Trade.TradeType
import org.ta4j.core.analysis.cost.CostModel
import org.ta4j.core.analysis.cost.ZeroCostModel
import org.ta4j.core.num.*

/**
 * Base implementation of a [TradingRecord].
 *
 */
open class BaseTradingRecord(
    entryTradeType: TradeType?,
    transactionCostModel: CostModel?,
    holdingCostModel: CostModel
) : TradingRecord {
    /**
     * The name of the trading record
     */
    override var name: String? = null

    /**
     * The recorded trades
     */
    private val trades: MutableList<Trade> = ArrayList()

    /**
     * The recorded BUY trades
     */
    private val buyTrades: MutableList<Trade> = ArrayList()

    /**
     * The recorded SELL trades
     */
    private val sellTrades: MutableList<Trade> = ArrayList()

    /**
     * The recorded entry trades
     */
    private val entryTrades: MutableList<Trade> = ArrayList()

    /**
     * The recorded exit trades
     */
    private val exitTrades: MutableList<Trade> = ArrayList()

    /**
     * The entry type (BUY or SELL) in the trading session
     */
    override val startingType: TradeType

    /**
     * The recorded positions
     */

    override val positions: MutableList<Position> = ArrayList()

    /**
     * The current non-closed position (there's always one)
     */
    private var currentPosition: Position

    /**
     * Trading cost models
     */
    private val transactionCostModel: CostModel?
    private val holdingCostModel: CostModel

    /**
     * Constructor.
     *
     * @param name the name of the tradingRecord
     */
    constructor(name: String?) : this(TradeType.BUY) {
        this.name = name
    }

    /**
     * Constructor.
     *
     * @param name           the name of the trading record
     * @param entryTradeType the [trade type][TradeType] of entries in the
     * trading session
     */
    constructor(name: String?, tradeType: TradeType?) : this(tradeType, ZeroCostModel(), ZeroCostModel()) {
        this.name = name
    }
    /**
     * Constructor.
     *
     * @param entryTradeType the [trade type][TradeType] of entries in the
     * trading session
     */
    /**
     * Constructor.
     */
    @JvmOverloads
    constructor(tradeType: TradeType? = TradeType.BUY) : this(tradeType, ZeroCostModel(), ZeroCostModel()) {
    }

    /**
     * Constructor.
     *
     * @param entryTradeType       the [trade type][TradeType] of entries in
     * the trading session
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    init {
        requireNotNull(entryTradeType) { "Starting type must not be null" }
        startingType = entryTradeType
        this.transactionCostModel = transactionCostModel
        this.holdingCostModel = holdingCostModel
        currentPosition = Position(entryTradeType, transactionCostModel, holdingCostModel)
    }

    /**
     * Constructor.
     *
     * @param trades the trades to be recorded (cannot be empty)
     */
    constructor(vararg trades: Trade) : this(ZeroCostModel(), ZeroCostModel(), *trades) {}

    /**
     * Constructor.
     *
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     * @param trades               the trades to be recorded (cannot be empty)
     */
    constructor(
        transactionCostModel: CostModel?,
        holdingCostModel: CostModel,
        vararg trades: Trade
    ) : this(trades[0].type, transactionCostModel, holdingCostModel) {
        for (o in trades) {
            val newTradeWillBeAnEntry = currentPosition.isNew
            if (newTradeWillBeAnEntry && o.type !== startingType) {
                // Special case for entry/exit types reversal
                // E.g.: BUY, SELL,
                // BUY, SELL,
                // SELL, BUY,
                // BUY, SELL
                currentPosition = Position(o.type, transactionCostModel, holdingCostModel)
            }
            val newTrade = currentPosition.operate(o.index, o.pricePerAsset, o.amount)
            recordTrade(newTrade, newTradeWillBeAnEntry)
        }
    }



    override fun getCurrentPosition(): Position {
        return currentPosition
    }

    override fun operate(index: Int, price: Num?, amount: Num?) {
        check(!currentPosition.isClosed) {
            // Current position closed, should not occur
            "Current position should not be closed"
        }
        val newTradeWillBeAnEntry = currentPosition.isNew
        val newTrade = currentPosition.operate(index, price, amount!!)
        recordTrade(newTrade, newTradeWillBeAnEntry)
    }

    override fun enter(index: Int, price: Num?, amount: Num?): Boolean {
        if (currentPosition.isNew) {
            operate(index, price, amount)
            return true
        }
        return false
    }

    override fun exit(index: Int, price: Num?, amount: Num?): Boolean {
        if (currentPosition.isOpened) {
            operate(index, price, amount)
            return true
        }
        return false
    }

    override fun getLastTrade(): Trade? {
        return if (!trades.isEmpty()) {
            trades[trades.size - 1]
        } else null
    }

    override fun getLastTrade(tradeType: TradeType): Trade? {
        if (TradeType.BUY === tradeType && !buyTrades.isEmpty()) {
            return buyTrades[buyTrades.size - 1]
        } else if (TradeType.SELL === tradeType && !sellTrades.isEmpty()) {
            return sellTrades[sellTrades.size - 1]
        }
        return null
    }

    override fun getLastEntry(): Trade? {
        return if (!entryTrades.isEmpty()) {
            entryTrades[entryTrades.size - 1]
        } else null
    }

    override fun getLastExit(): Trade? {
        return if (!exitTrades.isEmpty()) {
            exitTrades[exitTrades.size - 1]
        } else null
    }

    /**
     * Records a trade and the corresponding position (if closed).
     *
     * @param trade   the trade to be recorded
     * @param isEntry true if the trade is an entry, false otherwise (exit)
     */
    private fun recordTrade(trade: Trade?, isEntry: Boolean) {
        requireNotNull(trade) { "Trade should not be null" }

        // Storing the new trade in entries/exits lists
        if (isEntry) {
            entryTrades.add(trade)
        } else {
            exitTrades.add(trade)
        }

        // Storing the new trade in trades list
        trades.add(trade)
        if (TradeType.BUY === trade.type) {
            // Storing the new trade in buy trades list
            buyTrades.add(trade)
        } else if (TradeType.SELL === trade.type) {
            // Storing the new trade in sell trades list
            sellTrades.add(trade)
        }

        // Storing the position if closed
        if (currentPosition.isClosed) {
            positions.add(currentPosition)
            currentPosition = Position(startingType, transactionCostModel, holdingCostModel)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("BaseTradingRecord: " + if (name == null) "" else name)
        sb.append(System.lineSeparator())
        for (trade in trades) {
            sb.append(trade.toString()).append(System.lineSeparator())
        }
        return sb.toString()
    }

    companion object {
        private const val serialVersionUID = -4436851731855891220L
    }
}