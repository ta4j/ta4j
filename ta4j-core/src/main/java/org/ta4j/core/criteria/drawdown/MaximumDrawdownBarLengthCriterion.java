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
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.criteria.AbstractEquityCurveCriterion;
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
public class MaximumDrawdownBarLengthCriterion extends AbstractEquityCurveCriterion {

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
        var cashFlow = new CashFlow(series, tradingRecord, equityCurveMode);
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
