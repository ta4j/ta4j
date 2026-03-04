/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Serial;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Live trading record that supports partial fills and multi-lot positions.
 *
 * <p>
 * This class is a compatibility facade for live-oriented APIs while delegating
 * shared trading-record state handling through common internals used by
 * backtesting paths.
 * </p>
 *
 * @since 0.22.2
 */
public class LiveTradingRecord implements TradingRecord, PositionLedger {

    @Serial
    private static final long serialVersionUID = 7960596064337713648L;

    private static final Gson GSON = new Gson();

    private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final TradeType startingType;
    private final ExecutionMatchPolicy matchPolicy;
    private transient CostModel transactionCostModel;
    private transient CostModel holdingCostModel;
    private final PositionBook positionBook;
    private final Integer startIndex;
    private final Integer endIndex;
    private String name;
    private int nextTradeIndex;
    private transient List<Trade> tradesCache;
    private transient long tradesCacheVersion;
    private long modificationCount;
    private Num totalFees;
    private transient NumFactory numFactory;
    private long nextSequence;
    private transient TradingRecordCore tradingRecordCore;

    /**
     * Creates a live trading record with BUY entries and FIFO matching.
     *
     * @since 0.22.2
     */
    public LiveTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Creates a live trading record.
     *
     * @param startingType entry trade type
     * @since 0.22.2
     */
    public LiveTradingRecord(TradeType startingType) {
        this(startingType, ExecutionMatchPolicy.FIFO, new ZeroCostModel(), new ZeroCostModel(), null, null);
    }

    /**
     * Creates a live trading record.
     *
     * @param startingType         entry trade type
     * @param matchPolicy          matching policy
     * @param transactionCostModel transaction cost model (ignored in favor of
     *                             recorded fees)
     * @param holdingCostModel     holding cost model
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     * @since 0.22.2
     */
    public LiveTradingRecord(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
            CostModel holdingCostModel, Integer startIndex, Integer endIndex) {
        Objects.requireNonNull(startingType, "startingType");
        Objects.requireNonNull(matchPolicy, "matchPolicy");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        Objects.requireNonNull(holdingCostModel, "holdingCostModel");
        this.startingType = startingType;
        this.matchPolicy = matchPolicy;
        this.transactionCostModel = RecordedTradeCostModel.INSTANCE;
        this.holdingCostModel = holdingCostModel;
        this.positionBook = new PositionBook(startingType, matchPolicy, this.transactionCostModel, holdingCostModel);
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.nextTradeIndex = 0;
    }

    /**
     * Records a live trade using an auto-incremented trade index.
     *
     * <p>
     * The trade index is overwritten by the record's auto-incremented index. Use
     * {@link #recordFill(int, Trade)} when you need to preserve a specific index.
     * </p>
     *
     * @param trade live trade
     * @throws IllegalArgumentException when trade price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordFill(Trade trade) {
        core().applyTrade(nextIndex(), trade, -1L);
    }

    /**
     * Records an execution fill.
     *
     * <p>
     * If {@link TradeFill#index()} is non-negative, that index is used. Otherwise,
     * the record assigns the next auto-incremented index.
     * </p>
     *
     * @param fill execution fill
     * @throws IllegalArgumentException when fill price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordExecutionFill(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        int index = fill.index();
        ExecutionSide side = resolveExecutionSide(fill.side());
        Instant time = resolveExecutionTime(fill.time(), null);
        BaseTrade trade = new BaseTrade(index >= 0 ? index : 0, time, fill.price(), fill.amount(), fill.fee(), side,
                fill.orderId(), fill.correlationId());
        if (index >= 0) {
            recordFill(index, trade);
        } else {
            recordFill(trade);
        }
    }

    /**
     * Records a live trade using the provided trade index.
     *
     * @param index trade index
     * @param trade live trade
     * @throws IllegalArgumentException when trade price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordFill(int index, Trade trade) {
        core().applyTrade(index, trade, -1L);
    }

    private void applyTradeInternal(int index, Trade trade, long sequence) {
        Objects.requireNonNull(trade, "trade");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        validateFill(trade);
        Num fee = feeOf(trade);
        Num price = trade.getPricePerAsset();
        TradeType type = trade.getType();
        lock.writeLock().lock();
        try {
            nextTradeIndex = Math.max(nextTradeIndex, index + 1);
            long appliedSequence = sequence >= 0 ? sequence : nextSequence++;
            if (appliedSequence >= nextSequence) {
                nextSequence = appliedSequence + 1;
            }
            if (type == startingType) {
                positionBook.recordEntry(index, trade, appliedSequence);
            } else {
                positionBook.recordExit(index, trade, appliedSequence);
            }
            if (numFactory == null) {
                numFactory = price.getNumFactory();
            }
            if (totalFees == null) {
                totalFees = numFactory.zero();
            }
            totalFees = totalFees.plus(fee);
            modificationCount++;
            tradesCache = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns open positions as lots.
     *
     * @return open positions
     * @since 0.22.2
     */
    @Override
    public List<OpenPosition> getOpenPositions() {
        return core().getOpenPositionsSnapshot();
    }

    /**
     * Returns the aggregated net open position.
     *
     * @return net open position, or null if none
     * @since 0.22.2
     */
    @Override
    public OpenPosition getNetOpenPosition() {
        return core().getNetOpenPositionSnapshot();
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    /**
     * Returns the execution match policy used by this record.
     *
     * @return match policy
     * @since 0.22.2
     */
    public ExecutionMatchPolicy getMatchPolicy() {
        return matchPolicy;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this record.
     *
     * @param name record name
     * @since 0.22.2
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Records a pre-built trade while preserving its fill progression.
     *
     * <p>
     * Multi-fill trades are replayed as individual live fills using the trade type
     * side.
     * </p>
     *
     * @param trade trade to record
     * @since 0.22.4
     */
    @Override
    public void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = Trade.executionFillsOf(trade);
        for (TradeFill fill : fills) {
            recordTradeFill(trade.getType(), fill, trade.getOrderId(), trade.getCorrelationId(), trade.getTime());
        }
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            TradeType tradeType = positionBook.openLots().isEmpty() ? startingType : startingType.complementType();
            core().applySynthetic(index, tradeType, price, amount, transactionCostModel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void recordTradeFill(TradeType tradeType, TradeFill fill, String tradeOrderId, String tradeCorrelationId,
            Instant tradeTime) {
        ExecutionSide side = fill.side() == null ? sideOf(tradeType) : fill.side();
        Instant executionTime = resolveExecutionTime(fill.time(), tradeTime);
        String orderId = chooseValue(fill.orderId(), tradeOrderId);
        String correlationId = chooseValue(fill.correlationId(), tradeCorrelationId);
        core().applyTrade(fill.index(), new BaseTrade(fill.index(), executionTime, fill.price(), fill.amount(),
                fill.fee(), side, orderId, correlationId), -1L);
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (!positionBook.openLots().isEmpty()) {
                return false;
            }
            core().applySynthetic(index, startingType, price, amount, transactionCostModel);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (positionBook.openLots().isEmpty()) {
                return false;
            }
            core().applySynthetic(index, startingType.complementType(), price, amount, transactionCostModel);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
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
        return core().getClosedPositionsSnapshot();
    }

    @Override
    public Position getCurrentPosition() {
        return core().getCurrentPositionView();
    }

    @Override
    public List<Trade> getTrades() {
        return core().getTradesSnapshot();
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

    private int nextIndex() {
        lock.writeLock().lock();
        try {
            return nextTradeIndex++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<OpenPosition> openPositionsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.openPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    private OpenPosition netOpenPositionSnapshot() {
        lock.readLock().lock();
        try {
            return positionBook.netOpenPosition();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Position> closedPositionsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.closedPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    private Position currentPositionView() {
        lock.readLock().lock();
        try {
            OpenPosition net = positionBook.netOpenPosition();
            if (net == null || net.amount() == null || net.amount().isZero()) {
                return new Position(startingType, transactionCostModel, holdingCostModel);
            }
            int entryIndex = positionBook.openLots().stream().mapToInt(PositionLot::entryIndex).min().orElse(0);
            ExecutionSide entrySide = startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
            Instant entryTime = net.earliestEntryTime() == null ? Instant.EPOCH : net.earliestEntryTime();
            BaseTrade entryTrade = new BaseTrade(entryIndex, entryTime, net.averageEntryPrice(), net.amount(),
                    net.totalFees(), entrySide, null, null);
            return new Position(entryTrade, transactionCostModel, holdingCostModel);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Trade> tradesSnapshot() {
        lock.readLock().lock();
        try {
            if (tradesCache != null && tradesCacheVersion == modificationCount) {
                return tradesCache;
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            if (tradesCache != null && tradesCacheVersion == modificationCount) {
                return tradesCache;
            }
            tradesCache = List.copyOf(buildTrades());
            tradesCacheVersion = modificationCount;
            return tradesCache;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ExecutionSide resolveExecutionSide(ExecutionSide side) {
        if (side != null) {
            return side;
        }
        if (core().getOpenPositionsSnapshot().isEmpty()) {
            return sideOf(startingType);
        }
        return sideOf(startingType.complementType());
    }

    private static ExecutionSide sideOf(TradeType tradeType) {
        if (tradeType == TradeType.BUY) {
            return ExecutionSide.BUY;
        }
        return ExecutionSide.SELL;
    }

    private static Instant resolveExecutionTime(Instant fillTime, Instant fallbackTime) {
        if (fillTime != null) {
            return fillTime;
        }
        if (fallbackTime != null) {
            return fallbackTime;
        }
        return Instant.EPOCH;
    }

    private static String chooseValue(String preferred, String fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback;
    }

    private static void validateFill(Trade trade) {
        Num amount = trade.getAmount();
        if (amount == null || amount.isNaN() || amount.isZero() || amount.isNegative()) {
            throw new IllegalArgumentException("Fill amount must be positive");
        }
        Num price = trade.getPricePerAsset();
        if (price == null || price.isNaN()) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        if (trade.getType() == null) {
            throw new IllegalArgumentException("Fill type must be set");
        }
        Num fee = trade.getCost();
        if (fee != null && fee.isNaN()) {
            throw new IllegalArgumentException("Fill fee must be set");
        }
    }

    private static Num feeOf(Trade trade) {
        Num fee = trade.getCost();
        if (fee != null) {
            return fee;
        }
        return trade.getPricePerAsset().getNumFactory().zero();
    }

    private List<Trade> buildTrades() {
        List<SequencedTrade> trades = new ArrayList<>();
        for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
            trades.add(new SequencedTrade(closed.position().getEntry(), closed.entrySequence()));
            trades.add(new SequencedTrade(closed.position().getExit(), closed.exitSequence()));
        }
        for (PositionLot lot : positionBook.openLots()) {
            ExecutionSide entrySide = startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
            trades.add(new SequencedTrade(new BaseTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(),
                    lot.amount(), lot.fee(), entrySide, lot.orderId(), lot.correlationId()), lot.entrySequence()));
        }
        trades.sort(Comparator.comparingInt((SequencedTrade trade) -> trade.trade().getIndex())
                .thenComparingLong(SequencedTrade::sequence));
        return trades.stream().map(SequencedTrade::trade).toList();
    }

    /**
     * Returns the total execution fees recorded by this record.
     *
     * @return total fees (zero if no fills)
     * @since 0.22.2
     */
    public Num getTotalFees() {
        return core().getTotalFees();
    }

    private Num totalFeesSnapshot() {
        lock.readLock().lock();
        try {
            if (totalFees == null) {
                NumFactory factory = numFactory == null ? DoubleNumFactory.getInstance() : numFactory;
                return factory.zero();
            }
            return totalFees;
        } finally {
            lock.readLock().unlock();
        }
    }

    private TradingRecordCore core() {
        TradingRecordCore coreSnapshot = tradingRecordCore;
        if (coreSnapshot != null) {
            return coreSnapshot;
        }
        lock.writeLock().lock();
        try {
            if (tradingRecordCore == null) {
                tradingRecordCore = new TradingRecordCore(startingType, this::tradesSnapshot,
                        this::closedPositionsSnapshot, this::currentPositionView, this::openPositionsSnapshot,
                        this::netOpenPositionSnapshot, this::totalFeesSnapshot, this::applyTradeInternal,
                        (index, type, price, amount, transactionCostModel) -> applyTradeInternal(index,
                                new BaseTrade(index, Instant.EPOCH, price, amount, null, sideOf(type), null, null),
                                -1L));
            }
            return tradingRecordCore;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("startingType", startingType.name());
        json.addProperty("matchPolicy", matchPolicy.name());
        json.addProperty("startIndex", startIndex);
        json.addProperty("endIndex", endIndex);
        json.addProperty("nextTradeIndex", nextTradeIndex);
        json.addProperty("openPositionCount", core().getOpenPositionsSnapshot().size());
        json.addProperty("closedPositionCount", core().getClosedPositionsSnapshot().size());
        json.addProperty("totalFees", getTotalFees().toString());

        List<Trade> trades = core().getTradesSnapshot();
        json.addProperty("tradeCount", trades.size());
        JsonArray tradesJson = new JsonArray();
        for (Trade trade : trades) {
            try {
                tradesJson.add(JsonParser.parseString(trade.toString()));
            } catch (RuntimeException parseFailure) {
                tradesJson.add(trade.toString());
            }
        }
        json.add("trades", tradesJson);
        return GSON.toJson(json);
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        lock = new ReentrantReadWriteLock();
        rehydrate(transactionCostModel, holdingCostModel);
        tradesCache = null;
        tradesCacheVersion = -1L;
        modificationCount = 0L;
        numFactory = null;
        tradingRecordCore = null;
    }

    private record SequencedTrade(Trade trade, long sequence) {
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * @param holdingCostModel holding cost model, null defaults to
     *                         {@link ZeroCostModel}
     * @since 0.22.2
     */
    public void rehydrate(CostModel holdingCostModel) {
        rehydrate(null, holdingCostModel);
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * <p>
     * Live trading records always use {@link RecordedTradeCostModel} for
     * transaction costs. Holding costs must be provided explicitly (or default to
     * {@link ZeroCostModel}).
     * </p>
     *
     * @param transactionCostModel ignored for live trading records
     * @param holdingCostModel     holding cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @since 0.22.2
     */
    public void rehydrate(CostModel transactionCostModel, CostModel holdingCostModel) {
        CostModel resolvedTransaction = RecordedTradeCostModel.INSTANCE;
        CostModel resolvedHolding = holdingCostModel == null ? new ZeroCostModel() : holdingCostModel;
        this.transactionCostModel = resolvedTransaction;
        this.holdingCostModel = resolvedHolding;
        positionBook.rehydrateCostModels(resolvedTransaction, resolvedHolding);
    }
}
