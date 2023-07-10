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
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class NumberOfConsecutivePositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfConsecutivePositionsCriterionTest(Function<Number, Num> numFunction) {
        super(params -> new NumberOfConsecutivePositionsCriterion((PositionFilter) params[0]), numFunction);
    }

    @Test
    public void calculateWithNoPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);

        assertNumEquals(0, getCriterion(PositionFilter.LOSS).calculate(series, new BaseTradingRecord()));
        assertNumEquals(0, getCriterion(PositionFilter.PROFIT).calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoLongPositions() {
        MockBarSeries seriesLoss = new MockBarSeries(numFunction, 110, 105, 100, 90, 80, 140);
        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.buyAt(0, seriesLoss), Trade.sellAt(2, seriesLoss),
                Trade.buyAt(3, seriesLoss), Trade.sellAt(4, seriesLoss));
        assertNumEquals(2, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, tradingRecordLoss));

        MockBarSeries seriesProfit = new MockBarSeries(numFunction, 100, 105, 110, 120, 130, 140);
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.buyAt(1, seriesProfit),
                Trade.sellAt(3, seriesProfit), Trade.buyAt(3, seriesProfit), Trade.sellAt(4, seriesProfit));
        assertNumEquals(2, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, tradingRecordProfit));
    }

    @Test
    public void calculateWithOneLongPosition() {
        MockBarSeries seriesLoss = new MockBarSeries(numFunction, 110, 105, 100, 90, 95, 105);
        Position positionLoss = new Position(Trade.buyAt(1, seriesLoss), Trade.sellAt(3, seriesLoss));
        assertNumEquals(1, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, positionLoss));

        MockBarSeries seriesProfit = new MockBarSeries(numFunction, 100, 105, 110, 120, 95, 105);
        Position positionProfit = new Position(Trade.buyAt(1, seriesProfit), Trade.sellAt(3, seriesProfit));
        assertNumEquals(1, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, positionProfit));
    }

    @Test
    public void calculateWithTwoShortPositions() {
        MockBarSeries seriesLoss = new MockBarSeries(numFunction, 100, 90, 110, 120, 95, 105);
        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.sellAt(0, seriesLoss), Trade.buyAt(1, seriesLoss),
                Trade.sellAt(3, seriesLoss), Trade.buyAt(5, seriesLoss));
        assertNumEquals(0, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, tradingRecordLoss));

        MockBarSeries seriesProfit = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.sellAt(0, seriesProfit),
                Trade.buyAt(1, seriesProfit), Trade.sellAt(3, seriesProfit), Trade.buyAt(5, seriesProfit));
        assertNumEquals(0, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, tradingRecordProfit));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterionLoss = getCriterion(PositionFilter.LOSS);
        assertTrue(criterionLoss.betterThan(numOf(3), numOf(6)));
        assertFalse(criterionLoss.betterThan(numOf(7), numOf(4)));

        AnalysisCriterion criterionProfit = getCriterion(PositionFilter.PROFIT);
        assertFalse(criterionProfit.betterThan(numOf(3), numOf(6)));
        assertTrue(criterionProfit.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionFilter.LOSS), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionFilter.PROFIT), 0);
    }
}