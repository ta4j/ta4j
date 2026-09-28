/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the cost basis of the open position.
 *
 * <p>
 * This returns the current open position's entry value plus entry transaction
 * cost. Returns zero when no open position exists.
 * </p>
 *
 * <p>
 * A native futures position is quoted in contracts, so its cost basis is the
 * entry settlement notional of the remaining contract quantity plus the
 * recorded opening fees. Only fills executed through the record end index (or
 * series end index for a position-level calculation) contribute. The raw
 * {@code price * contracts} product would mix a per-asset price with a contract
 * count and is therefore never used for futures.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionCostBasisCriterion extends AbstractAnalysisCriterion {
    private static final Comparator<TradeFill> EXECUTION_FILL_ORDER = Comparator.comparingInt(TradeFill::index)
            .thenComparing(TradeFill::time, Comparator.nullsFirst(Comparator.naturalOrder()));

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (position.getFuturesContract() == null && !position.isOpened()) {
            return factory.zero();
        }
        return toSeriesNum(factory, costBasis(series, position, series.getEndIndex()));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        Position current = tradingRecord.getCurrentPosition();
        if (current.getFuturesContract() == null && !current.isOpened()) {
            return factory.zero();
        }
        return toSeriesNum(factory, costBasis(series, current, tradingRecord.getEndIndex(series)));
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }

    private Num costBasis(BarSeries series, Position position, int finalIndex) {
        Trade entry = position.getEntry();
        FuturesContract contract = position.getFuturesContract();
        if (contract != null) {
            List<TradeFill> entryFills = Trade.executionFillsOf(entry)
                    .stream()
                    .filter(fill -> fill.index() >= 0 && fill.index() <= finalIndex)
                    .sorted(EXECUTION_FILL_ORDER)
                    .toList();
            if (entryFills.isEmpty()) {
                return series.numFactory().zero();
            }
            List<TradeFill> exitFills = position.getExit() == null ? List.of()
                    : Trade.executionFillsOf(position.getExit())
                            .stream()
                            .filter(fill -> fill.index() >= 0 && fill.index() <= finalIndex)
                            .sorted(EXECUTION_FILL_ORDER)
                            .toList();
            return futuresCostBasis(contract, entry, entryFills, exitFills);
        }
        List<TradeFill> allEntryFills = Trade.executionFillsOf(entry);
        List<TradeFill> entryFills = allEntryFills.stream()
                .filter(fill -> fill.index() >= 0 && fill.index() <= finalIndex)
                .toList();
        if (entryFills.isEmpty()) {
            return series.numFactory().zero();
        }
        Trade executedEntry = entryFills.size() == allEntryFills.size() ? entry
                : Trade.fromFills(entry.getType(), entryFills, entry.getCostModel());
        return executedEntry.getPricePerAsset(series)
                .multipliedBy(executedEntry.getAmount())
                .plus(executedEntry.getCost());
    }

    private Num futuresCostBasis(FuturesContract contract, Trade entry, List<TradeFill> entryFills,
            List<TradeFill> exitFills) {
        NumFactory factory = entry.getPricePerAsset().getNumFactory();
        List<Num> remainingAmounts = new ArrayList<>(entryFills.size());
        for (TradeFill entryFill : entryFills) {
            remainingAmounts.add(factory.numOf(entryFill.amount().getDelegate()));
        }

        int entryFillIndex = 0;
        for (TradeFill exitFill : exitFills) {
            Num remainingExitAmount = factory.numOf(exitFill.amount().getDelegate());
            while (remainingExitAmount.isPositive()) {
                while (entryFillIndex < remainingAmounts.size() && !remainingAmounts.get(entryFillIndex).isPositive()) {
                    entryFillIndex++;
                }
                if (entryFillIndex >= remainingAmounts.size()) {
                    throw new IllegalArgumentException("Exit amount exceeds executed entry amount");
                }
                Num remainingEntryAmount = remainingAmounts.get(entryFillIndex);
                Num matchedAmount = remainingExitAmount.isLessThan(remainingEntryAmount) ? remainingExitAmount
                        : remainingEntryAmount;
                remainingAmounts.set(entryFillIndex, remainingEntryAmount.minus(matchedAmount));
                remainingExitAmount = remainingExitAmount.minus(matchedAmount);
            }
        }

        Num total = factory.zero();
        for (int i = 0; i < entryFills.size(); i++) {
            Num remainingAmount = remainingAmounts.get(i);
            if (!remainingAmount.isPositive()) {
                continue;
            }
            TradeFill entryFill = entryFills.get(i);
            Num entryPrice = toSeriesNum(factory, entryFill.price());
            Num notional = contract.settlementNotional(remainingAmount, entryPrice);
            Num openingFee = openingFee(entry, entryFill, remainingAmount, factory);
            total = total.plus(notional).plus(openingFee);
        }
        return total;
    }

    private Num openingFee(Trade entry, TradeFill entryFill, Num remainingAmount, NumFactory factory) {
        Num fillFee = entryFill.hasRecordedFees() ? entryFill.fee() : entry.getCostModel().calculate(entryFill);
        Num normalizedFee = toSeriesNum(factory, fillFee);
        Num originalAmount = toSeriesNum(factory, entryFill.amount());
        Num projectedFee = normalizedFee.dividedBy(originalAmount).multipliedBy(remainingAmount);
        if (Num.isFinite(projectedFee)
                && (!projectedFee.isZero() || normalizedFee.isZero() || remainingAmount.isZero())) {
            return projectedFee;
        }
        Num fallback = remainingAmount.dividedBy(originalAmount).multipliedBy(normalizedFee);
        if (!Num.isFinite(fallback)) {
            throw new IllegalArgumentException("opening fee allocation must be finite in series number factory");
        }
        return fallback;
    }

    private Num toSeriesNum(NumFactory factory, Num value) {
        if (value == null) {
            return factory.zero();
        }
        if (value.isNaN()) {
            return NaN.NaN;
        }
        Num normalized = factory.numOf(value.getDelegate());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException("value must be finite and representable in series number factory");
        }
        if (!value.isZero() && normalized.isZero()) {
            throw new IllegalArgumentException("value must be representable in series number factory");
        }
        return normalized;
    }
}
