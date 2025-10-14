/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * {@code MultiTradingRecord} is a {@link TradingRecord} implementation that
 * keeps track of multiple open {@link Position positions} at once. Each entry
 * creates a new {@link Position} while exits try to match the position with the
 * same traded amount. When multiple candidates are available, matching respects
 * the configured {@link MatchingPolicy} (defaults to FIFO).
 *
 * <p>
 * The public API mirrors {@link BaseTradingRecord} while exposing additional
 * helpers to introspect open positions. Existing consumers that rely on
 * {@link #getCurrentPosition()} will receive the most recently opened position.
 *
 * @since 0.19
 */
public class MultiTradingRecord implements TradingRecord {

    /**
     * Exit-matching policy that controls how open positions are selected when
     * multiple matches are available.
     *
     * @since 0.19
     */
    public enum MatchingPolicy {
        /** Selects the earliest (first) open position. */
        FIFO,
        /** Selects the most recently opened position. */
        LIFO
    }

    private static final long serialVersionUID = -3436846516158330125L;

    private String name;
    private final Integer startIndex;
    private final Integer endIndex;
    private final TradeType startingType;
    private final transient CostModel transactionCostModel;
    private final transient CostModel holdingCostModel;
    private final MatchingPolicy matchingPolicy;

    private final List<Trade> trades = new ArrayList<>();
    private final List<Trade> buyTrades = new ArrayList<>();
    private final List<Trade> sellTrades = new ArrayList<>();
    private final List<Trade> entryTrades = new ArrayList<>();
    private final List<Trade> exitTrades = new ArrayList<>();
    private final List<Position> closedPositions = new ArrayList<>();

    private final Deque<Position> openPositions = new ArrayDeque<>();
    private Position nextPosition;

    /**
     * Creates a record with default BUY starting type, FIFO matching and zero cost
     * models.
     */
    public MultiTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Creates a record with the provided name.
     *
     * @param name the record name
     */
    public MultiTradingRecord(String name) {
        this(name, TradeType.BUY, null, null, MatchingPolicy.FIFO, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Creates a record with the provided name and matching policy.
     *
     * @param name           the record name
     * @param matchingPolicy the matching policy to use, not null
     */
    public MultiTradingRecord(String name, MatchingPolicy matchingPolicy) {
        this(name, TradeType.BUY, null, null, matchingPolicy, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Creates a record with the provided name, starting type and matching policy.
     *
     * @param name           the record name
     * @param entryTradeType the starting {@link TradeType} for new positions, not
     *                       null
     * @param matchingPolicy the matching policy to use, not null
     */
    public MultiTradingRecord(String name, TradeType entryTradeType, MatchingPolicy matchingPolicy) {
        this(name, entryTradeType, null, null, matchingPolicy, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Creates a record with default BUY starting type, zero cost models and the
     * provided matching policy.
     *
     * @param matchingPolicy the matching policy to use, not null
     */
    public MultiTradingRecord(MatchingPolicy matchingPolicy) {
        this(TradeType.BUY, matchingPolicy);
    }

    /**
     * Creates a record with the provided starting type, FIFO matching and zero cost
     * models.
     *
     * @param entryTradeType the starting {@link TradeType} for new positions, not
     *                       null
     */
    public MultiTradingRecord(TradeType entryTradeType) {
        this(entryTradeType, MatchingPolicy.FIFO);
    }

    /**
     * Creates a record with the provided starting type, matching policy and zero
     * cost models.
     *
     * @param entryTradeType the starting {@link TradeType} for new positions, not
     *                       null
     * @param matchingPolicy the matching policy to use, not null
     */
    public MultiTradingRecord(TradeType entryTradeType, MatchingPolicy matchingPolicy) {
        this(entryTradeType, matchingPolicy, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Creates a record with the provided starting type, matching policy and cost
     * models.
     *
     * @param entryTradeType       the starting {@link TradeType} for new positions,
     *                             not null
     * @param matchingPolicy       the matching policy to use, not null
     * @param transactionCostModel the transaction cost model, not null
     * @param holdingCostModel     the holding cost model, not null
     */
    public MultiTradingRecord(TradeType entryTradeType, MatchingPolicy matchingPolicy, CostModel transactionCostModel,
            CostModel holdingCostModel) {
        this(null, entryTradeType, null, null, matchingPolicy, transactionCostModel, holdingCostModel);
    }

    /**
     * Creates a record with the provided starting type, matching policy, indices
     * and cost models.
     *
     * @param name                 the record name (nullable)
     * @param entryTradeType       the starting {@link TradeType} for new positions,
     *                             not null
     * @param startIndex           the start index (nullable)
     * @param endIndex             the end index (nullable)
     * @param matchingPolicy       the matching policy to use, not null
     * @param transactionCostModel the transaction cost model, not null
     * @param holdingCostModel     the holding cost model, not null
     */
    public MultiTradingRecord(String name, TradeType entryTradeType, Integer startIndex, Integer endIndex,
            MatchingPolicy matchingPolicy, CostModel transactionCostModel, CostModel holdingCostModel) {
        this.name = name;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.matchingPolicy = Objects.requireNonNullElse(matchingPolicy, MatchingPolicy.FIFO);
        this.startingType = Objects.requireNonNull(entryTradeType, "Starting type must not be null");
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel,
                "Transaction cost model must not be null");
        this.holdingCostModel = Objects.requireNonNull(holdingCostModel, "Holding cost model must not be null");
        this.nextPosition = new Position(startingType, this.transactionCostModel, this.holdingCostModel);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    /**
     * @return the exit matching policy
     * @since 0.19
     */
    public MatchingPolicy getMatchingPolicy() {
        return matchingPolicy;
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        if (!nextPosition.isNew()) {
            // Defensive guard: nextPosition should always be new here.
            nextPosition = new Position(startingType, transactionCostModel, holdingCostModel);
        }
        Trade newTrade = nextPosition.operate(index, price, amount);
        if (newTrade == null) {
            return false;
        }
        recordTrade(newTrade, true);
        openPositions.addLast(nextPosition);
        nextPosition = new Position(startingType, transactionCostModel, holdingCostModel);
        return true;
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        Position positionToClose = selectOpenPosition(amount);
        if (positionToClose == null) {
            return false;
        }
        Trade newTrade = positionToClose.operate(index, price, amount);
        if (newTrade == null) {
            return false;
        }
        recordTrade(newTrade, false);

        // Handle partial exits: if exit amount is less than entry amount,
        // create a new position with the remaining amount
        Num entryAmount = positionToClose.getEntry().getAmount();
        if (amount.isLessThan(entryAmount)) {
            // Create a new position with the remaining amount
            Num remainingAmount = entryAmount.minus(amount);
            Position remainingPosition = createPositionWithRemainingAmount(positionToClose, remainingAmount, index);

            // Remove the original position and add the remaining position
            openPositions.remove(positionToClose);
            openPositions.addLast(remainingPosition);

            // Close the partial position
            closedPositions.add(positionToClose);
        } else {
            // Full exit - remove the position completely
            openPositions.remove(positionToClose);
            closedPositions.add(positionToClose);
        }
        return true;
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        if (openPositions.isEmpty()) {
            enter(index, price, amount);
        } else {
            exit(index, price, amount);
        }
    }

    @Override
    public Position getCurrentPosition() {
        Position current = openPositions.peekLast();
        return current != null ? current : nextPosition;
    }

    @Override
    public boolean isClosed() {
        return openPositions.isEmpty();
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
        return closedPositions;
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
        return trades.get(trades.size() - 1);
    }

    @Override
    public Trade getLastTrade(TradeType tradeType) {
        if (TradeType.BUY == tradeType) {
            return buyTrades.isEmpty() ? null : buyTrades.get(buyTrades.size() - 1);
        }
        if (TradeType.SELL == tradeType) {
            return sellTrades.isEmpty() ? null : sellTrades.get(sellTrades.size() - 1);
        }
        return null;
    }

    @Override
    public Trade getLastEntry() {
        return entryTrades.isEmpty() ? null : entryTrades.get(entryTrades.size() - 1);
    }

    @Override
    public Trade getLastExit() {
        return exitTrades.isEmpty() ? null : exitTrades.get(exitTrades.size() - 1);
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }

    @Override
    public List<Position> getOpenPositions() {
        return List.copyOf(openPositions);
    }

    /**
     * Returns the current open positions in the requested order.
     *
     * @param ordering the ordering to apply, not null
     * @return the open positions in the requested order
     * @since 0.19
     */
    public List<Position> getOpenPositions(MatchingPolicy ordering) {
        Objects.requireNonNull(ordering, "Ordering must not be null");
        if (openPositions.isEmpty()) {
            return List.of();
        }
        if (ordering == MatchingPolicy.FIFO) {
            return List.copyOf(openPositions);
        }
        List<Position> reversed = new ArrayList<>(openPositions.size());
        Iterator<Position> descending = openPositions.descendingIterator();
        while (descending.hasNext()) {
            reversed.add(descending.next());
        }
        return List.copyOf(reversed);
    }

    /**
     * Tries to locate an open position whose entry amount equals the requested
     * amount.
     *
     * @param amount the amount to match
     * @return the matching position or {@code null} if none found
     * @since 0.19
     */
    public Position findOpenPositionByAmount(Num amount) {
        if (openPositions.isEmpty() || amount == null || amount.isNaN()) {
            return null;
        }
        Iterator<Position> iterator = iteratorForPolicy(matchingPolicy);
        while (iterator.hasNext()) {
            Position candidate = iterator.next();
            Trade entry = candidate.getEntry();
            if (entry != null) {
                Num entryAmount = entry.getAmount();
                if (entryAmount != null && !entryAmount.isNaN() && entryAmount.equals(amount)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Position selectOpenPosition(Num amount) {
        if (openPositions.isEmpty()) {
            return null;
        }
        Position matched = findOpenPositionByAmount(amount);
        if (matched != null) {
            return matched;
        }
        return matchingPolicy == MatchingPolicy.FIFO ? openPositions.peekFirst() : openPositions.peekLast();
    }

    private Iterator<Position> iteratorForPolicy(MatchingPolicy policy) {
        if (policy == MatchingPolicy.LIFO) {
            return openPositions.descendingIterator();
        }
        return openPositions.iterator();
    }

    private void recordTrade(Trade trade, boolean isEntry) {
        Objects.requireNonNull(trade, "Trade should not be null");
        trades.add(trade);
        if (TradeType.BUY == trade.getType()) {
            buyTrades.add(trade);
        } else if (TradeType.SELL == trade.getType()) {
            sellTrades.add(trade);
        }
        if (isEntry) {
            entryTrades.add(trade);
        } else {
            exitTrades.add(trade);
        }
    }

    /**
     * Creates a new position with the remaining amount from a partially closed
     * position. The new position maintains the same entry trade characteristics but
     * with the remaining amount.
     *
     * @param originalPosition the position that was partially closed
     * @param remainingAmount  the amount remaining after the partial exit
     * @param exitIndex        the index at which the partial exit occurred
     * @return a new position with the remaining amount
     */
    private Position createPositionWithRemainingAmount(Position originalPosition, Num remainingAmount, int exitIndex) {
        Trade originalEntry = originalPosition.getEntry();

        // Create a new position and use operate to set the entry trade
        Position remainingPosition = new Position(originalPosition.getStartingType(), transactionCostModel,
                holdingCostModel);

        // Use operate to create the entry trade with the remaining amount
        remainingPosition.operate(originalEntry.getIndex(), originalEntry.getPricePerAsset(), remainingAmount);

        return remainingPosition;
    }
}
