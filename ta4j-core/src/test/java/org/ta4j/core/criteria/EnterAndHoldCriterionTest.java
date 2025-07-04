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
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossPercentageCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class EnterAndHoldCriterionTest extends AbstractCriterionTest {

    public EnterAndHoldCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 1 ? new EnterAndHoldCriterion((AnalysisCriterion) params[0])
                : new EnterAndHoldCriterion((TradeType) params[0], (AnalysisCriterion) params[1]), numFactory);
    }

    @Test
    public void calculateWithOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(105d / 100, buyAndHoldReturn.calculate(series, position));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(0.95, sellAndHoldReturn.calculate(series, position));

        // buy and hold of PnlPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion());
        assertNumEquals(5, buyAndHoldPnlPercentage.calculate(series, position));

        // sell and hold of PnlPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL, new NetProfitLossPercentageCriterion());
        assertNumEquals(-5, sellAndHoldPnlPercentage.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(0.7, buyAndHoldReturn.calculate(series, new BaseTradingRecord()));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(1.3, sellAndHoldReturn.calculate(series, new BaseTradingRecord()));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion());
        assertNumEquals(-30, buyAndHoldPnlPercentage.calculate(series, new BaseTradingRecord()));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL, new NetProfitLossPercentageCriterion());
        assertNumEquals(30, sellAndHoldPnlPercentage.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(1.05, buyAndHoldReturn.calculate(series, tradingRecord));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(0.95, sellAndHoldReturn.calculate(series, tradingRecord));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion());
        assertNumEquals(5, buyAndHoldPnlPercentage.calculate(series, tradingRecord));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL, new NetProfitLossPercentageCriterion());
        assertNumEquals(-5, sellAndHoldPnlPercentage.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(0.7, buyAndHoldReturn.calculate(series, tradingRecord));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(1.3, sellAndHoldReturn.calculate(series, tradingRecord));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion());
        assertNumEquals(-30, buyAndHoldPnlPercentage.calculate(series, tradingRecord));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL, new NetProfitLossPercentageCriterion());
        assertNumEquals(30, sellAndHoldPnlPercentage.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithAmount() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        // 2 winning positions
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        // buy and hold with amount of 10 (which means the pnl is 10 times higher than
        // amount of 1)
        var buyAndHoldPnl = new EnterAndHoldCriterion(TradeType.BUY, new NetProfitLossCriterion(),
                BigDecimal.valueOf(10));
        var buyAndHoldPnlValue = buyAndHoldPnl.calculate(series, tradingRecord);
        assertNumEquals(5 * 10d, buyAndHoldPnlValue);
    }

    @Test
    public void betterThan() {

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertTrue(buyAndHoldReturn.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(buyAndHoldReturn.betterThan(numOf(0.6), numOf(0.9)));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertTrue(sellAndHoldReturn.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(sellAndHoldReturn.betterThan(numOf(0.6), numOf(0.9)));

        // buy and hold of PnlPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion());
        assertTrue(buyAndHoldPnlPercentage.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(buyAndHoldPnlPercentage.betterThan(numOf(0.6), numOf(0.9)));

        // sell and hold of PnlPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL, new NetProfitLossPercentageCriterion());
        assertTrue(sellAndHoldPnlPercentage.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(sellAndHoldPnlPercentage.betterThan(numOf(0.6), numOf(0.9)));
    }

    @Test
    public void calculateWithTransactionCosts() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        var txCostModel = new LinearTransactionCostModel(0.05);
        var holdingCostModel = new ZeroCostModel();

        var record = new BaseTradingRecord(TradeType.BUY, txCostModel, holdingCostModel);
        var amount = series.numFactory().one();
        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        var criterion = getCriterion(new NetProfitLossCriterion());
        // net = (110-100) − (100*0.05 + 110*0.05) = 10 - 10.5 = −0.5
        assertNumEquals(-0.5, criterion.calculate(series, record));

        record = new BaseTradingRecord(TradeType.BUY);
        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        // net = (110-100) = 10
        assertNumEquals(10, criterion.calculate(series, record));
    }

}
