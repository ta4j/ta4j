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

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Pair of two {@link Trade trades}.
 * * 两对 {@link Trade trades}。
 *
 * The exit trade has the complement type of the entry trade.<br>
 * * 离场交易具有进场交易的补码类型。<br>
 * I.e.: entry == BUY --> exit == SELL entry == SELL --> exit == BUY
 * * 即：进入 == 买入 --> 退出 == 卖出 进入 == 卖出 --> 退出 == 买入
 */
public class Position implements Serializable {

    private static final long serialVersionUID = -5484709075767220358L;

    /** The entry trade
     * 入场交易*/
    private Trade entry;

    /** The exit trade
     * 退出交易*/
    private Trade exit;

    /** The type of the entry trade
     * 入场交易的类型 */
    private TradeType startingType;

    /** The cost model for transactions of the asset
     * 资产交易的成本模型 */
    private CostModel transactionCostModel;

    /** The cost model for holding the asset
     * 持有资产的成本模型 */
    private CostModel holdingCostModel;

    /**
     * Constructor.
     */
    public Position() {
        this(TradeType.BUY);
    }

    /**
     * Constructor.
     * 
     * @param startingType the starting {@link TradeType trade type} of the position (i.e. type of the entry trade)
     *                     * @param startingType 头寸的起始{@link TradeType 交易类型}（即入场交易的类型）
     */
    public Position(TradeType startingType) {
        this(startingType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param startingType         the starting {@link TradeType trade type} of the  position (i.e. type of the entry trade)
     *                             * @param startingType 头寸的起始{@link TradeType 交易类型}（即入场交易的类型）
     *
     * @param transactionCostModel the cost model for transactions of the asset
     *                             资产交易的成本模型
     *
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     *                             持有资产的成本模型（例如借款）
     */
    public Position(TradeType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null 起始类型不能为空");
        }
        this.startingType = startingType;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * Constructor.
     * 
     * @param entry the entry {@link Trade trade}
     *              条目 {@link Trade trade}
     *
     * @param exit  the exit {@link Trade trade}
     *              退出 {@link Trade trade}
     */
    public Position(Trade entry, Trade exit) {
        this(entry, exit, entry.getCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param entry                the entry {@link Trade trade}
     *                             条目 {@link Trade trade}
     *
     * @param exit                 the exit {@link Trade trade}
     *                             退出 {@link Trade trade}
     *
     * @param transactionCostModel the cost model for transactions of the asset
     *                             资产交易的成本模型
     *
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     *                             持有资产的成本模型（例如借款）
     */
    public Position(Trade entry, Trade exit, CostModel transactionCostModel, CostModel holdingCostModel) {

        if (entry.getType().equals(exit.getType())) {
            throw new IllegalArgumentException("Both trades must have different types 两种交易必须有不同的类型");
        }

        if (!(entry.getCostModel().equals(transactionCostModel))
                || !(exit.getCostModel().equals(transactionCostModel))) {
            throw new IllegalArgumentException("Trades and the position must incorporate the same trading cost model 交易和头寸必须包含相同的交易成本模型");
        }

        this.startingType = entry.getType();
        this.entry = entry;
        this.exit = exit;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * @return the entry {@link Trade trade} of the position
     * * @return 仓位的入口{@link Trade trade}
     */
    public Trade getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Trade trade} of the position
     * * @return 仓位的退出{@link Trade trade}
     */
    public Trade getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Position) {
            Position p = (Position) obj;
            return (entry == null ? p.getEntry() == null : entry.equals(p.getEntry()))
                    && (exit == null ? p.getExit() == null : exit.equals(p.getExit()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, exit);
    }

    /**
     * Operates the position at the index-th position
     * * 操作第 index 个位置的位置
     *
     * @param index the bar index
     *              条形索引
     *
     * @return the trade
     * 贸易
     */
    public Trade operate(int index) {
        return operate(index, NaN, NaN);
    }

    /**
     * Operates the position at the index-th position
     * * 操作第 index 个位置的位置
     * 
     * @param index  the bar index
     *               条形索引
     *
     * @param price  the price
     *               价格
     *
     * @param amount the amount
     *               数量
     *
     * @return the trade
     *              贸易
     */
    public Trade operate(int index, Num price, Num amount) {
        Trade trade = null;
        if (isNew()) {
            trade = new Trade(index, startingType, price, amount, transactionCostModel);
            entry = trade;
        } else if (isOpened()) {
            if (index < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryTrade index 索引 i 小于 entryTrade 索引");
            }
            trade = new Trade(index, startingType.complementType(), price, amount, transactionCostModel);
            exit = trade;
        }
        return trade;
    }

    /**
     * @return true if the position is closed, false otherwise
     * * @return 如果平仓则返回 true，否则返回 false
     */
    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    /**
     * @return true if the position is opened, false otherwise
     * @return 开仓返回真，否则返回假
     */
    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    /**
     * @return true if the position is new, false otherwise
     * * @return 如果位置是新的，则返回 true，否则返回 false
     */
    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    @Override
    public String toString() {
        return "Entry 入口: " + entry + " exit 出口: " + exit;
    }

    /**
     * @return true if position is closed and {@link #getProfit()} > 0
     * * @return 如果仓位平仓且 {@link #getProfit()} > 0，则返回 true
     */
    public boolean hasProfit() {
        return getProfit().isPositive();
    }

    /**
     * @return true if position is closed and {@link #getProfit()} < 0
     * * @return 如果仓位关闭并且 {@link #getProfit()} < 0，则返回 true
     */
    public boolean hasLoss() {
        return getProfit().isNegative();
    }

    /**
     * Calculate the profit of the position if it is closed
     * * 计算平仓时的利润
     *
     * @return the profit or loss of the position
     *      * @return the profit or loss of the position
     */
    public Num getProfit() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossProfit(exit.getPricePerAsset()).minus(getPositionCost());
        }
    }

    /**
     * Calculate the profit of the position. If it is open, calculates the profit until the final bar.
     * * 计算仓位的利润。 如果它是开放的，则计算直到最后一根柱子的利润。
     *
     * @param finalIndex the index of the final bar to be considered (if position is open)
     *                   * @param finalIndex 要考虑的最后一根柱的索引（如果仓位未平仓）
     *
     * @param finalPrice the price of the final bar to be considered (if position is open)
     *                   * @param finalPrice 要考虑的最终柱的价格（如果持仓未平仓）
     *
     * @return the profit or loss of the position
     *          头寸的盈亏
     */
    public Num getProfit(int finalIndex, Num finalPrice) {
        Num grossProfit = getGrossProfit(finalPrice);
        Num tradingCost = getPositionCost(finalIndex);
        return grossProfit.minus(tradingCost);
    }

    /**
     * Calculate the gross return of the position if it is closed
     *              如果平仓，计算头寸的总回报
     *
     * @return the gross return of the position in percent
     *              头寸的总回报百分比
     */
    public Num getGrossReturn() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossReturn(exit.getPricePerAsset());
        }
    }

    /**
     * Calculate the gross return of the position, if it exited at the provided price.
     * * 计算头寸的总回报，如果它以提供的价格退出。
     *
     * @param finalPrice the price of the final bar to be considered (if position is open)
     *                   要考虑的最后一根柱线的价格（如果头寸未平仓）
     * @return the gross return of the position in percent
     * * @return 以百分比为单位的头寸总回报
     */
    public Num getGrossReturn(Num finalPrice) {
        return getGrossReturn(getEntry().getPricePerAsset(), finalPrice);
    }

    /**
     * Calculates the gross return of the position. If either the entry or the exit price are <code>NaN</code>, the close price from the supplied {@link BarSeries} is used.
     * * 计算头寸的总回报。 如果进入或退出价格是 <code>NaN</code>，则使用提供的 {@link BarSeries} 的收盘价。
     * 
     * @param barSeries
     * @return the gross return in percent with entry and exit prices from the   barSeries
     * * @return 以 barSeries 的进入和退出价格为单位的总回报率
     */
    public Num getGrossReturn(BarSeries barSeries) {
        Num entryPrice = getEntry().getPricePerAsset(barSeries);
        Num exitPrice = getExit().getPricePerAsset(barSeries);
        return getGrossReturn(entryPrice, exitPrice);
    }

    /**
     * Calculates the gross return between entry and exit price in percent. Includes the base.
     * * 以百分比计算进入和退出价格之间的总回报。 包括底座。
     * <p>
     * For example:
     * 例如：
     * <ul>
     * <li>For buy position with a profit of 4%, it returns 1.04 (includes the base)
     * * <li>对于获利为 4% 的买入仓位，返回 1.04（包括底数）
     *
     * <li>For sell position with a loss of 4%, it returns 0.96 (includes the base)
     *      对于亏损4%的空头头寸，返回0.96（包括底价）
     * </ul>
     * 
     * @param entryPrice the entry price
     *                   入场价
     *
     * @param exitPrice  the exit price
     *                   退出价格
     *
     * @return the gross return in percent between entryPrice and exitPrice (includes the base)
     * * @return 在 entryPrice 和 exitPrice 之间的总回报百分比（包括基数）
     */
    public Num getGrossReturn(Num entryPrice, Num exitPrice) {
        if (getEntry().isBuy()) {
            return exitPrice.dividedBy(entryPrice);
        } else {
            Num one = entryPrice.numOf(1);
            return ((exitPrice.dividedBy(entryPrice).minus(one)).negate()).plus(one);
        }
    }

    /**
     * Calculate the gross profit of the position if it is closed
     * * 如果平仓，计算仓位的毛利润
     *
     * @return the gross profit of the position
     * * @return 仓位毛利
     */
    public Num getGrossProfit() {
        if (isOpened()) {
            return numOf(0);
        } else {
            return getGrossProfit(exit.getPricePerAsset());
        }
    }

    /**
     * Calculate the gross (w/o trading costs) profit of the position.
     * * 计算头寸的毛利润（不含交易成本）。
     * 
     * @param finalPrice the price of the final bar to be considered (if position is  open)
     *                   * @param finalPrice 要考虑的最终柱的价格（如果持仓未平仓）
     *
     * @return the profit or loss of the position
     * * @return 仓位盈亏
     */
    public Num getGrossProfit(Num finalPrice) {
        Num grossProfit;
        if (isOpened()) {
            grossProfit = entry.getAmount().multipliedBy(finalPrice).minus(entry.getValue());
        } else {
            grossProfit = exit.getValue().minus(entry.getValue());
        }

        // Profits of long position are losses of short
        // 多头头寸的利润是空头头寸的损失
        if (entry.isSell()) {
            grossProfit = grossProfit.negate();
        }
        return grossProfit;
    }

    /**
     * Calculates the total cost of the position
     * * 计算职位的总成本
     * 
     * @param finalIndex the index of the final bar to be considered (if position is  open)
     *                   * @param finalIndex 要考虑的最后一根柱的索引（如果仓位未平仓）
     * @return the cost of the position
     *  @return 职位的成本
     */
    public Num getPositionCost(int finalIndex) {
        Num transactionCost = transactionCostModel.calculate(this, finalIndex);
        Num borrowingCost = getHoldingCost(finalIndex);
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the total cost of the closed position
     * * 计算平仓的总成本
     * 
     * @return the cost of the position
     * * @return 仓位的成本
     */
    public Num getPositionCost() {
        Num transactionCost = transactionCostModel.calculate(this);
        Num borrowingCost = getHoldingCost();
        return transactionCost.plus(borrowingCost);
    }

    /**
     * Calculates the holding cost of the closed position
     * * 计算平仓的持有成本
     * 
     * @return the cost of the position
     * * @return 仓位的成本
     */
    public Num getHoldingCost() {
        return holdingCostModel.calculate(this);
    }

    /**
     * Calculates the holding cost of the position
     * * 计算持仓成本
     * 
     * @param finalIndex the index of the final bar to be considered (if position is  open)
     *                   * @param finalIndex 要考虑的最后一根柱的索引（如果仓位未平仓）
     *
     * @return the cost of the position
     * * @return 仓位的成本
     */
    public Num getHoldingCost(int finalIndex) {
        return holdingCostModel.calculate(this, finalIndex);
    }

    private Num numOf(Number num) {
        return entry.getNetPrice().numOf(num);
    }
}
