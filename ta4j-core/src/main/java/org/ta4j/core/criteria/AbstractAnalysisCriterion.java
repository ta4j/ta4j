/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An abstract analysis criterion.
 */
public abstract class AbstractAnalysisCriterion implements AnalysisCriterion {

    /**
     * Returns the return representation used by this criterion, if applicable.
     * <p>
     * Criteria that use {@link ReturnRepresentation} should override this method to
     * return their representation. Criteria that don't use return representations
     * should not override this method (it defaults to empty).
     *
     * @return the return representation, or empty if this criterion doesn't use
     *         return representations
     */
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.empty();
    }

    /**
     * Returns the positions open at the logical end of a trading record, for
     * criteria that account for open exposure.
     *
     * <p>
     * For a record with an explicit start or end index, this is every position
     * entered within {@code [getStartIndex(series), getEndIndex(series)]} that is
     * still open at the end: recorded positions exiting after the end (as open
     * views of their entries) and the record's open lots
     * ({@link TradingRecord#getOpenPositions()}, or the current position for
     * records without lots). Slices of one entry trade, created when a lot-aware
     * record splits an entry across several exits, are merged back into one open
     * position so per-trade costs are charged once per entry. These are the
     * positions excluded by {@link TradingRecord#getContainedPositions(BarSeries)}
     * only because they were still open at the end. Records without explicit bounds
     * return the current open position, if any.
     * </p>
     *
     * @param series        the bar series used to resolve the logical range
     * @param tradingRecord the trading record
     * @return the open positions, possibly empty
     * @since 0.26.1
     */
    protected static List<Position> positionsOpenAtEnd(BarSeries series, TradingRecord tradingRecord) {
        Position current = tradingRecord.getCurrentPosition();
        boolean currentOpened = current != null && current.isOpened();
        if (tradingRecord.getStartIndex() == null && tradingRecord.getEndIndex() == null) {
            return currentOpened ? List.of(current) : List.of();
        }
        int startIndex = tradingRecord.getStartIndex(series);
        int endIndex = tradingRecord.getEndIndex(series);
        Map<EntryKey, List<Position>> slicesByEntry = new LinkedHashMap<>();
        for (Position position : tradingRecord.getPositions()) {
            if (position != null && position.isClosed() && position.getExit().getIndex() > endIndex) {
                addOpenSlice(slicesByEntry, new Position(position.getEntry(), position.getTransactionCostModel(),
                        position.getHoldingCostModel()), startIndex, endIndex);
            }
        }
        List<Position> openLots = tradingRecord.getOpenPositions();
        if (openLots.isEmpty() && currentOpened) {
            openLots = List.of(current);
        }
        for (Position openLot : openLots) {
            if (openLot != null && openLot.isOpened()) {
                addOpenSlice(slicesByEntry, openLot, startIndex, endIndex);
            }
        }
        List<Position> openPositions = new ArrayList<>(slicesByEntry.size());
        for (List<Position> slices : slicesByEntry.values()) {
            openPositions.add(slices.size() == 1 ? slices.get(0) : mergeSlices(slices));
        }
        return openPositions;
    }

    private static void addOpenSlice(Map<EntryKey, List<Position>> slicesByEntry, Position slice, int startIndex,
            int endIndex) {
        Trade entry = slice.getEntry();
        if (entry.getIndex() < startIndex || entry.getIndex() > endIndex) {
            return;
        }
        slicesByEntry.computeIfAbsent(EntryKey.of(entry), key -> new ArrayList<>(1)).add(slice);
    }

    private static Position mergeSlices(List<Position> slices) {
        Position first = slices.get(0);
        Trade entry = first.getEntry();
        Num amount = entry.getAmount();
        Num fee = entry.getCost();
        for (int i = 1; i < slices.size(); i++) {
            Trade slice = slices.get(i).getEntry();
            amount = amount.plus(slice.getAmount());
            fee = fee.plus(slice.getCost());
        }
        ExecutionSide side = entry.isBuy() ? ExecutionSide.BUY : ExecutionSide.SELL;
        Trade merged = Trade.fromFills(entry.getType(), List.of(new TradeFill(entry.getIndex(), entry.getTime(),
                entry.getPricePerAsset(), amount, fee, side, entry.getOrderId(), entry.getCorrelationId())),
                entry.getCostModel());
        return new Position(merged, first.getTransactionCostModel(), first.getHoldingCostModel());
    }

    /**
     * Identity of an entry trade shared by the slices a lot-aware record creates
     * when one entry is closed by several exits.
     */
    private record EntryKey(int index, Trade.TradeType type, Num price, Instant time, String orderId,
            String correlationId) {

        static EntryKey of(Trade entry) {
            return new EntryKey(entry.getIndex(), entry.getType(), entry.getPricePerAsset(), entry.getTime(),
                    entry.getOrderId(), entry.getCorrelationId());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EntryKey key && index == key.index && type == key.type && price.isEqual(key.price)
                    && Objects.equals(time, key.time) && Objects.equals(orderId, key.orderId)
                    && Objects.equals(correlationId, key.correlationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, type, time, orderId, correlationId);
        }
    }

    @Override
    public String toString() {
        String[] tokens = getClass().getSimpleName().split("(?=\\p{Lu})", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(tokens[i]).append(' ');
        }
        return sb.toString().trim();
    }
}
