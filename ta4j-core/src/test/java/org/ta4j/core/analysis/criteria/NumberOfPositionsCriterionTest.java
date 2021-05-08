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
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Position.PositionType;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class NumberOfPositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfPositionsCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> params.length == 0 ? new NumberOfPositionsCriterion()
                : new NumberOfPositionsCriterion((PositionType) params[0]), numFunction);
    }

    @Test
    public void calculateWithNoPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0, buyAndHold.calculate(series, new BaseTradingRecord()));
        assertNumEquals(0, getCriterion(PositionType.PROFIT).calculate(series, new BaseTradingRecord()));
        assertNumEquals(0, getCriterion(PositionType.LOSS).calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(2, buyAndHold.calculate(series, tradingRecord));
        assertNumEquals(2, getCriterion(PositionType.PROFIT).calculate(series, tradingRecord));

        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series));
        assertNumEquals(2, getCriterion(PositionType.LOSS).calculate(series, tradingRecordLoss));
    }

    @Test
    public void calculateWithOnePosition() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        Position position = new Position();
        AnalysisCriterion positionsCriterion = getCriterion();

        assertNumEquals(1, positionsCriterion.calculate(series, position));

        Position positionProfit = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));
        assertNumEquals(1, getCriterion(PositionType.PROFIT).calculate(series, positionProfit));

        Position positionLoss = new Position(Trade.buyAt(1, series), Trade.sellAt(3, series));
        assertNumEquals(1, getCriterion(PositionType.LOSS).calculate(series, positionLoss));
    }

    @Test
    public void calculateWithTwoShortPositions() {
        MockBarSeries seriesProfit = new MockBarSeries(numFunction, 110, 105, 110, 100, 95, 105);
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.sellAt(0, seriesProfit),
                Trade.buyAt(1, seriesProfit), Trade.sellAt(2, seriesProfit), Trade.buyAt(4, seriesProfit));
        assertNumEquals(2, getCriterion(PositionType.PROFIT).calculate(seriesProfit, tradingRecordProfit));

        MockBarSeries seriesLoss = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, seriesLoss), Trade.buyAt(1, seriesLoss),
                Trade.sellAt(3, seriesLoss), Trade.buyAt(5, seriesLoss));
        assertNumEquals(2, getCriterion().calculate(seriesLoss, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(7), numOf(4)));

        AnalysisCriterion criterionProfit = getCriterion(PositionType.PROFIT);
        assertTrue(criterionProfit.betterThan(numOf(6), numOf(3)));
        assertFalse(criterionProfit.betterThan(numOf(4), numOf(7)));

        AnalysisCriterion criterionLoss = getCriterion(PositionType.LOSS);
        assertTrue(criterionLoss.betterThan(numOf(3), numOf(6)));
        assertFalse(criterionLoss.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionType.PROFIT), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionType.LOSS), 0);
    }
}
