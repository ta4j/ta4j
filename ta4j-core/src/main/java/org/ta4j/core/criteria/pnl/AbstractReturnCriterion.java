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
import org.ta4j.core.num.NumFactory;

/**
 * Base class for return based criteria.
 * <p>
 * Handles calculation of the aggregated return across positions and the
 * optional inclusion of the base percentage.
 */
public abstract class AbstractReturnCriterion extends AbstractAnalysisCriterion {

    /**
     * If {@code true} the base percentage of {@code 1} (equivalent to 100%) is
     * included in the returned value.
     */
    protected final boolean addBase;

    /**
     * Constructor with {@link #addBase} set to {@code true}.
     */
    protected AbstractReturnCriterion() {
        this(true);
    }

    /**
     * Constructor.
     *
     * @param addBase whether to include the base percentage
     */
    protected AbstractReturnCriterion(boolean addBase) {
        this.addBase = addBase;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            return calculateReturn(series, position);
        }
        var numFactory = series.numFactory();
        if (addBase) {
            return numFactory.one();
        }
        return numFactory.zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var one = series.numFactory().one();
        var result = tradingRecord.getPositions()
                .stream()
                .map(p -> calculate(series, p))
                .reduce(one, Num::multipliedBy);
        if (addBase) {
            return result;
        }
        return result.minus(one);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the return of the given closed position including the base.
     *
     * @param series   the bar series
     * @param position the closed position
     * @return the return of the position including the base
     */
    protected abstract Num calculateReturn(BarSeries series, Position position);
}
