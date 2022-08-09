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
import java.io.Serializable
import java.util.*

/**
 * Pair of two [trades][Trade].
 *
 * The exit trade has the complement type of the entry trade.<br></br>
 * I.e.: entry == BUY --> exit == SELL entry == SELL --> exit == BUY
 */
class Position : Serializable {
    /**
     * @return the entry [trade][Trade] of the position
     */
    /** The entry trade  */
    var entry: Trade? = null
        private set
    /**
     * @return the exit [trade][Trade] of the position
     */
    /** The exit trade  */
    var exit: Trade? = null
        private set
    /**
     * @return the [.startingType]
     */
    /** The type of the entry trade  */
    val startingType: TradeType

    /** The cost model for transactions of the asset  */
    private val transactionCostModel: CostModel?

    /** The cost model for holding the asset  */
    private val holdingCostModel: CostModel?
    /**
     * Constructor.
     *
     * @param startingType         the starting [trade type][TradeType] of the position (i.e. type of the entry trade)
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */

    @JvmOverloads
    constructor(
        startingType: TradeType? = TradeType.BUY,
        transactionCostModel: CostModel? = ZeroCostModel(),
        holdingCostModel: CostModel? = ZeroCostModel()
    ) {
        require(startingType!=null)
        this.startingType = startingType
        this.transactionCostModel = transactionCostModel
        this.holdingCostModel = holdingCostModel
    }
    /**
     * Constructor.
     *
     * @param entry                the entry [trade][Trade]
     * @param exit                 the exit [trade][Trade]
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    @JvmOverloads
    constructor(
        entry: Trade,
        exit: Trade,
        transactionCostModel: CostModel? = entry.costModel,
        holdingCostModel: CostModel = ZeroCostModel()
    ) {
        require(entry.type != exit.type) { "Both trades must have different types" }
        require(
            !(!entry.costModel!!.equals(transactionCostModel)
                    || !exit.costModel!!.equals(transactionCostModel))
        ) { "Trades and the position must incorporate the same trading cost model" }
        startingType = entry.type
        this.entry = entry
        this.exit = exit
        this.transactionCostModel = transactionCostModel
        this.holdingCostModel = holdingCostModel
    }

    override fun equals(other: Any?): Boolean {
        if (other is Position) {
            val p = other
            return ((if (entry == null) p.entry == null else entry == p.entry)
                    && if (exit == null) p.exit == null else exit == p.exit)
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(entry, exit)
    }
    /**
     * Operates the position at the index-th position
     *
     * @param index  the bar index
     * @param price  the price
     * @param amount the amount
     * @return the trade
     */
    @JvmOverloads
    fun operate(index: Int, price: Num? = NaN.NaN, amount: Num = NaN.NaN): Trade? {
        var trade: Trade? = null
        if (isNew) {
            trade = Trade(index, startingType, price, amount, transactionCostModel)
            entry = trade
        } else if (isOpened) {
            check(index >= entry!!.index) { "The index i is less than the entryTrade index" }
            trade = Trade(index, startingType.complementType(), price, amount, transactionCostModel)
            exit = trade
        }
        return trade
    }

    /**
     * @return true if the position is closed, false otherwise
     */
    val isClosed: Boolean
        get() = entry != null && exit != null

    /**
     * @return true if the position is opened, false otherwise
     */
    val isOpened: Boolean
        get() = entry != null && exit == null

    /**
     * @return true if the position is new, false otherwise
     */
    val isNew: Boolean
        get() = entry == null && exit == null

    /**
     * @return true if position is closed and [.getProfit] > 0
     */
    fun hasProfit(): Boolean {
        return profit!!.isPositive
    }

    /**
     * @return true if position is closed and [.getProfit] < 0
     */
    fun hasLoss(): Boolean {
        return profit!!.isNegative
    }

    /**
     * Calculates the net profit of the position if it is closed.
     *
     * @return the profit or loss of the position
     */
    val profit: Num?
        get() = if (isOpened) {
            numOf(0)
        } else {
            getGrossProfit(exit!!.pricePerAsset).minus(positionCost!!)
        }

    /**
     * Calculates the net profit of the position. If it is open, calculates the
     * profit until the final bar.
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     * open)
     * @param finalPrice the price of the final bar to be considered (if position is
     * open)
     * @return the profit or loss of the position
     */
    fun getProfit(finalIndex: Int, finalPrice: Num?): Num {
        val grossProfit = getGrossProfit(finalPrice)
        val tradingCost = getPositionCost(finalIndex)
        return grossProfit.minus(tradingCost)
    }

    /**
     * Calculate the gross profit of the position if it is closed
     *
     * @return the gross profit of the position
     */
    val grossProfit: Num?
        get() = if (isOpened) {
            numOf(0)
        } else {
            getGrossProfit(exit!!.pricePerAsset)
        }

    /**
     * Calculate the gross profit of the position.
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     * open)
     * @return the profit or loss of the position
     */
    fun getGrossProfit(finalPrice: Num?): Num {
        var grossProfit: Num
        grossProfit = if (isOpened) {
            entry!!.amount.times(finalPrice!!).minus(entry!!.value!!)
        } else {
            exit!!.value!!.minus(entry!!.value!!)
        }

        // Profits of long position are losses of short
        if (entry!!.isSell) {
            grossProfit = grossProfit.negate()
        }
        return grossProfit
    }

    /**
     * Calculates the gross return of the position if it is closed.
     *
     * @return the gross return of the position in percent
     */
    val grossReturn: Num?
        get() = if (isOpened) {
            numOf(0)
        } else {
            getGrossReturn(exit!!.pricePerAsset)
        }

    /**
     * Calculates the gross return of the position, if it exited at the provided
     * price.
     *
     * @param finalPrice the price of the final bar to be considered (if position is
     * open)
     * @return the gross return of the position in percent
     */
    fun getGrossReturn(finalPrice: Num?): Num{
        return getGrossReturn(entry!!.pricePerAsset!!, finalPrice!!)
    }

    /**
     * Calculates the gross return of the position. If either the entry or the exit
     * price are `NaN`, the close price from the supplied
     * [BarSeries] is used.
     *
     * @param barSeries
     * @return the gross return in percent with entry and exit prices from the
     * barSeries
     */
    fun getGrossReturn(barSeries: BarSeries?): Num {
        val entryPrice = entry!!.getPricePerAsset(barSeries)
        val exitPrice = exit!!.getPricePerAsset(barSeries)
        return getGrossReturn(entryPrice!!, exitPrice!!)
    }

    /**
     * Calculates the gross return between entry and exit price in percent. Includes
     * the base.
     *
     *
     *
     * For example:
     *
     *  * For buy position with a profit of 4%, it returns 1.04 (includes the base)
     *  * For sell position with a loss of 4%, it returns 0.96 (includes the base)
     *
     *
     * @param entryPrice the entry price
     * @param exitPrice  the exit price
     * @return the gross return in percent between entryPrice and exitPrice
     * (includes the base)
     */
    fun getGrossReturn(entryPrice: Num, exitPrice: Num): Num {
        return if (entry!!.isBuy) {
            exitPrice.div(entryPrice)
        } else {
            val one = entryPrice.numOf(1)
            exitPrice.div(entryPrice).minus(one).negate().plus(one)
        }
    }

    /**
     * Calculates the total cost of the position
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     * open)
     * @return the cost of the position
     */
    fun getPositionCost(finalIndex: Int): Num {
        val transactionCost = transactionCostModel!!.calculate(this, finalIndex)
        val borrowingCost = getHoldingCost(finalIndex)
        return transactionCost!!.plus(borrowingCost)
    }

    /**
     * Calculates the total cost of the closed position
     *
     * @return the cost of the position
     */
    val positionCost: Num?
        get() {
            val transactionCost = transactionCostModel!!.calculate(this)
            val borrowingCost = holdingCost
            return transactionCost!!.plus(borrowingCost!!)
        }

    /**
     * Calculates the holding cost of the closed position
     *
     * @return the cost of the position
     */
    val holdingCost: Num?
        get() = holdingCostModel?.calculate(this)

    /**
     * Calculates the holding cost of the position
     *
     * @param finalIndex the index of the final bar to be considered (if position is
     * open)
     * @return the cost of the position
     */
    fun getHoldingCost(finalIndex: Int): Num {
        return holdingCostModel?.calculate(this, finalIndex)!!
    }

    /**
     * @param num the Number to be converted to a Num
     * @return the Num of num
     */
    private fun numOf(num: Number): Num? {
        return entry!!.netPrice!!.numOf(num)
    }

    override fun toString(): String {
        return "Entry: $entry exit: $exit"
    }

    companion object {
        private const val serialVersionUID = -5484709075767220358L
    }
}
