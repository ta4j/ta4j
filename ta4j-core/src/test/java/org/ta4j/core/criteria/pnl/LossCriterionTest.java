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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class LossCriterionTest extends AbstractCriterionTest {

    public LossCriterionTest(Function<Number, Num> numFunction) {
        super(params -> new LossCriterion((boolean) params[0]), numFunction);
    }

    @Test
    public void calculateComparingIncludingVsExcludingCosts() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 95, 100, 80, 85, 70);
        LinearTransactionCostModel transactionCost = new LinearTransactionCostModel(0.01);
        ZeroCostModel holdingCost = new ZeroCostModel();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, transactionCost, holdingCost);

        // entry price = 100 (cost = 100*0.01 = 1) => netPrice = 101, grossPrice = 100
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numOf(1));
        // exit price = 95 (cost = 95*0.01 = 0.95) => netPrice = 94.05, grossPrice = 95
        tradingRecord.exit(1, series.getBar(1).getClosePrice(),
                tradingRecord.getCurrentPosition().getEntry().getAmount());

        // entry price = 100 (cost = 100*0.01 = 1) => netPrice = 101, grossPrice = 100
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), numOf(1));
        // exit price = 70 (cost = 70*0.01 = 0.70) => netPrice = 69.3, grossPrice = 70
        tradingRecord.exit(5, series.getBar(5).getClosePrice(),
                tradingRecord.getCurrentPosition().getEntry().getAmount());

        // include costs, i.e. loss - costs:
        // [(94.05 - 101)] + [(69.3 - 101)] = -6.95 + (-31.7) = -38.65 loss
        // [(95 - 100)] + [(70 - 100)] = -5 + (-30) = -35 loss - 3.65 = -38.65 loss
        AnalysisCriterion lossIncludingCosts = getCriterion(false);
        assertNumEquals(-38.65, lossIncludingCosts.calculate(series, tradingRecord));

        // exclude costs, i.e. costs are not contained:
        // [(95 - 100)] + [(70 - 100)] = -5 + (-30) = -35 loss
        AnalysisCriterion lossExcludingCosts = getCriterion(true);
        assertNumEquals(-35, lossExcludingCosts.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithProfitPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion loss = getCriterion(true);
        assertNumEquals(0, loss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion loss = getCriterion(true);
        assertNumEquals(-35, loss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithShortPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 95, 100, 70, 80, 85, 100);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(5, series));

        AnalysisCriterion loss = getCriterion(true);
        assertNumEquals(-35, loss.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion(true);
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(true), 0);
    }
}
