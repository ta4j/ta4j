/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Read-only projected trading record used for windowed analysis.
 */
final class WindowedTradingRecord implements TradingRecord {

    private final String name;
    private final TradeType startingType;
    private final Integer startIndex;
    private final Integer endIndex;
    private final CostModel transactionCostModel;
    private final CostModel holdingCostModel;
    private final List<Position> positions;
    private final Position currentPosition;
    private final List<Trade> trades;
    private final List<Trade> buyTrades;
    private final List<Trade> sellTrades;
    private final List<Trade> entryTrades;
    private final List<Trade> exitTrades;

    WindowedTradingRecord(String name, TradeType startingType, Integer startIndex, Integer endIndex,
            CostModel transactionCostModel, CostModel holdingCostModel, List<Position> positions,
            Position currentPosition) {
        this.name = name == null ? "windowed-record" : name;
        this.startingType = Objects.requireNonNull(startingType, "startingType");
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.transactionCostModel = Objects.requireNonNullElseGet(transactionCostModel, ZeroCostModel::new);
        this.holdingCostModel = Objects.requireNonNullElseGet(holdingCostModel, ZeroCostModel::new);
        this.positions = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(positions, "positions")));
        this.currentPosition = Objects.requireNonNullElseGet(currentPosition,
                () -> new Position(startingType, this.transactionCostModel, this.holdingCostModel));

        List<Trade> projectedTrades = new ArrayList<>();
        List<Trade> projectedEntryTrades = new ArrayList<>();
        List<Trade> projectedExitTrades = new ArrayList<>();
        List<Trade> projectedBuyTrades = new ArrayList<>();
        List<Trade> projectedSellTrades = new ArrayList<>();

        for (Position position : this.positions) {
            Trade entry = position.getEntry();
            Trade exit = position.getExit();
            if (entry != null) {
                projectedEntryTrades.add(entry);
                projectedTrades.add(entry);
                if (entry.isBuy()) {
                    projectedBuyTrades.add(entry);
                } else {
                    projectedSellTrades.add(entry);
                }
            }
            if (exit != null) {
                projectedExitTrades.add(exit);
                projectedTrades.add(exit);
                if (exit.isBuy()) {
                    projectedBuyTrades.add(exit);
                } else {
                    projectedSellTrades.add(exit);
                }
            }
        }
        if (this.currentPosition != null && this.currentPosition.isOpened()
                && this.currentPosition.getEntry() != null) {
            var entry = this.currentPosition.getEntry();
            projectedEntryTrades.add(entry);
            projectedTrades.add(entry);
            if (entry.isBuy()) {
                projectedBuyTrades.add(entry);
            } else {
                projectedSellTrades.add(entry);
            }
        }

        this.trades = Collections.unmodifiableList(projectedTrades);
        this.entryTrades = Collections.unmodifiableList(projectedEntryTrades);
        this.exitTrades = Collections.unmodifiableList(projectedExitTrades);
        this.buyTrades = Collections.unmodifiableList(projectedBuyTrades);
        this.sellTrades = Collections.unmodifiableList(projectedSellTrades);
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        throw new UnsupportedOperationException("WindowedTradingRecord is read-only");
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        throw new UnsupportedOperationException("WindowedTradingRecord is read-only");
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        throw new UnsupportedOperationException("WindowedTradingRecord is read-only");
    }

    @Override
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    @Override
    public CostModel getHoldingCostModel() {
        return holdingCostModel;
    }

    @Override
    public List<Position> getPositions() {
        return positions;
    }

    @Override
    public Position getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public List<Trade> getTrades() {
        return trades;
    }

    @Override
    public Trade getLastTrade() {
        if (trades.isEmpty()) {
            return null;
        }
        return trades.getLast();
    }

    @Override
    public Trade getLastTrade(TradeType tradeType) {
        if (tradeType == TradeType.BUY && !buyTrades.isEmpty()) {
            return buyTrades.getLast();
        }
        if (tradeType == TradeType.SELL && !sellTrades.isEmpty()) {
            return sellTrades.getLast();
        }
        return null;
    }

    @Override
    public Trade getLastEntry() {
        if (entryTrades.isEmpty()) {
            return null;
        }
        return entryTrades.getLast();
    }

    @Override
    public Trade getLastExit() {
        if (exitTrades.isEmpty()) {
            return null;
        }
        return exitTrades.getLast();
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }
}
