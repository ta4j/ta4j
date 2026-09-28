/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that totals execution fees across a trading record.
 *
 * <p>
 * This criterion is intentionally distinct from {@link CommissionsCriterion}.
 * The commissions criterion models costs using the configured transaction cost
 * model, while this criterion returns actual, recorded execution fees when a
 * {@link TradingRecord} exposes them. This keeps modeled cost analytics stable
 * while enabling fee-aware live tracking (partial fills, maker/taker mixes, and
 * exchange fee changes).
 * </p>
 *
 * <p>
 * When a trading record does not expose execution fees, this criterion falls
 * back to the transaction cost model (matching {@link CommissionsCriterion}
 * semantics).
 * </p>
 *
 * <p>
 * A native futures position or record returns its actual recorded settlement
 * fees, i.e. the allocated cost of every executed fill, and never the
 * configured cost model. Funding and variation-margin cash flows are account
 * settlements rather than execution fees and stay outside this total.
 * </p>
 *
 * @since 0.22.2
 */
public class TotalFeesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (position.getFuturesContract() != null) {
            int endIndex = position.isClosed() ? Integer.MAX_VALUE : series.getEndIndex();
            return toSeriesNum(factory, executedFees(factory, position, endIndex));
        }
        if (position.isClosed()) {
            Num cost = position.getEntry().getCostModel().calculate(position);
            return toSeriesNum(factory, cost);
        }
        if (position.isOpened()) {
            int endIndex = series.getEndIndex();
            Num cost = position.getEntry().getCostModel().calculate(position, endIndex);
            return toSeriesNum(factory, cost);
        }
        return factory.zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        if (tradingRecord.getFuturesContract() != null) {
            int finalIndex = Math.max(tradingRecord.getEndIndex(series), lastExecutedIndex(tradingRecord));
            return toSeriesNum(factory, executedFees(factory, tradingRecord, finalIndex));
        }
        Num recordedFees = tradingRecord.getRecordedTotalFees();
        if (recordedFees != null) {
            return toSeriesNum(factory, recordedFees);
        }
        Num closedFees = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(position -> calculate(series, position))
                .reduce(factory.zero(), Num::plus);

        Position current = tradingRecord.getCurrentPosition();
        if (current.isOpened()) {
            return closedFees.plus(calculate(series, current));
        }
        return closedFees;
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }

    private Num executedFees(NumFactory factory, TradingRecord tradingRecord, int openFinalIndex) {
        CompensatedSum total = new CompensatedSum(factory);
        for (Position position : tradingRecord.getPositions()) {
            addExecutedFees(total, position, Integer.MAX_VALUE);
        }
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions.isEmpty()) {
            Position current = tradingRecord.getCurrentPosition();
            if (current != null && current.isOpened()) {
                addExecutedFees(total, current, openFinalIndex);
            }
            return total.total();
        }
        for (Position position : openPositions) {
            addExecutedFees(total, position, openFinalIndex);
        }
        return total.total();
    }

    private int lastExecutedIndex(TradingRecord tradingRecord) {
        int lastIndex = -1;
        for (Trade trade : tradingRecord.getTrades()) {
            for (TradeFill fill : Trade.executionFillsOf(trade)) {
                if (fill.index() >= 0) {
                    lastIndex = Math.max(lastIndex, fill.index());
                }
            }
        }
        return lastIndex;
    }

    private Num executedFees(NumFactory factory, Position position, int finalIndex) {
        CompensatedSum total = new CompensatedSum(factory);
        addExecutedFees(total, position, finalIndex);
        return total.total();
    }

    private void addExecutedFees(CompensatedSum total, Position position, int finalIndex) {
        Trade entry = position.getEntry();
        if (entry != null && entry.getIndex() <= finalIndex) {
            addFee(total, entry, finalIndex);
        }
        Trade exit = position.getExit();
        if (exit != null && exit.getIndex() <= finalIndex) {
            addFee(total, exit, finalIndex);
        }
    }

    private void addFee(CompensatedSum total, Trade trade, int finalIndex) {
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex && fill.fee() != null && !fill.fee().isNaN()) {
                total.add(fill.fee());
            }
        }
    }

    private static final class CompensatedSum {

        private final NumFactory factory;
        private Num sum;
        private Num compensation;

        private CompensatedSum(NumFactory factory) {
            this.factory = factory;
            this.sum = factory.zero();
            this.compensation = factory.zero();
        }

        private void add(Num value) {
            Num normalized = factory.numOf(value.getDelegate());
            if (!Num.isFinite(normalized) || (!value.isZero() && normalized.isZero())) {
                throw new IllegalArgumentException("Fee must be finite and representable in series number factory");
            }
            Num nextSum = sum.plus(normalized);
            if (!Num.isFinite(nextSum)) {
                throw new IllegalArgumentException("Fee total must be finite in series number factory");
            }
            Num correction;
            if (sum.abs().isGreaterThanOrEqual(normalized.abs())) {
                correction = sum.minus(nextSum).plus(normalized);
            } else {
                correction = normalized.minus(nextSum).plus(sum);
            }
            Num nextCompensation = compensation.plus(correction);
            if (!Num.isFinite(nextCompensation)) {
                throw new IllegalArgumentException("Fee total must be finite in series number factory");
            }
            sum = nextSum;
            compensation = nextCompensation;
        }

        private Num total() {
            Num total = sum.plus(compensation);
            if (!Num.isFinite(total)) {
                throw new IllegalArgumentException("Fee total must be finite in series number factory");
            }
            return total;
        }
    }

    private Num toSeriesNum(NumFactory factory, Num value) {
        if (value == null) {
            return factory.zero();
        }
        if (value.isNaN()) {
            return NaN.NaN;
        }
        return factory.numOf(value.getDelegate());
    }
}
