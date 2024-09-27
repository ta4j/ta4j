/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
import org.ta4j.core.num.Num;

/**
 * Calculates the percentage of losing or winning positions, returned in decimal
 * format.
 *
 * <ul>
 * <li>For {@link #positionFilter} = {@link PositionFilter#PROFIT}:
 * <code>number of winning positions / total number of positions</code>
 * <li>For {@link #positionFilter} = {@link PositionFilter#LOSS}:
 * <code>number of losing positions / total number of positions</code>
 * </ul>
 */
public class PositionsRatioCriterion extends AbstractAnalysisCriterion {

    private final PositionFilter positionFilter;
    private final AnalysisCriterion numberOfPositionsCriterion;

    /**
     * @return {@link PositionsRatioCriterion} with {@link PositionFilter#PROFIT}
     */
    public static PositionsRatioCriterion WinningPositionsRatioCriterion() {
        return new PositionsRatioCriterion(PositionFilter.PROFIT);
    }

    /**
     * @return {@link PositionsRatioCriterion} with {@link PositionFilter#LOSS}
     */
    public static PositionsRatioCriterion LosingPositionsRatioCriterion() {
        return new PositionsRatioCriterion(PositionFilter.LOSS);
    }

    /**
     * Constructor.
     *
     * @param positionFilter consider either the winning or losing positions
     */
    public PositionsRatioCriterion(PositionFilter positionFilter) {
        this.positionFilter = positionFilter;
        if (positionFilter == PositionFilter.PROFIT) {
            this.numberOfPositionsCriterion = new NumberOfWinningPositionsCriterion();
        } else {
            this.numberOfPositionsCriterion = new NumberOfLosingPositionsCriterion();
        }

    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return numberOfPositionsCriterion.calculate(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        return numberOfPositions.dividedBy(series.numFactory().numOf(tradingRecord.getPositionCount()));
    }

    /**
     * <ul>
     * <li>For {@link PositionFilter#PROFIT}: The higher the criterion value, the
     * better.
     * <li>For {@link PositionFilter#LOSS}: The lower the criterion value, the
     * better.
     * </ul>
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return positionFilter == PositionFilter.PROFIT ? criterionValue1.isGreaterThan(criterionValue2)
                : criterionValue1.isLessThan(criterionValue2);
    }
}
