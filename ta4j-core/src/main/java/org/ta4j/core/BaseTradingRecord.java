/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Unified {@link TradingRecord} implementation used for backtest and live
 * flows.
 *
 * <p>
 * This class combines classic index/price/amount operations with fill-aware
 * {@link #operate(Trade)} support, so a single record type can model both
 * simulated and live execution behavior.
 * </p>
 *
 * @since 0.22.2
 */
public class BaseTradingRecord implements TradingRecord {

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

    /** Constructor with {@link #startingType} = BUY and FIFO matching. */
    public BaseTradingRecord() {
        this(TradeType.BUY);
    }

    /**
     * Constructor with {@link #startingType} = BUY and FIFO matching.
     *
     * @param name record name
     */
    public BaseTradingRecord(String name) {
        this(TradeType.BUY);
        this.name = name;
    }

    /**
     * Constructor.
     *
     * @param name      record name
     * @param tradeType entry trade type
     */
    public BaseTradingRecord(String name, TradeType tradeType) {
        this(tradeType, new ZeroCostModel(), new ZeroCostModel());
        this.name = name;
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType entry trade type
     */
    public BaseTradingRecord(TradeType startingType) {
        this(startingType, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType         entry trade type
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     */
    public BaseTradingRecord(TradeType startingType, CostModel transactionCostModel, CostModel holdingCostModel) {
        this(startingType, ExecutionMatchPolicy.FIFO, transactionCostModel, holdingCostModel, null, null);
    }

    /**
     * Constructor with FIFO matching.
     *
     * @param startingType         entry trade type
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     */
    public BaseTradingRecord(TradeType startingType, Integer startIndex, Integer endIndex,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        this(startingType, ExecutionMatchPolicy.FIFO, transactionCostModel, holdingCostModel, startIndex, endIndex);
    }

    /**
     * Constructor.
     *
     * @param startingType         entry trade type
     * @param matchPolicy          lot matching policy
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @param startIndex           optional start index
     * @param endIndex             optional end index
     */
    public BaseTradingRecord(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
            CostModel holdingCostModel, Integer startIndex, Integer endIndex) {
        Objects.requireNonNull(startingType, "startingType");
        Objects.requireNonNull(matchPolicy, "matchPolicy");
        this.startingType = startingType;
        this.matchPolicy = matchPolicy;
        this.transactionCostModel = defaultCostModel(transactionCostModel);
        this.holdingCostModel = defaultCostModel(holdingCostModel);
        this.positionBook = new PositionBook(startingType, matchPolicy, this.transactionCostModel,
                this.holdingCostModel);
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.nextTradeIndex = 0;
    }

    /**
     * Constructor.
     *
     * @param trades trades to record (must not be empty)
     */
    public BaseTradingRecord(Trade... trades) {
        this(new ZeroCostModel(), new ZeroCostModel(), trades);
    }

    /**
     * Constructor.
     *
     * @param position position to record (entry required)
     * @since 0.22.2
     */
    public BaseTradingRecord(Position position) {
        this(defaultCostModel(position.getTransactionCostModel()), defaultCostModel(position.getHoldingCostModel()),
                positionToTrades(position));
    }

    /**
     * Constructor.
     *
     * @param positions positions to record (must not be empty)
     * @since 0.22.2
     */
    public BaseTradingRecord(List<Position> positions) {
        this(positionsToTrades(positions));
    }

    /**
     * Constructor.
     *
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @param trades               trades to record (must not be empty)
     */
    public BaseTradingRecord(CostModel transactionCostModel, CostModel holdingCostModel, Trade... trades) {
        this(validateTrades(trades), ExecutionMatchPolicy.FIFO, transactionCostModel, holdingCostModel, null, null);
        for (Trade trade : trades) {
            operate(trade);
        }
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
    }

    /**
     * @return lot matching policy
     * @since 0.22.4
     */
    public ExecutionMatchPolicy getMatchPolicy() {
        return matchPolicy;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets record name.
     *
     * @param name name
     * @since 0.22.4
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Records one trade using an auto-incremented index.
     *
     * <p>
     * Use {@link #operate(Trade)} in new code.
     * </p>
     *
     * @param trade trade to record
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordFill(Trade trade) {
        operate(tradeWithAssignedIndex(trade, nextIndex()));
    }

    /**
     * Records one trade using an explicit index.
     *
     * <p>
     * Use {@link #operate(Trade)} in new code.
     * </p>
     *
     * @param index trade index
     * @param trade trade to record
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordFill(int index, Trade trade) {
        operate(tradeWithAssignedIndex(trade, index));
    }

    /**
     * Records one execution fill.
     *
     * <p>
     * Use {@link #operate(TradeFill)} in new code.
     * </p>
     *
     * @param fill execution fill
     * @since 0.22.4
     */
    @Deprecated(since = "0.22.4", forRemoval = true)
    public void recordExecutionFill(TradeFill fill) {
        operate(fill);
    }

    @Override
    public void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(trade.getType(), "trade.type");
        lock.writeLock().lock();
        try {
            List<TradeFill> fills = Trade.executionFillsOf(trade);
            List<PlannedTradeFill> plannedTradeFills = planTradeFills(trade, fills);
            for (PlannedTradeFill plannedTradeFill : plannedTradeFills) {
                applyTradeInternal(plannedTradeFill.index(), plannedTradeFill.trade(), -1L);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            TradeType tradeType = positionBook.hasOpenLots() ? startingType.complementType() : startingType;
            applySyntheticInternal(index, tradeType, price, amount, transactionCostModel);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (positionBook.hasOpenLots()) {
                return false;
            }
            applySyntheticInternal(index, startingType, price, amount, transactionCostModel);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (!positionBook.hasOpenLots()) {
                return false;
            }
            applySyntheticInternal(index, startingType.complementType(), price, amount, transactionCostModel);
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
        return closedPositionsSnapshot();
    }

    @Override
    public Position getCurrentPosition() {
        return currentPositionView();
    }

    @Override
    public List<Trade> getTrades() {
        return tradesSnapshot();
    }

    @Override
    public Trade getLastEntry() {
        return lastRecordedEntrySnapshot();
    }

    @Override
    public Trade getLastExit() {
        return lastRecordedExitSnapshot();
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
     * Returns open positions.
     *
     * @return open positions
     * @since 0.22.4
     */
    @Override
    public List<Position> getOpenPositions() {
        return openPositionsSnapshot();
    }

    /**
     * Returns the aggregated net open position.
     *
     * @return net open position, or {@code null} when no lots are open
     * @since 0.22.4
     */
    @Override
    @Deprecated(since = "0.22.4")
    public Position getNetOpenPosition() {
        return netOpenPositionSnapshot();
    }

    /**
     * @return summed execution costs/fees across all recorded trades
     * @since 0.22.4
     */
    public Num getTotalFees() {
        return totalFeesSnapshot();
    }

    @Override
    public Num getRecordedTotalFees() {
        return getTotalFees();
    }

    DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(startingType, tradesSnapshot(), closedPositionsSnapshot(), currentPositionView(),
                openPositionsSnapshot(), netOpenPositionSnapshot(), totalFeesSnapshot());
    }

    private int nextIndex() {
        lock.writeLock().lock();
        try {
            return nextTradeIndex++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<PlannedTradeFill> planTradeFills(Trade trade, List<TradeFill> fills) {
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("trade must expose at least one fill");
        }
        TradeType tradeType = trade.getType();
        ExecutionSide tradeSide = sideOf(tradeType);
        ExecutionSide openSide = currentOpenSide();
        Position netOpenPosition = positionBook.netOpenPosition();
        int plannedNextIndex = nextTradeIndex;
        Num totalAmount = fills.getFirst().price().getNumFactory().zero();
        List<PlannedTradeFill> plannedTradeFills = new ArrayList<>(fills.size());
        for (TradeFill fill : fills) {
            PlannedTradeFill plannedTradeFill = planTradeFill(tradeType, tradeSide, fill, trade.getOrderId(),
                    trade.getCorrelationId(), trade.getTime(), plannedNextIndex);
            plannedTradeFills.add(plannedTradeFill);
            plannedNextIndex = Math.max(plannedNextIndex, plannedTradeFill.index() + 1);
            totalAmount = totalAmount.plus(plannedTradeFill.trade().getAmount());
        }
        if (openSide != null && tradeSide != openSide && netOpenPosition != null
                && totalAmount.isGreaterThan(netOpenPosition.getEntry().getAmount())) {
            throw new IllegalArgumentException("Exit amount " + totalAmount + " exceeds open position amount "
                    + netOpenPosition.getEntry().getAmount());
        }
        return List.copyOf(plannedTradeFills);
    }

    private PlannedTradeFill planTradeFill(TradeType tradeType, ExecutionSide tradeSide, TradeFill fill,
            String tradeOrderId, String tradeCorrelationId, Instant tradeTime, int plannedNextIndex) {
        if (fill.side() != null && fill.side() != tradeSide) {
            throw new IllegalArgumentException("Fill side " + fill.side() + " does not match trade type " + tradeType);
        }
        if (fill.price() == null || fill.price().isNaN()) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        int resolvedIndex = fill.index() >= 0 ? fill.index() : plannedNextIndex;
        Instant executionTime = resolveExecutionTime(fill.time(), tradeTime);
        String orderId = chooseValue(fill.orderId(), tradeOrderId);
        String correlationId = chooseValue(fill.correlationId(), tradeCorrelationId);
        Num normalizedAmount = normalizeRecordedAmount(fill.amount(), fill.price());
        Num normalizedFee = normalizeFee(fill.fee(), fill.price());
        Trade plannedTrade = recordedTrade(resolvedIndex, executionTime, fill.price(), normalizedAmount, normalizedFee,
                tradeSide, orderId, correlationId);
        validateFill(plannedTrade);
        return new PlannedTradeFill(resolvedIndex, plannedTrade);
    }

    private Trade tradeWithAssignedIndex(Trade trade, int index) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = Trade.executionFillsOf(trade);
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("trade must expose at least one fill");
        }
        int firstIndex = fills.getFirst().index() >= 0 ? fills.getFirst().index() : 0;
        List<TradeFill> indexedFills = new ArrayList<>(fills.size());
        for (int i = 0; i < fills.size(); i++) {
            TradeFill fill = fills.get(i);
            int assignedIndex = fill.index() >= 0 ? index + (fill.index() - firstIndex) : index + i;
            indexedFills.add(new TradeFill(assignedIndex, fill.time(), fill.price(), fill.amount(), fill.fee(),
                    fill.side(), chooseValue(fill.orderId(), trade.getOrderId()),
                    chooseValue(fill.correlationId(), trade.getCorrelationId())));
        }
        if (indexedFills.size() == 1) {
            TradeFill fill = indexedFills.getFirst();
            if (fill.price() == null || fill.price().isNaN()) {
                throw new IllegalArgumentException("Fill price must be set");
            }
            return recordedTrade(fill.index(), resolveExecutionTime(fill.time(), trade.getTime()), fill.price(),
                    fill.amount(), normalizeFee(fill.fee(), fill.price()),
                    fill.side() == null ? sideOf(trade.getType()) : fill.side(), fill.orderId(), fill.correlationId());
        }
        return Trade.fromFills(trade.getType(), indexedFills, trade.getCostModel());
    }

    private void applyTradeInternal(int index, Trade trade, long sequence) {
        Objects.requireNonNull(trade, "trade");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        validateFill(trade);
        Num fee = feeOf(trade);
        Num price = trade.getPricePerAsset();
        lock.writeLock().lock();
        try {
            nextTradeIndex = Math.max(nextTradeIndex, index + 1);
            long appliedSequence = sequence >= 0 ? sequence : nextSequence++;
            if (appliedSequence >= nextSequence) {
                nextSequence = appliedSequence + 1;
            }

            ExecutionSide tradeSide = sideOf(trade.getType());
            ExecutionSide openSide = currentOpenSide();
            if (openSide == null || tradeSide == openSide) {
                positionBook.recordEntry(index, trade, appliedSequence);
            } else {
                positionBook.recordExit(index, trade, appliedSequence);
            }

            if ((numFactory == null || numFactory.one().isNaN()) && price != null && !price.isNaN()) {
                numFactory = price.getNumFactory();
            }
            if (totalFees == null) {
                totalFees = resolveNumFactory(price).zero();
            }
            totalFees = totalFees.plus(fee);
            modificationCount++;
            tradesCache = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void applySyntheticInternal(int index, TradeType type, Num price, Num amount,
            CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        Num normalizedAmount = normalizeSyntheticAmount(amount, price);
        applyTradeInternal(index, new BaseTrade(index, type, price, normalizedAmount, transactionCostModel), -1L);
    }

    private ExecutionSide currentOpenSide() {
        Position net = positionBook.netOpenPosition();
        if (net == null || !net.isOpened()) {
            return null;
        }
        return sideOf(net.getEntry().getType());
    }

    private List<Position> openPositionsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(positionBook.openPositions());
        } finally {
            lock.readLock().unlock();
        }
    }

    private Position netOpenPositionSnapshot() {
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
            Position net = positionBook.netOpenPosition();
            if (net == null || !net.isOpened()) {
                return new Position(startingType, transactionCostModel, holdingCostModel);
            }
            return net;
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

    private List<Trade> buildTrades() {
        List<SequencedTrade> trades = new ArrayList<>();
        for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
            trades.add(new SequencedTrade(closed.position().getEntry(), closed.entrySequence()));
            trades.add(new SequencedTrade(closed.position().getExit(), closed.exitSequence()));
        }
        for (SequencedTrade openEntry : positionBook.openEntryTradesWithSequence()) {
            trades.add(openEntry);
        }
        trades.sort(Comparator.comparingInt((SequencedTrade trade) -> trade.trade().getIndex())
                .thenComparingLong(SequencedTrade::sequence));
        return trades.stream().map(SequencedTrade::trade).toList();
    }

    private Trade lastRecordedEntrySnapshot() {
        lock.readLock().lock();
        try {
            SequencedTrade candidate = null;
            for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
                candidate = newerTrade(candidate, closed.position().getEntry(), closed.entrySequence());
            }
            for (SequencedTrade openEntry : positionBook.openEntryTradesWithSequence()) {
                candidate = newerTrade(candidate, openEntry.trade(), openEntry.sequence());
            }
            return candidate == null ? null : candidate.trade();
        } finally {
            lock.readLock().unlock();
        }
    }

    private Trade lastRecordedExitSnapshot() {
        lock.readLock().lock();
        try {
            SequencedTrade candidate = null;
            for (PositionBook.ClosedPosition closed : positionBook.closedPositionsWithSequence()) {
                candidate = newerTrade(candidate, closed.position().getExit(), closed.exitSequence());
            }
            return candidate == null ? null : candidate.trade();
        } finally {
            lock.readLock().unlock();
        }
    }

    private static SequencedTrade newerTrade(SequencedTrade current, Trade trade, long sequence) {
        if (trade == null) {
            return current;
        }
        if (current == null || sequence > current.sequence()) {
            return new SequencedTrade(trade, sequence);
        }
        return current;
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

    @Override
    public String toString() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("startingType", startingType.name());
        json.addProperty("matchPolicy", matchPolicy.name());
        json.addProperty("startIndex", startIndex);
        json.addProperty("endIndex", endIndex);
        json.addProperty("nextTradeIndex", nextTradeIndex);
        json.addProperty("openPositionCount", openPositionsSnapshot().size());
        json.addProperty("closedPositionCount", closedPositionsSnapshot().size());
        json.addProperty("totalFees", getTotalFees().toString());

        List<Trade> trades = tradesSnapshot();
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
        transactionCostModel = defaultCostModel(transactionCostModel);
        holdingCostModel = defaultCostModel(holdingCostModel);
        positionBook.rehydrateCostModels(transactionCostModel, holdingCostModel);
        tradesCache = null;
        tradesCacheVersion = -1L;
        modificationCount = 0L;
        numFactory = null;
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * @param holdingCostModel holding cost model, null defaults to
     *                         {@link ZeroCostModel}
     * @since 0.22.4
     */
    public void rehydrate(CostModel holdingCostModel) {
        rehydrate(transactionCostModel, holdingCostModel);
    }

    /**
     * Rehydrates transient cost models after deserialization.
     *
     * @param transactionCostModel transaction cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @param holdingCostModel     holding cost model, null defaults to
     *                             {@link ZeroCostModel}
     * @since 0.22.4
     */
    public void rehydrate(CostModel transactionCostModel, CostModel holdingCostModel) {
        CostModel resolvedTransaction = defaultCostModel(transactionCostModel);
        CostModel resolvedHolding = defaultCostModel(holdingCostModel);
        this.transactionCostModel = resolvedTransaction;
        this.holdingCostModel = resolvedHolding;
        positionBook.rehydrateCostModels(resolvedTransaction, resolvedHolding);
    }

    private static Instant resolveExecutionTime(Instant fillTime, Instant fallbackTime) {
        if (fillTime != null) {
            return fillTime;
        }
        return fallbackTime;
    }

    private static Trade recordedTrade(int index, Instant time, Num pricePerAsset, Num amount, Num fee,
            ExecutionSide side, String orderId, String correlationId) {
        Num normalizedFee = fee == null ? pricePerAsset.getNumFactory().zero() : fee;
        if (time != null) {
            return new BaseTrade(index, time, pricePerAsset, amount, normalizedFee, side, orderId, correlationId);
        }
        if (pricePerAsset.isNaN()) {
            return new BaseTrade(index, side.toTradeType(), pricePerAsset, amount, RecordedTradeCostModel.INSTANCE);
        }
        return Trade.fromFills(side.toTradeType(),
                List.of(new TradeFill(index, null, pricePerAsset, amount, normalizedFee, side, orderId, correlationId)),
                RecordedTradeCostModel.INSTANCE);
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
        if (price == null) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        if (trade.getType() == null) {
            throw new IllegalArgumentException("Fill type must be set");
        }
    }

    private Num normalizeRecordedAmount(Num amount, Num reference) {
        if (amount != null && !amount.isNaN()) {
            if (amount.isNegative()) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        }
        return resolveNumFactory(reference).one();
    }

    private Num normalizeSyntheticAmount(Num amount, Num reference) {
        if (amount != null && !amount.isNaN()) {
            // Synthetic enter/exit APIs carry direction in the trade type, so signed
            // quantities are normalized to their absolute magnitudes.
            return amount.isNegative() ? amount.abs() : amount;
        }
        return resolveNumFactory(reference).one();
    }

    private Num normalizeFee(Num fee, Num reference) {
        if (fee != null && !fee.isNaN()) {
            return fee;
        }
        return resolveNumFactory(reference).zero();
    }

    private NumFactory resolveNumFactory(Num reference) {
        if (reference != null && !reference.isNaN()) {
            return reference.getNumFactory();
        }
        if (numFactory != null) {
            return numFactory;
        }
        return DoubleNumFactory.getInstance();
    }

    private static Num feeOf(Trade trade) {
        Num fee = trade.getCost();
        if (fee != null && !fee.isNaN()) {
            return fee;
        }
        return trade.getPricePerAsset().getNumFactory().zero();
    }

    private static ExecutionSide sideOf(TradeType tradeType) {
        if (tradeType == TradeType.BUY) {
            return ExecutionSide.BUY;
        }
        return ExecutionSide.SELL;
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }

    private static TradeType validateTrades(Trade... trades) {
        if (trades == null || trades.length == 0) {
            throw new IllegalArgumentException("At least one trade is required");
        }
        Objects.requireNonNull(trades[0], "trade[0]");
        Objects.requireNonNull(trades[0].getType(), "trade[0].type");
        return trades[0].getType();
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

    /**
     * Internal lot-matching ledger for fill-aware trading records.
     */
    private static final class PositionBook implements Serializable {

        @Serial
        private static final long serialVersionUID = -6897162194206253952L;

        private final TradeType startingType;
        private final ExecutionMatchPolicy matchPolicy;
        private transient CostModel transactionCostModel;
        private transient CostModel holdingCostModel;
        private final Deque<PositionLot> openLots;
        private final List<ClosedPosition> closedPositions;

        private PositionBook(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
                CostModel holdingCostModel) {
            Objects.requireNonNull(startingType, "startingType");
            Objects.requireNonNull(matchPolicy, "matchPolicy");
            Objects.requireNonNull(transactionCostModel, "transactionCostModel");
            Objects.requireNonNull(holdingCostModel, "holdingCostModel");
            this.startingType = startingType;
            this.matchPolicy = matchPolicy;
            this.transactionCostModel = transactionCostModel;
            this.holdingCostModel = holdingCostModel;
            this.openLots = new ArrayDeque<>();
            this.closedPositions = new ArrayList<>();
        }

        private void recordEntry(int index, Trade trade, long sequence) {
            if (trade == null) {
                throw new IllegalArgumentException("trade must not be null");
            }
            if (trade.getAmount() == null || !trade.getAmount().isPositive()) {
                throw new IllegalArgumentException("trade amount must be positive");
            }
            if (trade.getPricePerAsset() == null) {
                throw new IllegalArgumentException("trade price must be set");
            }
            if (trade.getType() == null) {
                throw new IllegalArgumentException("trade type must be set");
            }
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
                normalizeAvgCostLots();
            }
            PositionLot lot = new PositionLot(index, timeOf(trade), trade.getPricePerAsset(), sideOf(trade.getType()),
                    trade.getAmount(), feeOf(trade), trade.getOrderId(), trade.getCorrelationId(), sequence);
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST && !openLots.isEmpty()) {
                PositionLot merged = openLots.removeFirst().merge(lot);
                openLots.addFirst(merged);
                return;
            }
            openLots.addLast(lot);
        }

        private List<Position> recordExit(int index, Trade trade, long sequence) {
            if (trade == null) {
                throw new IllegalArgumentException("trade must not be null");
            }
            if (trade.getAmount() == null || !trade.getAmount().isPositive()) {
                throw new IllegalArgumentException("trade amount must be positive");
            }
            if (trade.getPricePerAsset() == null) {
                throw new IllegalArgumentException("trade price must be set");
            }
            if (trade.getType() == null) {
                throw new IllegalArgumentException("trade type must be set");
            }
            if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
                normalizeAvgCostLots();
            }
            Num remaining = trade.getAmount();
            Num remainingFee = feeOf(trade);
            List<Position> closed = new ArrayList<>();
            while (remaining.isPositive()) {
                PositionLot lot = nextLot(trade);
                if (lot == null) {
                    throw new IllegalStateException("No open lots to close");
                }
                Num lotAmount = lot.amount();
                if (matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID && remaining.isGreaterThan(lotAmount)) {
                    throw new IllegalStateException("Exit amount exceeds matched lot amount");
                }
                Num closeAmount = remaining.isGreaterThan(lotAmount) ? lotAmount : remaining;
                Num exitFeePortion = remainingFee.isZero() ? remainingFee
                        : remainingFee.multipliedBy(closeAmount).dividedBy(remaining);
                ClosedPosition closedPosition = closeLot(lot, trade, index, closeAmount, exitFeePortion, sequence);
                closed.add(closedPosition.position());
                closedPositions.add(closedPosition);
                remaining = remaining.minus(closeAmount);
                remainingFee = remainingFee.minus(exitFeePortion);
            }
            return List.copyOf(closed);
        }

        private boolean hasOpenLots() {
            return !openLots.isEmpty();
        }

        private List<SequencedTrade> openEntryTradesWithSequence() {
            List<SequencedTrade> trades = new ArrayList<>(openLots.size());
            for (PositionLot lot : openLots) {
                Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), lot.side(), lot.orderId(), lot.correlationId());
                trades.add(new SequencedTrade(entry, lot.entrySequence()));
            }
            return List.copyOf(trades);
        }

        private List<Position> closedPositions() {
            return closedPositions.stream().map(ClosedPosition::position).toList();
        }

        private List<Position> openPositions() {
            List<Position> positions = new ArrayList<>();
            for (PositionLot lot : openLots) {
                Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), lot.side(), lot.orderId(), lot.correlationId());
                positions.add(new Position(entry, RecordedTradeCostModel.INSTANCE, holdingCostModel));
            }
            return positions;
        }

        private Position netOpenPosition() {
            if (openLots.isEmpty()) {
                return null;
            }
            Num totalAmount = null;
            Num totalCost = null;
            Num totalFees = null;
            Instant earliest = null;
            boolean hasUnknownEntryTime = false;
            ExecutionSide side = null;
            int entryIndex = Integer.MAX_VALUE;
            for (PositionLot lot : openLots) {
                Num lotCost = lot.entryPrice().multipliedBy(lot.amount());
                totalAmount = totalAmount == null ? lot.amount() : totalAmount.plus(lot.amount());
                totalCost = totalCost == null ? lotCost : totalCost.plus(lotCost);
                totalFees = totalFees == null ? lot.fee() : totalFees.plus(lot.fee());
                if (lot.entryTime() == null) {
                    hasUnknownEntryTime = true;
                } else if (!hasUnknownEntryTime && (earliest == null || lot.entryTime().isBefore(earliest))) {
                    earliest = lot.entryTime();
                }
                entryIndex = Math.min(entryIndex, lot.entryIndex());
                if (side == null) {
                    side = lot.side();
                } else if (side != lot.side()) {
                    throw new IllegalStateException("Open lots contain mixed entry sides");
                }
            }
            Num average = totalAmount == null || totalAmount.isZero() ? totalCost : totalCost.dividedBy(totalAmount);
            Num fee = totalFees == null ? totalCost.getNumFactory().zero() : totalFees;
            Trade entry = recordedTrade(entryIndex == Integer.MAX_VALUE ? 0 : entryIndex,
                    hasUnknownEntryTime ? null : earliest, average, totalAmount, fee, side, null, null);
            return new Position(entry, RecordedTradeCostModel.INSTANCE, holdingCostModel);
        }

        private PositionLot nextLot(Trade trade) {
            if (openLots.isEmpty()) {
                return null;
            }
            if (matchPolicy == ExecutionMatchPolicy.LIFO) {
                return openLots.peekLast();
            }
            if (matchPolicy == ExecutionMatchPolicy.SPECIFIC_ID) {
                return matchSpecificLot(trade);
            }
            return openLots.peekFirst();
        }

        private PositionLot matchSpecificLot(Trade trade) {
            String correlationId = trade.getCorrelationId();
            String key = correlationId != null && !correlationId.isBlank() ? correlationId : trade.getOrderId();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Specific-id matching requires correlationId or orderId");
            }
            for (PositionLot lot : openLots) {
                if (key.equals(lot.correlationId()) || key.equals(lot.orderId())) {
                    return lot;
                }
            }
            throw new IllegalStateException("No open lot matches " + key);
        }

        private void normalizeAvgCostLots() {
            if (openLots.size() <= 1) {
                return;
            }
            PositionLot merged = null;
            while (!openLots.isEmpty()) {
                PositionLot lot = openLots.removeFirst();
                merged = merged == null ? lot : merged.merge(lot);
            }
            if (merged != null) {
                openLots.addFirst(merged);
            }
        }

        private ClosedPosition closeLot(PositionLot lot, Trade trade, int index, Num closeAmount, Num exitFeePortion,
                long exitSequence) {
            Num lotAmount = lot.amount();
            Num entryFeePortion = lot.fee().isZero() ? lot.fee()
                    : lot.fee().multipliedBy(closeAmount).dividedBy(lotAmount);
            if (closeAmount.isEqual(lotAmount)) {
                openLots.remove(lot);
            } else {
                lot.reduce(closeAmount, entryFeePortion);
            }
            Trade entry = recordedTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), closeAmount,
                    entryFeePortion, lot.side(), lot.orderId(), lot.correlationId());
            Trade exit = recordedTrade(index, timeOf(trade), trade.getPricePerAsset(), closeAmount, exitFeePortion,
                    sideOf(trade.getType()), trade.getOrderId(), trade.getCorrelationId());
            return new ClosedPosition(new Position(entry, exit, RecordedTradeCostModel.INSTANCE, holdingCostModel),
                    lot.entrySequence(), exitSequence);
        }

        private static Num feeOf(Trade trade) {
            Num cost = trade.getCost();
            if (cost != null && !cost.isNaN()) {
                return cost;
            }
            Num price = trade.getPricePerAsset();
            if (price != null && !price.isNaN()) {
                return price.getNumFactory().zero();
            }
            Num amount = trade.getAmount();
            if (amount != null && !amount.isNaN()) {
                return amount.getNumFactory().zero();
            }
            return DoubleNumFactory.getInstance().zero();
        }

        private static Instant timeOf(Trade trade) {
            return trade.getTime();
        }

        private static ExecutionSide sideOf(TradeType tradeType) {
            Objects.requireNonNull(tradeType, "tradeType");
            if (tradeType == TradeType.BUY) {
                return ExecutionSide.BUY;
            }
            if (tradeType == TradeType.SELL) {
                return ExecutionSide.SELL;
            }
            throw new IllegalArgumentException("Unsupported trade type: " + tradeType);
        }

        private List<ClosedPosition> closedPositionsWithSequence() {
            return List.copyOf(closedPositions);
        }

        private void rehydrateCostModels(CostModel transactionCostModel, CostModel holdingCostModel) {
            this.transactionCostModel = transactionCostModel == null ? RecordedTradeCostModel.INSTANCE
                    : transactionCostModel;
            this.holdingCostModel = holdingCostModel == null ? new ZeroCostModel() : holdingCostModel;
            for (int i = 0; i < closedPositions.size(); i++) {
                ClosedPosition closed = closedPositions.get(i);
                Position position = closed.position();
                Position rehydrated = rehydratePosition(position, this.transactionCostModel, this.holdingCostModel);
                closedPositions.set(i, new ClosedPosition(rehydrated, closed.entrySequence(), closed.exitSequence()));
            }
        }

        private record ClosedPosition(Position position, long entrySequence,
                long exitSequence) implements Serializable {
        }

        private static final class PositionLot implements Serializable {

            @Serial
            private static final long serialVersionUID = -2333496329345071348L;

            private final int entryIndex;
            private final long entrySequence;
            private final Instant entryTime;
            private final Num entryPrice;
            private final ExecutionSide side;
            private Num amount;
            private Num fee;
            private final String orderId;
            private final String correlationId;

            private PositionLot(int entryIndex, Instant entryTime, Num entryPrice, ExecutionSide side, Num amount,
                    Num fee, String orderId, String correlationId, long entrySequence) {
                Objects.requireNonNull(entryPrice, "entryPrice");
                Objects.requireNonNull(side, "side");
                Objects.requireNonNull(amount, "amount");
                Objects.requireNonNull(fee, "fee");
                this.entryIndex = entryIndex;
                this.entrySequence = entrySequence;
                this.entryTime = entryTime;
                this.entryPrice = entryPrice;
                this.side = side;
                this.amount = amount;
                this.fee = fee;
                this.orderId = orderId;
                this.correlationId = correlationId;
            }

            private int entryIndex() {
                return entryIndex;
            }

            private long entrySequence() {
                return entrySequence;
            }

            private Instant entryTime() {
                return entryTime;
            }

            private Num entryPrice() {
                return entryPrice;
            }

            private ExecutionSide side() {
                return side;
            }

            private Num amount() {
                return amount;
            }

            private Num fee() {
                return fee;
            }

            private String orderId() {
                return orderId;
            }

            private String correlationId() {
                return correlationId;
            }

            private PositionLot reduce(Num reduceAmount, Num reduceFee) {
                amount = amount.minus(reduceAmount);
                fee = fee.minus(reduceFee);
                return this;
            }

            private PositionLot merge(PositionLot other) {
                if (side != other.side) {
                    throw new IllegalArgumentException("cannot merge lots with different sides");
                }
                Num totalAmount = amount.plus(other.amount);
                Num totalCost = entryPrice.multipliedBy(amount).plus(other.entryPrice.multipliedBy(other.amount));
                Num mergedPrice = totalCost.dividedBy(totalAmount);
                Num mergedFee = fee.plus(other.fee);
                int mergedIndex = Math.min(entryIndex, other.entryIndex);
                Instant mergedTime;
                if (entryTime == null || other.entryTime == null) {
                    mergedTime = null;
                } else {
                    mergedTime = entryTime.isBefore(other.entryTime) ? entryTime : other.entryTime;
                }
                long mergedSequence = Math.min(entrySequence, other.entrySequence);
                return new PositionLot(mergedIndex, mergedTime, mergedPrice, side, totalAmount, mergedFee, null, null,
                        mergedSequence);
            }

            @Serial
            private Object readResolve() throws InvalidObjectException {
                if (side == null) {
                    throw new InvalidObjectException("PositionLot.side is required");
                }
                if (entryPrice == null) {
                    throw new InvalidObjectException("PositionLot.entryPrice is required");
                }
                if (amount == null) {
                    throw new InvalidObjectException("PositionLot.amount is required");
                }
                if (fee == null) {
                    throw new InvalidObjectException("PositionLot.fee is required");
                }
                return this;
            }

            @Override
            public String toString() {
                JsonObject json = new JsonObject();
                json.addProperty("entryIndex", entryIndex);
                json.addProperty("entrySequence", entrySequence);
                json.addProperty("entryTime", entryTime == null ? null : entryTime.toString());
                json.addProperty("entryPrice", entryPrice.toString());
                json.addProperty("side", side.name());
                json.addProperty("amount", amount.toString());
                json.addProperty("fee", fee.toString());
                json.addProperty("orderId", orderId);
                json.addProperty("correlationId", correlationId);
                return GSON.toJson(json);
            }
        }

        @Override
        public String toString() {
            JsonObject json = new JsonObject();
            json.addProperty("startingType", startingType == null ? null : startingType.name());
            json.addProperty("matchPolicy", matchPolicy == null ? null : matchPolicy.name());
            json.addProperty("openLotCount", openLots.size());
            json.addProperty("closedPositionCount", closedPositions.size());

            JsonArray openLotsJson = new JsonArray();
            for (PositionLot lot : openLots) {
                openLotsJson.add(JsonParser.parseString(lot.toString()));
            }
            json.add("openLots", openLotsJson);
            return GSON.toJson(json);
        }

        @Serial
        private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
            inputStream.defaultReadObject();
            if (transactionCostModel == null) {
                transactionCostModel = RecordedTradeCostModel.INSTANCE;
            }
            if (holdingCostModel == null) {
                holdingCostModel = new ZeroCostModel();
            }
        }

        private static Position rehydratePosition(Position position, CostModel transactionCostModel,
                CostModel holdingCostModel) {
            if (position == null || position.getEntry() == null) {
                return position;
            }
            if (position.getExit() == null) {
                return new Position(position.getEntry(), transactionCostModel, holdingCostModel);
            }
            return new Position(position.getEntry(), position.getExit(), transactionCostModel, holdingCostModel);
        }
    }

    private record SequencedTrade(Trade trade, long sequence) {
    }

    private record PlannedTradeFill(int index, Trade trade) {
    }

    static record DebugSnapshot(TradeType startingType, List<Trade> trades, List<Position> closedPositions,
            Position currentPosition, List<Position> openPositions, Position netOpenPosition, Num totalFees) {

        DebugSnapshot {
            Objects.requireNonNull(startingType, "startingType");
            Objects.requireNonNull(totalFees, "totalFees");
            trades = trades == null ? List.of() : List.copyOf(trades);
            closedPositions = closedPositions == null ? List.of() : List.copyOf(closedPositions);
            openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
        }
    }
}
