/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.util.stream.Stream;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link TradingRecord}.
 */
public class BaseTradingRecord implements TradingRecord {

    @Serial
    private static final long serialVersionUID = -4436851731855891220L;

    /** The name of the trading record. */
    private String name;

    /** The start of the recording (included). */
    private final Integer startIndex;

    /** The end of the recording (included). */
    private final Integer endIndex;

    /** The recorded trades. */
    private final List<TradeView> trades = new ArrayList<>();

    /** The recorded BUY trades. */
    private final List<TradeView> buyTrades = new ArrayList<>();

    /** The recorded SELL trades. */
    private final List<TradeView> sellTrades = new ArrayList<>();

    /** The recorded entry trades. */
    private final List<TradeView> entryTrades = new ArrayList<>();

    /** The recorded exit trades. */
    private final List<TradeView> exitTrades = new ArrayList<>();

    /** The entry type (BUY or SELL) in the trading session. */
    private final TradeType startingType;

    /** The recorded positions. */
    private final List<Position> positions = new ArrayList<>();

    /** The current non-closed position (there's always one). */
    private Position currentPosition;

    /** The cost model for transactions of the asset. */
    private final transient CostModel transactionCostModel;

    /** The cost model for holding asset (e.g. borrowing). */
    private final transient CostModel holdingCostModel;

    /** Constructor with {@link #startingType} = BUY. */
    public BaseTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Constructor with {@link #startingType} = BUY.
     *
     * @param name the name of the tradingRecord
     */
    public BaseTradingRecord(String name) {
        this(TradeType.BUY);
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param name      the name of the trading record
     * @param tradeType the {@link TradeType trade type} of entries in the trading
     *                  session
     */
    public BaseTradingRecord(String name, TradeType tradeType) {
        this(tradeType, new ZeroCostModel(), new ZeroCostModel());
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param tradeType the {@link TradeType trade type} of entries in the trading
     *                  session
     */
    public BaseTradingRecord(TradeType tradeType) {
        this(tradeType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param entryTradeType       the {@link TradeType trade type} of entries in
     *                             the trading session
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     */
    public BaseTradingRecord(TradeType entryTradeType, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(entryTradeType, null, null, transactionCostModel, holdingCostModel);
    }

    /**
     * Constructor.
     *
     * @param entryTradeType       the {@link TradeType trade type} of entries in
     *                             the trading session
     * @param startIndex           the start of the recording (included)
     * @param endIndex             the end of the recording (included)
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     * @throws NullPointerException if entryTradeType is null
     */
    public BaseTradingRecord(TradeType entryTradeType, Integer startIndex, Integer endIndex,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        Objects.requireNonNull(entryTradeType, "Starting type must not be null");

        this.startingType = entryTradeType;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        currentPosition = new Position(entryTradeType, transactionCostModel, holdingCostModel);
    }

    /**
     * Constructor.
     *
     * @param trades the trades to be recorded (cannot be empty)
     */
    public BaseTradingRecord(TradeView... trades) {
        this(new ZeroCostModel(), new ZeroCostModel(), trades);
    }

    /**
     * Constructor.
     *
     * @param position the position to be recorded (entry required)
     * @since 0.22.2
     */
    public BaseTradingRecord(Position position) {
        this(defaultCostModel(position.getTransactionCostModel()), defaultCostModel(position.getHoldingCostModel()),
                positionToTrades(position));
    }

    /**
     * Constructor.
     *
     * @param positions the positions to be recorded (cannot be empty)
     * @since 0.22.2
     */
    public BaseTradingRecord(List<Position> positions) {
        this(positionsToTrades(positions));
    }

    /**
     * Constructor.
     *
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding the asset (e.g.
     *                             borrowing)
     * @param trades               the trades to be recorded (cannot be empty)
     */
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, TradeView... trades) {
        this(trades[0].getType(), transactionCostModel, holdingCostModel);
        for (TradeView o : trades) {
            boolean newTradeWillBeAnEntry = currentPosition.isNew();
            if (newTradeWillBeAnEntry && o.getType() != startingType) {
                // Special case for entry/exit types reversal
                // E.g.: BUY, SELL,
                // BUY, SELL,
                // SELL, BUY,
                // BUY, SELL
                currentPosition = new Position(o.getType(), transactionCostModel, holdingCostModel);
            }
            TradeView newTrade = currentPosition.operate(o.getIndex(), o.getPricePerAsset(), o.getAmount());
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
            throw new IllegalStateException("Current position should not be closed");
        }
        boolean newTradeWillBeAnEntry = currentPosition.isNew();
        TradeView newTrade = currentPosition.operate(index, price, amount);
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
    public List<TradeView> getTrades() {
        return trades;
    }

    @Override
    public TradeView getLastTrade() {
        if (!trades.isEmpty()) {
            return trades.getLast();
        }
        return null;
    }

    @Override
    public TradeView getLastTrade(TradeType tradeType) {
        if (TradeType.BUY == tradeType && !buyTrades.isEmpty()) {
            return buyTrades.getLast();
        } else if (TradeType.SELL == tradeType && !sellTrades.isEmpty()) {
            return sellTrades.getLast();
        }
        return null;
    }

    @Override
    public TradeView getLastEntry() {
        if (!entryTrades.isEmpty()) {
            return entryTrades.getLast();
        }
        return null;
    }

    @Override
    public TradeView getLastExit() {
        if (!exitTrades.isEmpty()) {
            return exitTrades.getLast();
        }
        return null;
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }

    /**
     * Records a trade and the corresponding position (if closed).
     *
     * @param trade   the trade to be recorded
     * @param isEntry true if the trade is an entry, false otherwise (exit)
     * @throws NullPointerException if trade is null
     */
    private void recordTrade(TradeView trade, boolean isEntry) {
        Objects.requireNonNull(trade, "Trade should not be null");

        // Storing the new trade in entries/exits lists
        if (isEntry) {
            entryTrades.add(trade);
        } else {
            exitTrades.add(trade);
        }

        // Storing the new trade in trades list
        trades.add(trade);
        if (TradeType.BUY == trade.getType()) {
            // Storing the new trade in buy trades list
            buyTrades.add(trade);
        } else if (TradeType.SELL == trade.getType()) {
            // Storing the new trade in sell trades list
            sellTrades.add(trade);
        }

        // Storing the position if closed
        if (currentPosition.isClosed()) {
            positions.add(currentPosition);
            currentPosition = new Position(startingType, transactionCostModel, holdingCostModel);
        }
    }

    private static Stream<TradeView> tradesOf(Position position) {
        Objects.requireNonNull(position, "position must not be null");

        var entry = position.getEntry();
        if (entry == null) {
            throw new IllegalArgumentException("Position entry must not be null");
        }

        var exit = position.getExit();
        if (exit == null) {
            return Stream.of(entry);
        }
        return Stream.of(entry, exit);
    }

    private static TradeView[] positionToTrades(Position position) {
        return tradesOf(position).toArray(TradeView[]::new);
    }

    private static TradeView[] positionsToTrades(List<Position> positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        return positions.stream().flatMap(BaseTradingRecord::tradesOf).toArray(TradeView[]::new);
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }

    @Override
    public String toString() {
        var lineSeparator = System.lineSeparator();
        var sb = new StringBuilder().append("BaseTradingRecord: ")
                .append(name == null ? "" : name)
                .append(lineSeparator);
        for (var trade : trades) {
            sb.append(trade).append(lineSeparator);
        }
        return sb.toString();
    }
}
