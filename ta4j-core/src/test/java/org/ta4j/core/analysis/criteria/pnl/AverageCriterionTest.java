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
package org.ta4j.core.analysis.criteria.pnl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position.PositionType;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class AverageCriterionTest extends AbstractCriterionTest {

    public AverageCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new AverageCriterion((PositionType) params[0]), numFunction);
    }

    @Test
    public void calculateOnlyWithProfitPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion avgProfit = getCriterion(PositionType.PROFIT);
        assertNumEquals(7.5, avgProfit.calculate(series, tradingRecord));

        AnalysisCriterion avgLoss = getCriterion(PositionType.LOSS);
        assertNumEquals(0, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion avgProfit = getCriterion(PositionType.PROFIT);
        assertNumEquals(0, avgProfit.calculate(series, tradingRecord));

        AnalysisCriterion avgLoss = getCriterion(PositionType.LOSS);
        assertNumEquals(-17.5, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithShortPositions() {
        MockBarSeries seriesProfit = new MockBarSeries(numFunction, 100, 85, 80, 70, 100, 95);
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.sellAt(0, seriesProfit),
                Trade.buyAt(1, seriesProfit), Trade.sellAt(2, seriesProfit), Trade.buyAt(5, seriesProfit));

        AnalysisCriterion avgProfit = getCriterion(PositionType.PROFIT);
        assertNumEquals(15, avgProfit.calculate(seriesProfit, tradingRecordProfit));

        MockBarSeries seriesLoss = new MockBarSeries(numFunction, 95, 100, 70, 80, 85, 100);
        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.sellAt(0, seriesLoss), Trade.buyAt(1, seriesLoss),
                Trade.sellAt(2, seriesLoss), Trade.buyAt(5, seriesLoss));

        AnalysisCriterion avgLoss = getCriterion(PositionType.LOSS);
        assertNumEquals(-17.5, avgLoss.calculate(seriesLoss, tradingRecordLoss));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterionProfit = getCriterion(PositionType.PROFIT);
        assertTrue(criterionProfit.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterionProfit.betterThan(numOf(1.5), numOf(2.0)));

        AnalysisCriterion criterionLoss = getCriterion(PositionType.LOSS);
        assertTrue(criterionLoss.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterionLoss.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionType.PROFIT), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction,
                getCriterion(PositionType.LOSS), 0);
    }
}