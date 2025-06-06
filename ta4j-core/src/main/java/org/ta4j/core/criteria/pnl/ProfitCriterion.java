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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Profit criterion which can either compute the <em>net</em> or the
 * <em>gross</em> profit.
 *
 * <p>
 * If {@code excludeCosts} is {@code false} (the default), trading costs are
 * deducted from each position and the resulting value represents the net
 * profit. If {@code excludeCosts} is {@code true}, the calculation ignores
 * trading costs and returns the gross profit instead.
 *
 * <p>
 * The profit of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 */
public class ProfitCriterion extends AbstractAnalysisCriterion {

    private final boolean excludeCosts;

    /**
     * Constructor creating a criterion that includes trading costs in the profit
     * calculation, i.e. net profit is returned.
     */
    public ProfitCriterion() {
        this(false);
    }

    /**
     * Constructor.
     *
     * @param excludeCosts set to {@code true} to ignore trading costs and return
     *                     gross profit
     */
    public ProfitCriterion(boolean excludeCosts) {
        this.excludeCosts = excludeCosts;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            Num profit = excludeCosts ? position.getGrossProfit() : position.getProfit();
            return profit.isPositive() ? profit : series.numFactory().zero();
        }
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(position -> calculate(series, position))
                .reduce(series.numFactory().zero(), Num::plus);
    }

    /** The higher the criterion value (= the higher the profit), the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
