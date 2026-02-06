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
 * Maximum drawdown criterion, returned in decimal format.
 *
 * <p>
 * The maximum drawdown measures the largest loss. Its value can be within the
 * range of [0,1], e.g. a maximum drawdown of {@code +1} (= +100%) means a total
 * loss, a maximum drawdown of {@code 0} (= 0%) means no loss at all.
 *
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether open positions
 * contribute to the drawdown. {@link EquityCurveMode#REALIZED} always ignores
 * open positions regardless of the requested handling.
 *
 * <pre>{@code
 * MaximumDrawdownCriterion markToMarket = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
 *         OpenPositionHandling.MARK_TO_MARKET);
 * MaximumDrawdownCriterion ignoreOpen = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
 *         OpenPositionHandling.IGNORE);
 * }</pre>
 *
 * @see <a href=
 *      "http://en.wikipedia.org/wiki/Drawdown_%28economics%29">https://en.wikipedia.org/wiki/Drawdown_(economics)</a>
 */
public class MaximumDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Constructor using {@link EquityCurveMode#MARK_TO_MARKET} by default.
     */
    public MaximumDrawdownCriterion() {
        super();
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use for drawdown
     * @since 0.22.2
     */
    public MaximumDrawdownCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumDrawdownCriterion(OpenPositionHandling openPositionHandling) {
        super(openPositionHandling);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use for drawdown
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public MaximumDrawdownCriterion(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.numFactory().zero();
        }
        CashFlow cashFlow = new CashFlow(series, position, equityCurveMode);
        return Drawdown.amount(series, null, cashFlow);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling);
        return Drawdown.amount(series, tradingRecord, cashFlow);
    }

    /**
     * The lower the criterion value, the better.
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

}
