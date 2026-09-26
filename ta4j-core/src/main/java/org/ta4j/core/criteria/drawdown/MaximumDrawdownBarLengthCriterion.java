/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractEquityCurveSettingsCriterion;
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
public class MaximumDrawdownBarLengthCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Constructor using {@link EquityCurveMode#MARK_TO_MARKET} by default.
     */
    public MaximumDrawdownBarLengthCriterion() {
        super();
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use for drawdown
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion(OpenPositionHandling openPositionHandling) {
        super(openPositionHandling);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use for drawdown
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion(EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
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
        }
        CashFlow cashFlow = new CashFlow(series, position, equityCurveMode);
        return Drawdown.length(series, null, cashFlow);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling);
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
