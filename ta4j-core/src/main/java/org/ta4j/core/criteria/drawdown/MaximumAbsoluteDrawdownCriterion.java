/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Criterion that calculates the <b>maximum absolute drawdown</b> of an equity
 * curve.
 * <p>
 * The maximum absolute drawdown is the largest observed decline from a
 * cumulative profit peak to a subsequent trough, expressed in absolute terms
 * rather than relative percentage. It is a measure of downside risk and capital
 * exposure during a trading period.
 *
 * @since 0.19
 */
public final class MaximumAbsoluteDrawdownCriterion extends AbstractAnalysisCriterion {

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var pnl = new CumulativePnL(series, tradingRecord);
        return Drawdown.amount(series, tradingRecord, pnl, false);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.numFactory().zero();
        } else {
            var pnl = new CumulativePnL(series, position);
            return Drawdown.amount(series, null, pnl, false);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num first, Num second) {
        return first.isLessThan(second);
    }

}