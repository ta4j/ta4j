/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Profit criterion with trading costs (= Gross profit) or without ( = Net
 * profit).
 *
 * <p>
 * The profit of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 */
public class ProfitCriterion extends AbstractAnalysisCriterion {

    private final boolean excludeTradingCosts;

    /**
     * Constructor for GrossProfit (includes trading costs)
     */
    public ProfitCriterion() {
        this(false);
    }

    /**
     * Constructor.
     * 
     * @param excludeTradingCosts set to true to exclude trading costs
     */
    public ProfitCriterion(boolean excludeTradingCosts) {
        this.excludeTradingCosts = excludeTradingCosts;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            Num profit = excludeTradingCosts ? position.getProfit() : position.getGrossProfit();
            return profit.isPositive() ? profit : series.numOf(0);
        }
        return series.numOf(0);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(position -> calculate(series, position))
                .reduce(series.numOf(0), Num::plus);
    }

    /** The higher the criterion value (= the higher the profit), the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
