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
package org.ta4j.core.analysis.criteria;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Position.PositionType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of position criterion.
 */
public class NumberOfPositionsCriterion extends AbstractAnalysisCriterion {

    private final PositionType positionType;

    /**
     * Constructor.
     * 
     * <p>
     * For counting the number of all positions.
     */
    public NumberOfPositionsCriterion() {
        this.positionType = null;
    }

    /**
     * Constructor.
     * 
     * @param positionType the PositionType to select either profit or loss
     *                     positions
     */
    public NumberOfPositionsCriterion(PositionType positionType) {
        this.positionType = Objects.requireNonNull(positionType);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (positionType == PositionType.PROFIT) {
            return position.hasProfit() ? series.numOf(1) : series.numOf(0);
        }
        if (positionType == PositionType.LOSS) {
            return position.hasLoss() ? series.numOf(1) : series.numOf(0);
        }
        return series.numOf(1);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (positionType == PositionType.PROFIT) {
            long numberOfWinningPositions = tradingRecord.getPositions().stream().filter(Position::hasProfit).count();
            return series.numOf(numberOfWinningPositions);
        }
        if (positionType == PositionType.LOSS) {
            long numberOfLosingPositions = tradingRecord.getPositions().stream().filter(Position::hasLoss).count();
            return series.numOf(numberOfLosingPositions);
        }
        return series.numOf(tradingRecord.getPositionCount());

    }

    /**
     * <ul>
     * <li>For {@link PositionType#PROFIT}: The higher the criterion value, the
     * better.
     * <li>For {@link PositionType#LOSS}: The lower the criterion value, the better.
     * <li>For no give {@link PositionType}: The lower the criterion value, the
     * better.
     * </ul>
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        if (positionType == PositionType.PROFIT) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
        if (positionType == PositionType.LOSS) {
            return criterionValue1.isLessThan(criterionValue2);
        }
        return criterionValue1.isLessThan(criterionValue2);
    }

    /** @return the {@link #positionType} */
    public PositionType getPositionType() {
        return positionType;
    }
}
