/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether the last open position
 * contributes to the drawdown. {@link EquityCurveMode#REALIZED} always ignores
 * open positions regardless of the requested handling.
 *
 * <pre>{@code
 * var markToMarket = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
 *         OpenPositionHandling.MARK_TO_MARKET);
 * var ignoreOpen = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
 * }</pre>
 *
 * @since 0.19
 */
public final class MaximumAbsoluteDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    /**
     * Creates a maximum absolute drawdown criterion using mark-to-market cash flow.
     *
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion() {
        super();
    }

    /**
     * Creates a maximum absolute drawdown criterion using the provided equity curve
     * mode.
     *
     * @param equityCurveMode the equity curve mode to use
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    /**
     * Creates a maximum absolute drawdown criterion using the provided open
     * position handling.
     *
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public MaximumAbsoluteDrawdownCriterion(OpenPositionHandling openPositionHandling) {
        super(openPositionHandling);
    }

    /**
     * Creates a maximum absolute drawdown criterion using the provided equity curve
     * mode and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use
     * @param openPositionHandling how to handle the last open position
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
        var pnl = new CumulativePnL(series, tradingRecord, equityCurveMode, openPositionHandling);
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
            var pnl = new CumulativePnL(series, position, equityCurveMode);
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
