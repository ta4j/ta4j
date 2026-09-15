/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

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
                    .toList();
            if (entryFills.isEmpty()) {
                return series.numFactory().zero();
            }
            List<TradeFill> exitFills = position.getExit() == null ? List.of()
                    : Trade.executionFillsOf(position.getExit())
                            .stream()
                            .filter(fill -> fill.index() >= 0 && fill.index() <= finalIndex)
                            .toList();
            NumFactory contractFactory = contract.contractSize().getNumFactory();
            Num totalEntryQuantity = contractFactory.zero();
            for (TradeFill entryFill : entryFills) {
                totalEntryQuantity = totalEntryQuantity.plus(contractFactory.numOf(entryFill.amount().getDelegate()));
            }
            Num totalExitQuantity = contractFactory.zero();
            for (TradeFill exitFill : exitFills) {
                totalExitQuantity = totalExitQuantity.plus(contractFactory.numOf(exitFill.amount().getDelegate()));
            }
            Num remainingQuantity = totalEntryQuantity.minus(totalExitQuantity);
            if (!remainingQuantity.isPositive()) {
                return contractFactory.zero();
            }
            List<TradeFill> allEntryFills = Trade.executionFillsOf(entry);
            Trade executedEntry = entryFills.size() == allEntryFills.size() ? entry
                    : Trade.fromFills(entry.getType(), entryFills, entry.getCostModel());
            Num averageEntryPrice = entryFills.size() == allEntryFills.size() ? entry.getPricePerAsset(series)
                    : executedEntry.getPricePerAsset(series);
            Num notional = contract.settlementNotional(remainingQuantity,
                    contractFactory.numOf(averageEntryPrice.getDelegate()));
            Num openingFees = executedEntry.getCost();
            if (openingFees == null || openingFees.isNaN()) {
                openingFees = notional.getNumFactory().zero();
            } else {
                openingFees = notional.getNumFactory().numOf(openingFees.getDelegate());
                Num remaining = notional.getNumFactory().numOf(remainingQuantity.getDelegate());
                Num total = notional.getNumFactory().numOf(totalEntryQuantity.getDelegate());
                openingFees = openingFees.multipliedBy(remaining).dividedBy(total);
            }
            return notional.plus(openingFees);
        }
        Num entryPrice = entry.getPricePerAsset(series);
        return entryPrice.multipliedBy(entry.getAmount()).plus(entry.getCost());
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
