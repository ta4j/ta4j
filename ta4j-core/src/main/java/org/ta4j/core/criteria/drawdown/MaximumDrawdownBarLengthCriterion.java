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
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether the last open position
 * contributes to the drawdown duration. {@link EquityCurveMode#REALIZED} always
 * ignores open positions regardless of the requested handling.
 *
 * <pre>{@code
 * var markToMarket = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET,
 *         OpenPositionHandling.MARK_TO_MARKET);
 * var ignoreOpen = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
 * }</pre>
 *
 * @since 0.19
 */
public class MaximumDrawdownBarLengthCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Creates a maximum drawdown length criterion using mark-to-market cash flow.
     *
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion() {
        super();
    }

    /**
     * Creates a maximum drawdown length criterion using the provided equity curve
     * mode.
     *
     * @param equityCurveMode the equity curve mode to use
     *
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    /**
     * Creates a maximum drawdown length criterion using the provided open position
     * handling.
     *
     * @param openPositionHandling how to handle the last open position
     *
     * @since 0.22.2
     */
    public MaximumDrawdownBarLengthCriterion(OpenPositionHandling openPositionHandling) {
        super(openPositionHandling);
    }

    /**
     * Creates a maximum drawdown length criterion using the provided equity curve
     * mode and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle the last open position
     *
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
        } else {
            var cashFlow = new CashFlow(series, position, equityCurveMode);
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
        var cashFlow = new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling);
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
