/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link TradingRecord}.
 *
 * <p>
 * This class is a compatibility facade for classic backtesting flows. Trade and
 * position state transitions are wired through shared trading-record internals
 * so behavior can stay aligned with live-oriented record processing.
 * </p>
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
    private final List<Trade> trades = new ArrayList<>();

    /** The entry type (BUY or SELL) in the trading session. */
    private final TradeType startingType;

    /** The current non-closed position (there's always one). */
    private Position currentPosition;

    /** Cached closed positions derived from recorded trades. */
    private transient List<Position> closedPositionsView;

    /** Trade-count marker for {@link #closedPositionsView}. */
    private transient int closedPositionsTradeCount = -1;

    /** The cost model for transactions of the asset. */
    private final transient CostModel transactionCostModel;

    /** The cost model for holding asset (e.g. borrowing). */
    private final transient CostModel holdingCostModel;

    /** Shared internal snapshot core used for diagnostics/parity tooling. */
    private transient TradingRecordCore tradingRecordCore;

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
        core();
    }

    /**
     * Constructor.
     *
     * @param trades the trades to be recorded (cannot be empty)
     */
    public BaseTradingRecord(Trade... trades) {
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
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, Trade... trades) {
        this(validateTrades(trades), transactionCostModel, holdingCostModel);
        for (Trade o : trades) {
            if (currentPosition.isNew() && o.getType() != startingType) {
                // Special case for entry/exit types reversal
                // E.g.: BUY, SELL,
                // BUY, SELL,
                // SELL, BUY,
                // BUY, SELL
                currentPosition = new Position(o.getType(), transactionCostModel, holdingCostModel);
            }
            operate(o);
        }
    }

    private static TradeType validateTrades(Trade... trades) {
        if (trades == null || trades.length == 0) {
            throw new IllegalArgumentException("At least one trade is required");
        }
        return trades[0].getType();
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
        return core().getCurrentPositionView();
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        core().applySynthetic(index, nextTradeType(), price, amount, transactionCostModel);
    }

    @Override
    public void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        core().applyTrade(trade.getIndex(), trade, -1L);
    }

    private void applyTradeInternal(Trade trade) {
        if (currentPosition.isClosed()) {
            // Current position closed, should not occur
            throw new IllegalStateException("Current position should not be closed");
        }
        Trade newTrade = currentPosition.operate(trade);
        recordTrade(newTrade);
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
        return core().getClosedPositionsView();
    }

    @Override
    public List<Trade> getTrades() {
        return core().getTradesView();
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }

    TradingRecordDebugSnapshot debugSnapshot() {
        return core().snapshot();
    }

    /**
     * Records a trade and the corresponding position (if closed).
     *
     * @param trade the trade to be recorded
     * @throws NullPointerException if trade is null
     */
    private void recordTrade(Trade trade) {
        Objects.requireNonNull(trade, "Trade should not be null");

        // Storing the new trade in trades list
        trades.add(trade);
        closedPositionsTradeCount = -1;
        closedPositionsView = null;

        // Storing the position if closed
        if (currentPosition.isClosed()) {
            currentPosition = new Position(startingType, transactionCostModel, holdingCostModel);
        }
    }

    private static Stream<Trade> tradesOf(Position position) {
        Objects.requireNonNull(position, "position must not be null");

        Trade entry = position.getEntry();
        if (entry == null) {
            throw new IllegalArgumentException("Position entry must not be null");
        }

        Trade exit = position.getExit();
        if (exit == null) {
            return Stream.of(entry);
        }
        return Stream.of(entry, exit);
    }

    private static Trade[] positionToTrades(Position position) {
        return tradesOf(position).toArray(Trade[]::new);
    }

    private static Trade[] positionsToTrades(List<Position> positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        return positions.stream().flatMap(BaseTradingRecord::tradesOf).toArray(Trade[]::new);
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }

    private TradeType nextTradeType() {
        if (currentPosition.isNew()) {
            return startingType;
        }
        if (currentPosition.isOpened()) {
            return currentPosition.getEntry().getType().complementType();
        }
        throw new IllegalStateException("Current position should not be closed");
    }

    private TradingRecordCore core() {
        if (tradingRecordCore == null) {
            tradingRecordCore = new TradingRecordCore(startingType, () -> trades, this::closedPositionsView,
                    () -> currentPosition, this::openPositionsSnapshot, this::netOpenPositionSnapshot,
                    this::totalFeesSnapshot, (index, trade, sequence) -> applyTradeInternal(trade),
                    (index, type, price, amount, transactionCostModel) -> applyTradeInternal(
                            new BaseTrade(index, type, price, amount, transactionCostModel)));
        }
        return tradingRecordCore;
    }

    private List<Position> closedPositionsView() {
        int closedTradeCount = trades.size();
        if (currentPosition.isOpened()) {
            closedTradeCount--;
        }
        if (closedTradeCount < 0) {
            closedTradeCount = 0;
        }
        if (closedPositionsView != null && closedPositionsTradeCount == closedTradeCount) {
            return closedPositionsView;
        }
        List<Position> derived = new ArrayList<>(closedTradeCount / 2);
        for (int i = 0; i + 1 < closedTradeCount; i += 2) {
            derived.add(new Position(trades.get(i), trades.get(i + 1), transactionCostModel, holdingCostModel));
        }
        closedPositionsView = derived;
        closedPositionsTradeCount = closedTradeCount;
        return closedPositionsView;
    }

    private List<OpenPosition> openPositionsSnapshot() {
        if (!currentPosition.isOpened() || currentPosition.getEntry() == null) {
            return List.of();
        }
        Trade entry = currentPosition.getEntry();
        Num fee = feeOf(entry);
        Instant entryTime = executionTimeOf(entry);
        ExecutionSide side = entry.isBuy() ? ExecutionSide.BUY : ExecutionSide.SELL;
        Num totalEntryCost = entry.getPricePerAsset().multipliedBy(entry.getAmount());
        PositionLot lot = new PositionLot(entry.getIndex(), entryTime, entry.getPricePerAsset(), entry.getAmount(), fee,
                entry.getOrderId(), entry.getCorrelationId(), 0L);
        return List.of(new OpenPosition(side, entry.getAmount(), entry.getPricePerAsset(), totalEntryCost, fee,
                entryTime, entryTime, List.of(lot)));
    }

    private OpenPosition netOpenPositionSnapshot() {
        List<OpenPosition> openPositions = openPositionsSnapshot();
        if (openPositions.isEmpty()) {
            return null;
        }
        return openPositions.getFirst();
    }

    private Num totalFeesSnapshot() {
        if (trades.isEmpty()) {
            return DoubleNumFactory.getInstance().zero();
        }
        Num totalFees = trades.getFirst().getPricePerAsset().getNumFactory().zero();
        for (Trade trade : trades) {
            totalFees = totalFees.plus(feeOf(trade));
        }
        return totalFees;
    }

    private static Num feeOf(Trade trade) {
        Num fee = trade.getCost();
        if (fee != null) {
            return fee;
        }
        return trade.getPricePerAsset().getNumFactory().zero();
    }

    private static Instant executionTimeOf(Trade trade) {
        Instant time = trade.getTime();
        if (time != null) {
            return time;
        }
        return Instant.EPOCH;
    }

    @Override
    public String toString() {
        String lineSeparator = System.lineSeparator();
        StringBuilder sb = new StringBuilder().append("BaseTradingRecord: ")
                .append(name == null ? "" : name)
                .append(lineSeparator);
        for (Trade trade : trades) {
            sb.append(trade).append(lineSeparator);
        }
        return sb.toString();
    }
}
