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
 * A trade.
 *
 * The trade is defined by:
 *
 *  * the index (in the [bar series][BarSeries]) it is executed
 *  * a [type][TradeType] (BUY or SELL)
 *  * a pricePerAsset (optional)
 *  * a trade amount (optional)
 *
 * A [position][Position] is a pair of complementary trades.
 */
class Trade : Serializable {
    /**
     * The type of an [trade][Trade].
     *
     * A BUY corresponds to a *BID* trade. A SELL corresponds to an *ASK*
     * trade.
     */
    enum class TradeType {
        BUY {
            override fun complementType(): TradeType {
                return SELL
            }
        },
        SELL {
            override fun complementType(): TradeType {
                return BUY
            }
        };

        /**
         * @return the complementary trade type
         */
        abstract fun complementType(): TradeType
    }
    /**
     * @return the trade type (BUY or SELL)
     */
    /**
     * Type of the trade
     */
    var type: TradeType
        private set
    /**
     * @return the index the trade is executed
     */
    /**
     * The index the trade was executed
     */
    var index: Int
        private set
    /**
     * @return the trade price per asset
     */
    /**
     * the trade price per asset
     */
    var pricePerAsset: Num? = null
        private set
    /**
     * @return the trade price per asset, net transaction costs
     */
    /**
     * The net price for the trade, net transaction costs
     */
    var netPrice: Num? = null
        private set
    /**
     * @return the trade amount
     */
    /**
     * the trade amount
     */
    var amount: Num
        private set
    /**
     * @return the costs of the trade
     */
    /**
     * The cost for executing the trade
     */
    var cost: Num? = null
        private set
    /**
     * @return the cost model for trade execution
     */
    /**
     * The cost model for trade execution
     */
    var costModel: CostModel? = null
        private set
    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param type                 the trade type
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution cost
     */
    protected constructor(
        index: Int,
        series: BarSeries,
        type: TradeType,
        amount: Num = series.numOf(1),
        transactionCostModel: CostModel? = ZeroCostModel()
    ) {
        this.type = type
        this.index = index
        this.amount = amount
        setPricesAndCost(series.getBar(index).closePrice, amount, transactionCostModel)
    }
    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param type                 the trade type
     * @param pricePerAsset        the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     */
    @JvmOverloads
    constructor(
        index: Int,
        type: TradeType,
        pricePerAsset: Num?,
        amount: Num = pricePerAsset!!.numOf(1),
        transactionCostModel: CostModel? = ZeroCostModel()
    ) {
        this.type = type
        this.index = index
        this.amount = amount
        setPricesAndCost(pricePerAsset, amount, transactionCostModel)
    }

    /**
     * @return the trade price per asset, or, if `NaN`, the close price
     * from the supplied [BarSeries].
     */
    fun getPricePerAsset(barSeries: BarSeries?): Num? {
        return if (pricePerAsset!!.isNaN) {
            barSeries!!.getBar(index).closePrice
        } else pricePerAsset
    }

    /**
     * Sets the raw and net prices of the trade
     *
     * @param pricePerAsset        the raw price of the asset
     * @param amount               the amount of assets ordered
     * @param transactionCostModel the cost model for trade execution
     */
    private fun setPricesAndCost(pricePerAsset: Num?, amount: Num, transactionCostModel: CostModel?) {
        costModel = transactionCostModel
        this.pricePerAsset = pricePerAsset
        cost = transactionCostModel!!.calculate(this.pricePerAsset, amount)
        val costPerAsset = cost!!.div(amount)
        // add transaction costs to the pricePerAsset at the trade
        if (type == TradeType.BUY) {
            netPrice = this.pricePerAsset!!.plus(costPerAsset)
        } else {
            netPrice = this.pricePerAsset!!.minus(costPerAsset)
        }
    }

    /**
     * @return true if this is a BUY trade, false otherwise
     */
    val isBuy: Boolean
        get() = type === TradeType.BUY

    /**
     * @return true if this is a SELL trade, false otherwise
     */
    val isSell: Boolean
        get() = type === TradeType.SELL

    override fun hashCode(): Int {
        return Objects.hash(type, index, pricePerAsset, amount)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as Trade
        return type == other.type && index == other.index && pricePerAsset == other.pricePerAsset && amount == other.amount
    }

    override fun toString(): String {
        return "Trade{type=$type, index=$index, price=$pricePerAsset, amount=$amount}"
    }

    /**
     * @return the value of a trade (without transaction cost)
     */
    val value: Num?
        get() = pricePerAsset!!.times(amount)

    companion object {
        private const val serialVersionUID = -905474949010114150L

        /**
         * @param index  the index the trade is executed
         * @param series the bar series
         * @return a BUY trade
         */
        @JvmStatic
        fun buyAt(index: Int, series: BarSeries): Trade {
            return Trade(index, series, TradeType.BUY)
        }

        /**
         * @param index                the index the trade is executed
         * @param price                the trade price
         * @param amount               the trade amount
         * @param transactionCostModel the cost model for trade execution
         * @return a BUY trade
         */
        @JvmStatic
        fun buyAt(index: Int, price: Num?, amount: Num, transactionCostModel: CostModel?): Trade {
            return Trade(index, TradeType.BUY, price, amount, transactionCostModel)
        }

        /**
         * @param index  the index the trade is executed
         * @param price  the trade price
         * @param amount the trade amount
         * @return a BUY trade
         */
        @JvmStatic
        fun buyAt(index: Int, price: Num?, amount: Num): Trade {
            return Trade(index, TradeType.BUY, price, amount)
        }

        /**
         * @param index  the index the trade is executed
         * @param series the bar series
         * @param amount the trade amount
         * @return a BUY trade
         */
        @JvmStatic
        fun buyAt(index: Int, series: BarSeries, amount: Num): Trade {
            return Trade(index, series, TradeType.BUY, amount)
        }

        /**
         * @param index                the index the trade is executed
         * @param series               the bar series
         * @param amount               the trade amount
         * @param transactionCostModel the cost model for trade execution
         * @return a BUY trade
         */
        @JvmStatic
        fun buyAt(index: Int, series: BarSeries, amount: Num, transactionCostModel: CostModel?): Trade {
            return Trade(index, series, TradeType.BUY, amount, transactionCostModel)
        }

        /**
         * @param index  the index the trade is executed
         * @param series the bar series
         * @return a SELL trade
         */
        @JvmStatic
        fun sellAt(index: Int, series: BarSeries): Trade {
            return Trade(index, series, TradeType.SELL)
        }

        /**
         * @param index  the index the trade is executed
         * @param price  the trade price
         * @param amount the trade amount
         * @return a SELL trade
         */
        @JvmStatic
        fun sellAt(index: Int, price: Num?, amount: Num): Trade {
            return Trade(index, TradeType.SELL, price, amount)
        }

        /**
         * @param index                the index the trade is executed
         * @param price                the trade price
         * @param amount               the trade amount
         * @param transactionCostModel the cost model for trade execution
         * @return a SELL trade
         */
        @JvmStatic
        fun sellAt(index: Int, price: Num?, amount: Num, transactionCostModel: CostModel?): Trade {
            return Trade(index, TradeType.SELL, price, amount, transactionCostModel)
        }

        /**
         * @param index  the index the trade is executed
         * @param series the bar series
         * @param amount the trade amount
         * @return a SELL trade
         */
        @JvmStatic
        fun sellAt(index: Int, series: BarSeries, amount: Num): Trade {
            return Trade(index, series, TradeType.SELL, amount)
        }

        /**
         * @param index                the index the trade is executed
         * @param series               the bar series
         * @param amount               the trade amount
         * @param transactionCostModel the cost model for trade execution
         * @return a SELL trade
         */
        @JvmStatic
        fun sellAt(index: Int, series: BarSeries, amount: Num, transactionCostModel: CostModel?): Trade {
            return Trade(index, series, TradeType.SELL, amount, transactionCostModel)
        }
    }
}