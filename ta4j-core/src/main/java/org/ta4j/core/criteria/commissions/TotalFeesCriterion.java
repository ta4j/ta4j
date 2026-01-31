/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import org.ta4j.core.BarSeries;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Position;
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
 * {@link LiveTradingRecord} is available. This keeps modeled cost analytics
 * stable while enabling fee-aware live tracking (partial fills, maker/taker
 * mixes, and exchange fee changes).
 * </p>
 *
 * <p>
 * When a trading record does not expose execution fees, this criterion falls
 * back to the transaction cost model (matching {@link CommissionsCriterion}
 * semantics).
 * </p>
 *
 * @since 0.22.2
 */
public class TotalFeesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
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
        if (tradingRecord instanceof LiveTradingRecord liveRecord) {
            return toSeriesNum(factory, liveRecord.getTotalFees());
        }
        var model = tradingRecord.getTransactionCostModel();
        Num closedFees = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(model::calculate)
                .map(value -> toSeriesNum(factory, value))
                .reduce(factory.zero(), Num::plus);

        Position current = tradingRecord.getCurrentPosition();
        if (current.isOpened()) {
            Num openFees = model.calculate(current, tradingRecord.getEndIndex(series));
            return closedFees.plus(toSeriesNum(factory, openFees));
        }
        return closedFees;
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
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
