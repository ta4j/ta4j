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

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * Reward risk ratio criterion (also known as "RoMaD"), returned in decimal
 * format.
 *
 * <pre>
 * RoMaD = {@link NetReturnCriterion net return (without base)} / {@link MaximumDrawdownCriterion maximum drawdown}
 * </pre>
 */
public class ReturnOverMaxDrawdownCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion netReturnCriterion;
    private final AnalysisCriterion maxDrawdownCriterion = new MaximumDrawdownCriterion();

    public ReturnOverMaxDrawdownCriterion() {
        this(ReturnRepresentation.RATE_OF_RETURN);
    }

    public ReturnOverMaxDrawdownCriterion(ReturnRepresentation returnRepresentation) {
        this.netReturnCriterion = new NetReturnCriterion(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isOpened()) {
            return series.numFactory().zero();
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, position);
        var netReturn = netReturnCriterion.calculate(series, position);
        if (maxDrawdown.isZero()) {
            return netReturn;
        }
        return netReturn.dividedBy(maxDrawdown);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero(); // penalise no-trade strategies
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, tradingRecord);
        var netReturn = netReturnCriterion.calculate(series, tradingRecord);
        if (maxDrawdown.isZero()) {
            return netReturn; // perfect equity curve
        }
        return netReturn.dividedBy(maxDrawdown); // regular RoMaD
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
