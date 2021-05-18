/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion;
import org.ta4j.core.criteria.pnl.ProfitLossCriterion;
import org.ta4j.core.num.Num;

/**
 * The SQN ("System Quality Number") Criterion.
 * 
 * @see <a href=
 *      "https://indextrader.com.au/van-tharps-sqn/">https://indextrader.com.au/van-tharps-sqn/</a>
 */
public class SqnCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion criterion;
    private final StandardDeviationCriterion standardDeviationCriterion;
    private final NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();

    /**
     * The number to be used for the part of <code>√(numberOfPositions)</code>
     * within the SQN-Formula when there are more than 100 trades. If this value is
     * <code>null</code>, then the number of positions calculated by
     * {@link #numberOfPositionsCriterion} is used instead.
     */
    private final Integer nPositions;

    /**
     * Constructor.
     * 
     * <p>
     * Uses ProfitLossCriterion for {@link #criterion}.
     */
    public SqnCriterion() {
        this(new ProfitLossCriterion());
    }

    /**
     * Constructor.
     * 
     * @param criterion the Criterion (e.g. ProfitLossCriterion or
     *                  ExpectancyCriterion)
     */
    public SqnCriterion(AnalysisCriterion criterion) {
        this(criterion, null);
    }

    /**
     * Constructor.
     * 
     * @param criterion  the Criterion (e.g. ProfitLossCriterion or
     *                   ExpectancyCriterion)
     * @param nPositions the {@link #nPositions} (optional)
     */
    public SqnCriterion(AnalysisCriterion criterion, Integer nPositions) {
        this.criterion = criterion;
        this.nPositions = nPositions;
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        Num pnl = criterion.calculate(series, position);
        Num avgPnl = pnl.dividedBy(numberOfPositions);
        Num stdDevPnl = standardDeviationCriterion.calculate(series, position);
        if (stdDevPnl.isZero()) {
            return series.numOf(0);
        }
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
        return avgPnl.dividedBy(stdDevPnl).multipliedBy(numberOfPositions.sqrt());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty())
            return series.numOf(0);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        Num pnl = criterion.calculate(series, tradingRecord);
        Num avgPnl = pnl.dividedBy(numberOfPositions);
        Num stdDevPnl = standardDeviationCriterion.calculate(series, tradingRecord);
        if (stdDevPnl.isZero()) {
            return series.numOf(0);
        }
        if (nPositions != null && numberOfPositions.isGreaterThan(series.numOf(100))) {
            numberOfPositions = series.numOf(nPositions);
        }
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
        return avgPnl.dividedBy(stdDevPnl).multipliedBy(numberOfPositions.sqrt());
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
