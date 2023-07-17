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
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class ProfitCriterionTest extends AbstractCriterionTest {

    public ProfitCriterionTest(Function<Number, Num> numFunction) {
        super(params -> new ProfitCriterion((boolean) params[0]), numFunction);
    }

    @Test
    public void calculateComparingIncludingVsExcludingCosts() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 100, 80, 85, 120);
        FixedTransactionCostModel transactionCost = new FixedTransactionCostModel(1);
        ZeroCostModel holdingCost = new ZeroCostModel();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, transactionCost, holdingCost);

        // entry price = 100 (cost = 1) => netPrice = 101, grossPrice = 100
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numOf(1));
        // exit price = 105 (cost = 1) => netPrice = 104, grossPrice = 105
        tradingRecord.exit(1, series.getBar(1).getClosePrice(),
                tradingRecord.getCurrentPosition().getEntry().getAmount());

        // entry price = 100 (cost = 1) => netPrice = 101, grossPrice = 100
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), numOf(1));
        // exit price = 120 (cost = 1) => netPrice = 119, grossPrice = 120
        tradingRecord.exit(5, series.getBar(5).getClosePrice(),
                tradingRecord.getCurrentPosition().getEntry().getAmount());

        // include costs, i.e. profit - costs:
        // [(104 - 101)] + [(119 - 101)] = 3 + 18 = +21 profit
        // [(105 - 100)] + [(120 - 100)] = 5 + 20 = +25 profit - 4 = +21 profit
        AnalysisCriterion profitIncludingCosts = getCriterion(false);
        assertNumEquals(21, profitIncludingCosts.calculate(series, tradingRecord));

        // exclude costs, i.e. costs are not contained:
        // [(105 - 100)] + [(120 - 100)] = 5 + 20 = +25 profit
        AnalysisCriterion profitExcludingCosts = getCriterion(true);
        assertNumEquals(25, profitExcludingCosts.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithProfitPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion profit = getCriterion(false);
        assertNumEquals(15, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithProfitPositions2() {
        MockBarSeries series = new MockBarSeries(numFunction, 100, 105, 100, 80, 85, 120);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion profit = getCriterion(false);
        assertNumEquals(25, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithShortPositions() {
        MockBarSeries series = new MockBarSeries(numFunction, 95, 100, 70, 80, 85, 100);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(5, series));

        AnalysisCriterion profit = getCriterion(false);
        assertNumEquals(0, profit.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion(false);
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFunction, getCriterion(false), 0);
    }
}
