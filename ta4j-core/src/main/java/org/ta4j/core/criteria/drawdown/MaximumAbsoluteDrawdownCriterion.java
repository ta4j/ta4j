/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractEquityCurveSettingsCriterion;
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
public final class MaximumAbsoluteDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Constructor using {@link EquityCurveMode#MARK_TO_MARKET} by default.
     */
    public MaximumAbsoluteDrawdownCriterion() {
        super();
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use for drawdown
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion(OpenPositionHandling openPositionHandling) {
        super(openPositionHandling);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use for drawdown
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion(EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        CumulativePnL pnl = new CumulativePnL(series, tradingRecord, equityCurveMode, openPositionHandling);
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
        }
        CumulativePnL pnl = new CumulativePnL(series, position, equityCurveMode);
        return Drawdown.amount(series, null, pnl, false);
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
