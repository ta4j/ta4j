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

package org.ta4j.core.criteria.pnl;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.CriterionFactory;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Base test for profit and loss criteria. Holds all scenarios while subclasses
 * perform the assertions.
 */
public abstract class AbstractPnlCriterionTest extends AbstractCriterionTest {

    protected AbstractPnlCriterionTest(CriterionFactory factory, NumFactory numFactory) {
        super(factory, numFactory);
    }

    @Test
    public void calculateWithProfits() {
        // 100 -> 105 (-1 -1) = 3, 100 -> 120 (-1 -1) = 18
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 100, 80, 85, 120).build();
        var cost = new FixedTransactionCostModel(1);
        var holdingCost = new ZeroCostModel();
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, holdingCost);

        record.enter(0, series.getBar(0).getClosePrice(), numOf(1));
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        record.enter(2, series.getBar(2).getClosePrice(), numOf(1));
        record.exit(5, series.getBar(5).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = getCriterion();
        handleCalculateWithProfits(criterion.calculate(series, record));
    }

    @Test
    public void calculateWithLosses() {
        // 100 -> 95 (-1%) and 100 -> 70 (-1%), both losing trades
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var cost = new LinearTransactionCostModel(0.01);
        var holdingCost = new ZeroCostModel();
        var record = new BaseTradingRecord(Trade.TradeType.BUY, cost, holdingCost);

        record.enter(0, series.getBar(0).getClosePrice(), numOf(1));
        record.exit(1, series.getBar(1).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        record.enter(2, series.getBar(2).getClosePrice(), numOf(1));
        record.exit(5, series.getBar(5).getClosePrice(), record.getCurrentPosition().getEntry().getAmount());

        var criterion = getCriterion();
        handleCalculateWithLosses(criterion.calculate(series, record));
    }

    @Test
    public void calculateOnlyWithProfitPositions() {
        // two winning long positions: 100->110 and 100->105
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series));

        var criterion = getCriterion();
        handleCalculateOnlyWithProfitPositions(criterion.calculate(series, record));
    }

    @Test
    public void calculateOnlyWithProfitPositions2() {
        // winning long positions without explicit costs
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 100, 80, 85, 120).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(5, series));

        var criterion = getCriterion();
        handleCalculateOnlyWithProfitPositions2(criterion.calculate(series, record));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        // two losing long positions 100->95 and 100->70
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(5, series));

        var criterion = getCriterion();
        handleCalculateOnlyWithLossPositions(criterion.calculate(series, record));
    }

    @Test
    public void calculateProfitWithShortPositions() {
        // shorting 95->100 and 70->100
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(95, 100, 70, 80, 85, 100).build();
        var record = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series), Trade.sellAt(2, series),
                Trade.buyAt(5, series));

        var criterion = getCriterion();
        handleCalculateProfitWithShortPositions(criterion.calculate(series, record));
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        handleBetterThan(criterion);
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        handleCalculateOneOpenPositionShouldReturnZero();
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        AnalysisCriterion criterion = getCriterion();
        handleCalculateWithNoPositions(criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOpenedPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion criterion = getCriterion();
        handleCalculateWithOpenedPosition(criterion.calculate(series, record));
    }

    protected abstract void handleCalculateWithProfits(Num result);

    protected abstract void handleCalculateWithLosses(Num result);

    protected abstract void handleCalculateOnlyWithProfitPositions(Num result);

    protected abstract void handleCalculateOnlyWithProfitPositions2(Num result);

    protected abstract void handleCalculateOnlyWithLossPositions(Num result);

    protected abstract void handleCalculateProfitWithShortPositions(Num result);

    protected abstract void handleBetterThan(AnalysisCriterion criterion);

    protected abstract void handleCalculateOneOpenPositionShouldReturnZero();

    protected abstract void handleCalculateWithOpenedPosition(Num result);

    protected abstract void handleCalculateWithNoPositions(Num result);
}
