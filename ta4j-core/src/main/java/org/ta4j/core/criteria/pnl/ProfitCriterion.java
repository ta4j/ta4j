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
 * Wrapper criterion delegating to {@link NetProfitCriterion} or
 * {@link GrossProfitCriterion}.
 */
public class ProfitCriterion extends AbstractAnalysisCriterion {

    private final AbstractPnLCriterion delegate;

    /**
     * Creates a profit criterion that includes trading costs (net profit).
     */
    public ProfitCriterion() {
        delegate = new NetProfitCriterion();
    }

    /**
     * Constructor retained for backward compatibility.
     *
     * @param excludeCosts if {@code true} trading costs are ignored (gross profit)
     *                     otherwise net profit is used
     */
    @Deprecated
    public ProfitCriterion(boolean excludeCosts) {
        if (excludeCosts) {
            delegate = new GrossProfitCriterion();
        } else {
            delegate = new NetProfitCriterion();
        }
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return delegate.calculate(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return delegate.calculate(series, tradingRecord);
    }

    /** The higher the criterion value (= the higher the profit), the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return delegate.betterThan(criterionValue1, criterionValue2);
    }

}
