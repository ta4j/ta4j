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
     * {@link #recordFill(int, LiveTrade)} when you need to preserve a specific
     * index.
     * </p>
     *
     * @param trade live trade
     * @throws IllegalArgumentException when trade price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordFill(LiveTrade trade) {
        recordFill(nextIndex(), trade);
    }

    /**
     * Records a live fill using an {@link ExecutionFill} contract.
     *
     * <p>
     * If {@link ExecutionFill#index()} is non-negative, that index is used.
     * Otherwise, the record assigns the next auto-incremented index.
     * </p>
     *
     * @param fill execution fill
     * @throws IllegalArgumentException when fill price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordExecutionFill(ExecutionFill fill) {
        Objects.requireNonNull(fill, "fill");
        int index = fill.index();
        LiveTrade trade;
        if (fill instanceof LiveTrade liveTrade) {
            trade = liveTrade;
        } else {
            trade = new LiveTrade(index >= 0 ? index : 0, fill.time(), fill.price(), fill.amount(), fill.fee(),
                    fill.side(), fill.orderId(), fill.correlationId());
        }
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
    public void recordFill(int index, LiveTrade trade) {
        Objects.requireNonNull(trade, "trade");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        LiveTrade resolved = trade.index() == index ? trade : trade.withIndex(index);
        validateFill(resolved);
        lock.writeLock().lock();
        try {
            nextTradeIndex = Math.max(nextTradeIndex, index + 1);
            long sequence = nextSequence++;
            if (resolved.side().toTradeType() == startingType) {
                positionBook.recordEntry(index, resolved, sequence);
            } else {
                positionBook.recordExit(index, resolved, sequence);
            }
            if (numFactory == null) {
                numFactory = resolved.price().getNumFactory();
            }
            if (totalFees == null) {
                totalFees = numFactory.zero();
            }
            totalFees = totalFees.plus(resolved.fee());
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
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.openPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the aggregated net open position.
     *
     * @return net open position, or null if none
     * @since 0.22.2
     */
    @Override
    public OpenPosition getNetOpenPosition() {
        lock.readLock().lock();
        try {
            return positionBook.netOpenPosition();
        } finally {
            lock.readLock().unlock();
        }
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
     * @since 0.22.3
     */
    @Override
    public void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = trade.getFills();
        if (fills.isEmpty()) {
            recordTradeFill(trade.getType(), trade.getIndex(), trade.getPricePerAsset(), trade.getAmount(),
                    trade.getOrderId(), trade.getCorrelationId(), trade.getTime());
            return;
        }
        for (TradeFill fill : fills) {
            recordTradeFill(trade.getType(), fill.index(), fill.price(), fill.amount(), trade.getOrderId(),
                    trade.getCorrelationId(), trade.getTime());
        }
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            ExecutionSide side = positionBook.openLots().isEmpty()
                    ? (startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL)
                    : (startingType == TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY);
            recordFill(index, new LiveTrade(index, Instant.EPOCH, price, amount, null, side, null, null));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void recordTradeFill(TradeType tradeType, int index, Num price, Num amount, String orderId,
            String correlationId, Instant tradeTime) {
        ExecutionSide side = tradeType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        Instant executionTime = tradeTime == null ? Instant.EPOCH : tradeTime;
        recordFill(index, new LiveTrade(index, executionTime, price, amount, null, side, orderId, correlationId));
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (!positionBook.openLots().isEmpty()) {
                return false;
            }
            recordFill(index, new LiveTrade(index, Instant.EPOCH, price, amount, null,
                    startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL, null, null));
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
            recordFill(index, new LiveTrade(index, Instant.EPOCH, price, amount, null,
                    startingType == TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY, null, null));
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
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.closedPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Position getCurrentPosition() {
        lock.readLock().lock();
        try {
            OpenPosition net = positionBook.netOpenPosition();
            if (net == null || net.amount() == null || net.amount().isZero()) {
                return new Position(startingType, transactionCostModel, holdingCostModel);
            }
            int entryIndex = positionBook.openLots().stream().mapToInt(PositionLot::entryIndex).min().orElse(0);
            ExecutionSide entrySide = startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
            Instant entryTime = net.earliestEntryTime() == null ? Instant.EPOCH : net.earliestEntryTime();
            LiveTrade entryTrade = new LiveTrade(entryIndex, entryTime, net.averageEntryPrice(), net.amount(),
                    net.totalFees(), entrySide, null, null);
            return new Position(entryTrade, transactionCostModel, holdingCostModel);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Trade> getTrades() {
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

    @Override
    public Trade getLastTrade() {
        List<Trade> trades = getTrades();
        return trades.isEmpty() ? null : trades.getLast();
    }

    @Override
    public Trade getLastTrade(TradeType tradeType) {
        List<Trade> trades = getTrades();
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            if (trade.getType() == tradeType) {
                return trade;
            }
        }
        return null;
    }

    @Override
    public Trade getLastEntry() {
        return getLastTrade(startingType);
    }

    @Override
    public Trade getLastExit() {
        return getLastTrade(startingType.complementType());
    }

    @Override
    public Integer getStartIndex() {
        return startIndex;
    }

    @Override
    public Integer getEndIndex() {
        return endIndex;
    }

    private int nextIndex() {
        lock.writeLock().lock();
        try {
            return nextTradeIndex++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static void validateFill(LiveTrade trade) {
        if (trade.amount().isNaN() || trade.amount().isZero() || trade.amount().isNegative()) {
            throw new IllegalArgumentException("Fill amount must be positive");
        }
        if (trade.price().isNaN()) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        if (trade.fee().isNaN()) {
            throw new IllegalArgumentException("Fill fee must be set");
        }
    }

    private List<Trade> buildTrades() {
        List<SequencedTrade> trades = new ArrayList<>();
        for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
            trades.add(new SequencedTrade(closed.position().getEntry(), closed.entrySequence()));
            trades.add(new SequencedTrade(closed.position().getExit(), closed.exitSequence()));
        }
        for (PositionLot lot : positionBook.openLots()) {
            ExecutionSide entrySide = startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
            trades.add(new SequencedTrade(new LiveTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(),
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

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("startingType", startingType.name());
            json.addProperty("matchPolicy", matchPolicy.name());
            json.addProperty("startIndex", startIndex);
            json.addProperty("endIndex", endIndex);
            json.addProperty("nextTradeIndex", nextTradeIndex);
            json.addProperty("openPositionCount", positionBook.openPositions().size());
            json.addProperty("closedPositionCount", positionBook.closedPositions().size());
            json.addProperty("totalFees", getTotalFees().toString());

            List<Trade> trades = buildTrades();
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
        } finally {
            lock.readLock().unlock();
        }
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
