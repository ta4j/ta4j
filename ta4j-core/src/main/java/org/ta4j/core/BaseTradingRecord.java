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

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link TradingRecord}.
 * * {@link TradingRecord} 的基本实现。
 *
 */
public class BaseTradingRecord implements TradingRecord {

    private static final long serialVersionUID = -4436851731855891220L;

    /**
     * The name of the trading record
     * * 交易记录名称
     */
    private String name;

    /**
     * The recorded trades
     * 记录的交易
     */
    private List<Trade> trades = new ArrayList<>();

    /**
     * The recorded BUY trades
     * 记录的买入交易
     */
    private List<Trade> buyTrades = new ArrayList<>();

    /**
     * The recorded SELL trades
     * 记录的卖出交易
     */
    private List<Trade> sellTrades = new ArrayList<>();

    /**
     * The recorded entry trades
     * 记录的入场交易
     */
    private List<Trade> entryTrades = new ArrayList<>();

    /**
     * The recorded exit trades
     * 记录的退出交易
     */
    private List<Trade> exitTrades = new ArrayList<>();

    /**
     * The entry type (BUY or SELL) in the trading session
     * 交易时段的入场类型（买入或卖出）
     */
    private TradeType startingType;

    /**
     * The recorded positions
     * 记录的位置
     */
    private List<Position> positions = new ArrayList<>();

    /**
     * The current non-closed position (there's always one)
     * 当前未平仓（总有一个）
     */
    private Position currentPosition;

    /**
     * Trading cost models
     * 交易成本模型
     */
    private CostModel transactionCostModel;
    private CostModel holdingCostModel;

    /**
     * Constructor.
     */
    public BaseTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Constructor.
     *
     * @param name the name of the tradingRecord
     *             * @param name 交易记录的名称
     */
    public BaseTradingRecord(String name) {
        this(TradeType.BUY);
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param name           the name of the trading record
     *                       交易记录的名称
     *
     * @param entryTradeType the {@link TradeType trade type} of entries in the trading session
     *                       * @param entryTradeType {@link TradeType trade type} 交易时段的条目
     */
    public BaseTradingRecord(String name, TradeType tradeType) {
        this(tradeType, new ZeroCostModel(), new ZeroCostModel());
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param entryTradeType the {@link TradeType trade type} of entries in the  trading session
     *                       交易时段中条目的 {@link TradeType trade type}
     */
    public BaseTradingRecord(TradeType tradeType) {
        this(tradeType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param entryTradeType       the {@link TradeType trade type} of entries in  the trading session
     *                             交易时段中条目的 {@link TradeType trade type}
     *
     * @param transactionCostModel the cost model for transactions of the asset
     *                             资产交易的成本模型
     *
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     *                             持有资产的成本模型（例如借款）
     */
    public BaseTradingRecord(TradeType entryTradeType, CostModel transactionCostModel, CostModel holdingCostModel) {
        if (entryTradeType == null) {
            throw new IllegalArgumentException("Starting type must not be null 起始类型不能为空");
        }
        this.startingType = entryTradeType;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        currentPosition = new Position(entryTradeType, transactionCostModel, holdingCostModel);
    }

    /**
     * Constructor.
     *
     * @param trades the trades to be recorded (cannot be empty)
     *               要记录的交易（不能为空）
     */
    public BaseTradingRecord(Trade... trades) {
        this(new ZeroCostModel(), new ZeroCostModel(), trades);
    }

    /**
     * Constructor.
     *
     * @param transactionCostModel the cost model for transactions of the asset
     *                             资产交易的成本模型
     *
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     *                             持有资产的成本模型（例如借款）
     *
     * @param trades               the trades to be recorded (cannot be empty)
     *                             要记录的交易（不能为空）
     */
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, Trade... trades) {
        this(trades[0].getType(), transactionCostModel, holdingCostModel);
        for (Trade o : trades) {
            boolean newTradeWillBeAnEntry = currentPosition.isNew();
            if (newTradeWillBeAnEntry && o.getType() != startingType) {
                // Special case for entry/exit types reversal
                // 进入/退出类型反转的特殊情况
                // E.g.: BUY, SELL,
                // 例如：买，卖，
                // BUY, SELL,
                // 买,卖
                // SELL, BUY,
                // 卖,买
                // BUY , SELL
                //买，卖
                currentPosition = new Position(o.getType(), transactionCostModel, holdingCostModel);
            }
            Trade newTrade = currentPosition.operate(o.getIndex(), o.getPricePerAsset(), o.getAmount());
            recordTrade(newTrade, newTradeWillBeAnEntry);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    @Override
    public Position getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        if (currentPosition.isClosed()) {
            // Current position closed, should not occur
            // 当前仓位已平仓，不应发生
            throw new IllegalStateException("Current position should not be closed 当前仓位不应平仓");
        }
        boolean newTradeWillBeAnEntry = currentPosition.isNew();
        Trade newTrade = currentPosition.operate(index, price, amount);
        recordTrade(newTrade, newTradeWillBeAnEntry);
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        if (currentPosition.isNew()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        if (currentPosition.isOpened()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }

    @Override
    public List<Position> getPositions() {
        return positions;
    }

    @Override
    public Trade getLastTrade() {
        if (!trades.isEmpty()) {
            return trades.get(trades.size() - 1);
        }
        return null;
    }

    @Override
    public Trade getLastTrade(TradeType tradeType) {
        if (TradeType.BUY.equals(tradeType) && !buyTrades.isEmpty()) {
            return buyTrades.get(buyTrades.size() - 1);
        } else if (TradeType.SELL.equals(tradeType) && !sellTrades.isEmpty()) {
            return sellTrades.get(sellTrades.size() - 1);
        }
        return null;
    }

    @Override
    public Trade getLastEntry() {
        if (!entryTrades.isEmpty()) {
            return entryTrades.get(entryTrades.size() - 1);
        }
        return null;
    }

    @Override
    public Trade getLastExit() {
        if (!exitTrades.isEmpty()) {
            return exitTrades.get(exitTrades.size() - 1);
        }
        return null;
    }

    /**
     * Records an trade and the corresponding position (if closed).
     * * 记录一笔交易和相应的头寸（如果平仓）。
     *
     * @param trade   the trade to be recorded
     *                要记录的交易
     *
     * @param isEntry true if the trade is an entry, false otherwise (exit)
     *                如果交易是一个条目，则为 true，否则为 false（退出）
     */
    private void recordTrade(Trade trade, boolean isEntry) {
        if (trade == null) {
            throw new IllegalArgumentException("Trade should not be null 贸易不应该为空");
        }

        // Storing the new trade in entries/exits lists
        // 将新交易存储在进入/退出列表中
        if (isEntry) {
            entryTrades.add(trade);
        } else {
            exitTrades.add(trade);
        }

        // Storing the new trade in trades list
        // 在交易列表中存储新的交易
        trades.add(trade);
        if (TradeType.BUY.equals(trade.getType())) {
            // Storing the new trade in buy trades list
            // 将新交易存储在买入交易列表中
            buyTrades.add(trade);
        } else if (TradeType.SELL.equals(trade.getType())) {
            // Storing the new trade in sell trades list
            // 在卖出交易列表中存储新的交易
            sellTrades.add(trade);
        }

        // Storing the position if closed
        // 如果关闭则保存仓位
        if (currentPosition.isClosed()) {
            positions.add(currentPosition);
            currentPosition = new Position(startingType, transactionCostModel, holdingCostModel);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BaseTradingRecord 基础交易记录: " + name != null ? name : "" + "\n");
        for (Trade trade : trades) {
            sb.append(trade.toString()).append("\n");
        }
        return sb.toString();
    }
}
