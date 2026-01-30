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

import com.google.gson.Gson;
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
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Live trading record that supports partial fills and multi-lot positions.
 *
 * @since 0.22.2
 */
public class LiveTradingRecord implements TradingRecord {

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
     * @param transactionCostModel transaction cost model
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
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
        this.positionBook = new PositionBook(startingType, matchPolicy, transactionCostModel, holdingCostModel);
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.nextTradeIndex = 0;
    }

    /**
     * Records a live fill using an auto-incremented trade index.
     *
     * @param fill execution fill
     * @throws IllegalArgumentException when fill price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordFill(ExecutionFill fill) {
        recordFill(nextIndex(), fill);
    }

    /**
     * Records a live fill using the provided trade index.
     *
     * @param index trade index
     * @param fill  execution fill
     * @throws IllegalArgumentException when fill price or amount is NaN/invalid
     * @since 0.22.2
     */
    public void recordFill(int index, ExecutionFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        validateFill(fill);
        lock.writeLock().lock();
        try {
            nextTradeIndex = Math.max(nextTradeIndex, index + 1);
            if (fill.side().toTradeType() == startingType) {
                positionBook.recordEntry(index, fill);
            } else {
                positionBook.recordExit(index, fill);
            }
            if (numFactory == null) {
                numFactory = fill.price().getNumFactory();
            }
            if (totalFees == null) {
                totalFees = numFactory.zero();
            }
            totalFees = totalFees.plus(fill.fee());
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
    public OpenPosition getNetOpenPosition() {
        lock.readLock().lock();
        try {
            return positionBook.netOpenPosition();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns a snapshot of the live trading record.
     *
     * @return snapshot
     * @since 0.22.2
     */
    public LiveTradingRecordSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new LiveTradingRecordSnapshot(getPositions(), getOpenPositions(), getNetOpenPosition(),
                    List.copyOf(buildTrades()));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TradeType getStartingType() {
        return startingType;
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

    @Override
    public void operate(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            ExecutionSide side = positionBook.openLots().isEmpty()
                    ? (startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL)
                    : (startingType == TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY);
            recordFill(index, new ExecutionFill(Instant.EPOCH, price, amount, null, side, null, null));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        lock.writeLock().lock();
        try {
            if (!positionBook.openLots().isEmpty()) {
                return false;
            }
            recordFill(index, new ExecutionFill(Instant.EPOCH, price, amount, null,
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
            recordFill(index, new ExecutionFill(Instant.EPOCH, price, amount, null,
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
            Position position = new Position(startingType, transactionCostModel, holdingCostModel);
            position.operate(entryIndex, net.averageEntryPrice(), net.amount());
            return position;
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

    private static void validateFill(ExecutionFill fill) {
        if (fill.amount().isNaN() || fill.amount().isZero() || fill.amount().isNegative()) {
            throw new IllegalArgumentException("Fill amount must be positive");
        }
        if (fill.price().isNaN()) {
            throw new IllegalArgumentException("Fill price must be set");
        }
        if (fill.fee().isNaN()) {
            throw new IllegalArgumentException("Fill fee must be set");
        }
    }

    private List<Trade> buildTrades() {
        List<Trade> trades = new ArrayList<>();
        for (Position position : positionBook.closedPositions()) {
            trades.add(position.getEntry());
            trades.add(position.getExit());
        }
        for (PositionLot lot : positionBook.openLots()) {
            trades.add(new Trade(lot.entryIndex(), startingType, lot.entryPrice(), lot.amount(), transactionCostModel));
        }
        trades.sort(Comparator.comparingInt(Trade::getIndex)
                .thenComparing(trade -> trade.getType() == startingType ? 0 : 1));
        return trades;
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
        return GSON.toJson(this);
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        lock = new ReentrantReadWriteLock();
        if (transactionCostModel == null) {
            transactionCostModel = new ZeroCostModel();
        }
        if (holdingCostModel == null) {
            holdingCostModel = new ZeroCostModel();
        }
        tradesCache = null;
        tradesCacheVersion = -1L;
        modificationCount = 0L;
        numFactory = null;
    }
}
