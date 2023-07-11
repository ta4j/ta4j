/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
 * Return (in percentage) criterion (includes trading costs), returned in
 * decimal format.
 *
 * <p>
 * The return of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 */
public class ReturnCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the base percentage of {@code 1} (equivalent to 100%) is added
     * to the criterion value.
     */
    private final boolean addBase;

    /**
     * Constructor with {@link #addBase} == true.
     */
    public ReturnCriterion() {
        this.addBase = true;
    }

    /**
     * Constructor.
     * 
     * @param addBase the {@link #addBase}
     */
    public ReturnCriterion(boolean addBase) {
        this.addBase = addBase;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return calculateProfit(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .map(position -> calculateProfit(series, position))
                .reduce(series.one(), Num::multipliedBy)
                .minus(addBase ? series.zero() : series.one());
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the gross return of a position (Buy and sell).
     *
     * @param series   a bar series
     * @param position a position
     * @return the gross return of the position
     */
    private Num calculateProfit(BarSeries series, Position position) {
        if (position.isClosed()) {
            return position.getGrossReturn(series);
        }
        return addBase ? series.one() : series.zero();
    }
}
