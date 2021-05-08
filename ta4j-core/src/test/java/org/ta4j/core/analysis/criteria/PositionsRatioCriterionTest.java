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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class PositionsRatioCriterionTest extends AbstractCriterionTest {

    public PositionsRatioCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new PositionsRatioCriterion((PositionFilter) params[0]), numFunction);
    }

    @Test
    public void calculate() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        AnalysisCriterion averageProfit = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(2d / 3, averageProfit.calculate(series, tradingRecord));

        AnalysisCriterion averageLoss = getCriterion(PositionFilter.LOSS);
        assertNumEquals(1d / 3, averageLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithShortPositions() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series));

        AnalysisCriterion averageProfit = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(0.5, averageProfit.calculate(series, tradingRecord));

        AnalysisCriterion averageLoss = getCriterion(PositionFilter.LOSS);
        assertNumEquals(0.5, averageLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnePosition() {
        BarSeries series = new MockBarSeries(numFunction, 100d, 95d, 102d, 105d, 97d, 113d);
        Position positionProfit = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        AnalysisCriterion averageProfit = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(numOf(0), averageProfit.calculate(series, positionProfit));

        positionProfit = new Position(Trade.buyAt(1, series), Trade.sellAt(2, series));
        assertNumEquals(1, averageProfit.calculate(series, positionProfit));

        Position positionLoss = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion averageLoss = getCriterion(PositionFilter.LOSS);
        assertNumEquals(numOf(1), averageLoss.calculate(series, positionLoss));

        positionLoss = new Position(Trade.buyAt(1, series), Trade.sellAt(2, series));
        assertNumEquals(0, averageLoss.calculate(series, positionLoss));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterionProfit = getCriterion(PositionFilter.PROFIT);
        assertTrue(criterionProfit.betterThan(numOf(12), numOf(8)));
        assertFalse(criterionProfit.betterThan(numOf(8), numOf(12)));

        AnalysisCriterion criterionLoss = getCriterion(PositionFilter.LOSS);
        assertTrue(criterionLoss.betterThan(numOf(8), numOf(12)));
        assertFalse(criterionLoss.betterThan(numOf(12), numOf(8)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionFilter.PROFIT), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionFilter.LOSS), 0);
    }
}
