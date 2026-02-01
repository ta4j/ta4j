/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Maintains open lots and closed positions for live trading.
 *
 * @since 0.22.2
 */
public final class PositionBook implements Serializable, PositionLedger {

    @Serial
    private static final long serialVersionUID = -6897162194206253952L;

    private static final Gson GSON = new Gson();

    private final TradeType startingType;
    private final ExecutionMatchPolicy matchPolicy;
    private transient CostModel transactionCostModel;
    private transient CostModel holdingCostModel;
    private final Deque<PositionLot> openLots;
    private final List<Position> closedPositions;

    /**
     * Creates a position book with FIFO matching.
     *
     * @param startingType entry trade type
     * @since 0.22.2
     */
    public PositionBook(TradeType startingType) {
        this(startingType, ExecutionMatchPolicy.FIFO, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Creates a position book.
     *
     * @param startingType         entry trade type
     * @param matchPolicy          matching policy
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @since 0.22.2
     */
    public PositionBook(TradeType startingType, ExecutionMatchPolicy matchPolicy, CostModel transactionCostModel,
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

    /**
     * Records an entry trade.
     *
     * @param index trade index
     * @param trade live trade
     * @since 0.22.2
     */
    public void recordEntry(int index, LiveTrade trade) {
        if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
            normalizeAvgCostLots();
        }
        PositionLot lot = new PositionLot(index, trade.time(), trade.price(), trade.amount(), trade.fee(),
                trade.orderId(), trade.correlationId());
        if (matchPolicy == ExecutionMatchPolicy.AVG_COST && !openLots.isEmpty()) {
            PositionLot merged = openLots.removeFirst().merge(lot);
            openLots.addFirst(merged);
            return;
        }
        openLots.addLast(lot);
    }

    /**
     * Records an exit trade and returns closed positions.
     *
     * @param index trade index
     * @param trade live trade
     * @return closed positions
     * @since 0.22.2
     */
    public List<Position> recordExit(int index, LiveTrade trade) {
        if (matchPolicy == ExecutionMatchPolicy.AVG_COST) {
            normalizeAvgCostLots();
        }
        Num remaining = trade.amount();
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
            Position position = closeLot(lot, trade, index, closeAmount);
            closed.add(position);
            closedPositions.add(position);
            remaining = remaining.minus(closeAmount);
        }
        return List.copyOf(closed);
    }

    /**
     * @return open lots
     * @since 0.22.2
     */
    public List<PositionLot> openLots() {
        return List.copyOf(openLots);
    }

    /**
     * @return closed positions
     * @since 0.22.2
     */
    @Override
    public List<Position> getPositions() {
        return closedPositions();
    }

    /**
     * @return closed positions
     * @since 0.22.2
     */
    public List<Position> closedPositions() {
        return List.copyOf(closedPositions);
    }

    /**
     * @return open positions (per-lot)
     * @since 0.22.2
     */
    @Override
    public List<OpenPosition> getOpenPositions() {
        return openPositions();
    }

    /**
     * @return open positions (per-lot)
     * @since 0.22.2
     */
    public List<OpenPosition> openPositions() {
        List<OpenPosition> positions = new ArrayList<>();
        for (PositionLot lot : openLots) {
            Num totalCost = lot.entryPrice().multipliedBy(lot.amount());
            positions.add(new OpenPosition(startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL,
                    lot.amount(), lot.entryPrice(), totalCost, lot.fee(), lot.entryTime(), lot.entryTime(),
                    List.of(lot)));
        }
        return positions;
    }

    /**
     * @return aggregated net open position
     * @since 0.22.2
     */
    public OpenPosition netOpenPosition() {
        if (openLots.isEmpty()) {
            return null;
        }
        Num totalAmount = null;
        Num totalCost = null;
        Num totalFees = null;
        Instant earliest = null;
        Instant latest = null;
        for (PositionLot lot : openLots) {
            Num lotCost = lot.entryPrice().multipliedBy(lot.amount());
            totalAmount = totalAmount == null ? lot.amount() : totalAmount.plus(lot.amount());
            totalCost = totalCost == null ? lotCost : totalCost.plus(lotCost);
            totalFees = totalFees == null ? lot.fee() : totalFees.plus(lot.fee());
            earliest = earliest == null || lot.entryTime().isBefore(earliest) ? lot.entryTime() : earliest;
            latest = latest == null || lot.entryTime().isAfter(latest) ? lot.entryTime() : latest;
        }
        Num average = totalAmount == null || totalAmount.isZero() ? totalCost : totalCost.dividedBy(totalAmount);
        return new OpenPosition(startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL, totalAmount,
                average, totalCost, totalFees == null ? totalCost.getNumFactory().zero() : totalFees, earliest, latest,
                openLots());
    }

    /**
     * @return aggregated net open position
     * @since 0.22.2
     */
    @Override
    public OpenPosition getNetOpenPosition() {
        return netOpenPosition();
    }

    private PositionLot nextLot(LiveTrade trade) {
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

    private PositionLot matchSpecificLot(LiveTrade trade) {
        String key = trade.correlationId() == null ? trade.orderId() : trade.correlationId();
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

    private Position closeLot(PositionLot lot, LiveTrade trade, int index, Num closeAmount) {
        Num lotAmount = lot.amount();
        Num feePortion = lot.fee().isZero() ? lot.fee() : lot.fee().multipliedBy(closeAmount).dividedBy(lotAmount);
        if (closeAmount.isEqual(lotAmount)) {
            openLots.remove(lot);
        } else {
            lot.reduce(closeAmount, feePortion);
        }
        Trade entry = new BaseTrade(lot.entryIndex(), startingType, lot.entryPrice(), closeAmount,
                transactionCostModel);
        Trade exit = new BaseTrade(index, startingType.complementType(), trade.price(), closeAmount,
                transactionCostModel);
        return new Position(entry, exit, transactionCostModel, holdingCostModel);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }

    @Serial
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        if (transactionCostModel == null) {
            transactionCostModel = new ZeroCostModel();
        }
        if (holdingCostModel == null) {
            holdingCostModel = new ZeroCostModel();
        }
    }
}
