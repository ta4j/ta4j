/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
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
 * recorded opening fees. The raw {@code price * contracts} product would mix a
 * per-asset price with a contract count and is therefore never used for
 * futures.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionCostBasisCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (!position.isOpened()) {
            return factory.zero();
        }
        return toSeriesNum(factory, costBasis(series, position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        Position current = tradingRecord.getCurrentPosition();
        if (!current.isOpened()) {
            return factory.zero();
        }
        return toSeriesNum(factory, costBasis(series, current));
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }

    private Num costBasis(BarSeries series, Position position) {
        Trade entry = position.getEntry();
        FuturesContract contract = position.getFuturesContract();
        if (contract != null) {
            Num notional = contract.settlementNotional(entry.getAmount().abs(), entry.getPricePerAsset(series));
            Num openingFees = entry.getCost();
            if (openingFees == null || openingFees.isNaN()) {
                openingFees = notional.getNumFactory().zero();
            } else {
                openingFees = notional.getNumFactory().numOf(openingFees.getDelegate());
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
