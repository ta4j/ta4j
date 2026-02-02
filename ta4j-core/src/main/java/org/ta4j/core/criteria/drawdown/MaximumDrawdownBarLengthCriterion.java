/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Criterion that calculates the maximum drawdown length of a trading strategy,
 * expressed as the number of bars between a peak in the equity curve and the
 * lowest point of the subsequent drawdown.
 * <p>
 * Unlike {@code MaximumDrawdownCriterion}, which measures the depth of the
 * drawdown, this criterion measures the <b>duration</b> of the longest drawdown
 * period.
 * </p>
 *
 * @since 0.19
 */
public class MaximumDrawdownBarLengthCriterion extends AbstractAnalysisCriterion {

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
            var cashFlow = new CashFlow(series, position);
            return Drawdown.length(series, null, cashFlow);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var cashFlow = new CashFlow(series, tradingRecord);
        return Drawdown.length(series, tradingRecord, cashFlow);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

}