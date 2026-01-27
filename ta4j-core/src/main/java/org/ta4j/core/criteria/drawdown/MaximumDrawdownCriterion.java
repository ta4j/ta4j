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
import org.ta4j.core.num.Num;

/**
 * Maximum drawdown criterion, returned in decimal format.
 *
 * <p>
 * The maximum drawdown measures the largest loss. Its value can be within the
 * range of [0,1], e.g. a maximum drawdown of {@code +1} (= +100%) means a total
 * loss, a maximum drawdown of {@code 0} (= 0%) means no loss at all.
 *
 * @see <a href=
 *      "http://en.wikipedia.org/wiki/Drawdown_%28economics%29">https://en.wikipedia.org/wiki/Drawdown_(economics)</a>
 */
public class MaximumDrawdownCriterion extends AbstractEquityCurveDrawdownCriterion<MaximumDrawdownCriterion> {

    /**
     * Creates a maximum drawdown criterion using mark-to-market cash flow.
     *
     * @since 0.22.2
     */
    public MaximumDrawdownCriterion() {
        super();
    }

    /**
     * Creates a maximum drawdown criterion using the provided equity curve mode.
     *
     * @param equityCurveMode the equity curve mode to use
     *
     * @since 0.22.2
     */
    public MaximumDrawdownCriterion(EquityCurveMode equityCurveMode) {
        super(equityCurveMode);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.numFactory().zero();
        }
        var cashFlow = new CashFlow(series, position, equityCurveMode);
        return Drawdown.amount(series, null, cashFlow);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var cashFlow = new CashFlow(series, tradingRecord, equityCurveMode);
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
