/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

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

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

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
        assertNumEquals(1.05, buyAndHoldReturn.calculate(series, position));

        var buyAndHoldReturnPercentage = getCriterion(new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(5.0, buyAndHoldReturnPercentage.calculate(series, position));

        var buyAndHoldReturnDecimal = getCriterion(new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.05, buyAndHoldReturnDecimal.calculate(series, position));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(0.95, sellAndHoldReturn.calculate(series, position));

        var sellAndHoldReturnPercentage = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-5.0, sellAndHoldReturnPercentage.calculate(series, position));

        var sellAndHoldReturnDecimal = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.05, sellAndHoldReturnDecimal.calculate(series, position));

        // buy and hold of PnlPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.05, buyAndHoldPnlPercentage.calculate(series, position));

        var buyAndHoldPnlPercentagePercentage = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(5.0, buyAndHoldPnlPercentagePercentage.calculate(series, position));

        var buyAndHoldPnlPercentageDecimal = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.05, buyAndHoldPnlPercentageDecimal.calculate(series, position));

        // sell and hold of PnlPercentageCriterion
        var sellAndHoldPnlPercentageDecimal = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.05, sellAndHoldPnlPercentageDecimal.calculate(series, position));

        var sellAndHoldPnlPercentagePercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-5.0, sellAndHoldPnlPercentagePercentage.calculate(series, position));

        var sellAndHoldPnlPercentageMultiplicative = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(0.95, sellAndHoldPnlPercentageMultiplicative.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(0.7, buyAndHoldReturn.calculate(series, new BaseTradingRecord()));

        var buyAndHoldReturnPercentage = getCriterion(new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-30.0, buyAndHoldReturnPercentage.calculate(series, new BaseTradingRecord()));

        var buyAndHoldReturnDecimal = getCriterion(new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.30, buyAndHoldReturnDecimal.calculate(series, new BaseTradingRecord()));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(1.3, sellAndHoldReturn.calculate(series, new BaseTradingRecord()));

        var sellAndHoldReturnPercentage = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(30.0, sellAndHoldReturnPercentage.calculate(series, new BaseTradingRecord()));

        var sellAndHoldReturnDecimal = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.30, sellAndHoldReturnDecimal.calculate(series, new BaseTradingRecord()));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.30, buyAndHoldPnlPercentage.calculate(series, new BaseTradingRecord()));

        var buyAndHoldPnlPercentagePercentage = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-30.0, buyAndHoldPnlPercentagePercentage.calculate(series, new BaseTradingRecord()));

        var buyAndHoldPnlPercentageMultiplicative = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(0.7, buyAndHoldPnlPercentageMultiplicative.calculate(series, new BaseTradingRecord()));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.30, sellAndHoldPnlPercentage.calculate(series, new BaseTradingRecord()));

        var sellAndHoldPnlPercentagePercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(30.0, sellAndHoldPnlPercentagePercentage.calculate(series, new BaseTradingRecord()));

        var sellAndHoldPnlPercentageMultiplicative = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(1.3, sellAndHoldPnlPercentageMultiplicative.calculate(series, new BaseTradingRecord()));
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

        var buyAndHoldReturnPercentage = getCriterion(new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(5.0, buyAndHoldReturnPercentage.calculate(series, tradingRecord));

        var buyAndHoldReturnDecimal = getCriterion(new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.05, buyAndHoldReturnDecimal.calculate(series, tradingRecord));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(0.95, sellAndHoldReturn.calculate(series, tradingRecord));

        var sellAndHoldReturnPercentage = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-5.0, sellAndHoldReturnPercentage.calculate(series, tradingRecord));

        var sellAndHoldReturnDecimal = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.05, sellAndHoldReturnDecimal.calculate(series, tradingRecord));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.05, buyAndHoldPnlPercentage.calculate(series, tradingRecord));

        var buyAndHoldPnlPercentagePercentage = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(5.0, buyAndHoldPnlPercentagePercentage.calculate(series, tradingRecord));

        var buyAndHoldPnlPercentageMultiplicative = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(1.05, buyAndHoldPnlPercentageMultiplicative.calculate(series, tradingRecord));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.05, sellAndHoldPnlPercentage.calculate(series, tradingRecord));

        var sellAndHoldPnlPercentagePercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-5.0, sellAndHoldPnlPercentagePercentage.calculate(series, tradingRecord));

        var sellAndHoldPnlPercentageMultiplicative = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(0.95, sellAndHoldPnlPercentageMultiplicative.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertNumEquals(0.7, buyAndHoldReturn.calculate(series, tradingRecord));

        var buyAndHoldReturnPercentage = getCriterion(new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-30.0, buyAndHoldReturnPercentage.calculate(series, tradingRecord));

        var buyAndHoldReturnDecimal = getCriterion(new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.30, buyAndHoldReturnDecimal.calculate(series, tradingRecord));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertNumEquals(1.3, sellAndHoldReturn.calculate(series, tradingRecord));

        var sellAndHoldReturnPercentage = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(30.0, sellAndHoldReturnPercentage.calculate(series, tradingRecord));

        var sellAndHoldReturnDecimal = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.30, sellAndHoldReturnDecimal.calculate(series, tradingRecord));

        // buy and hold of NetProfitLossPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(-0.30, buyAndHoldPnlPercentage.calculate(series, tradingRecord));

        var buyAndHoldPnlPercentagePercentage = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(-30.0, buyAndHoldPnlPercentagePercentage.calculate(series, tradingRecord));

        var buyAndHoldPnlPercentageMultiplicative = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(0.7, buyAndHoldPnlPercentageMultiplicative.calculate(series, tradingRecord));

        // sell and hold of NetProfitLossPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertNumEquals(0.30, sellAndHoldPnlPercentage.calculate(series, tradingRecord));

        var sellAndHoldPnlPercentagePercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertNumEquals(30.0, sellAndHoldPnlPercentagePercentage.calculate(series, tradingRecord));

        var sellAndHoldPnlPercentageMultiplicative = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertNumEquals(1.3, sellAndHoldPnlPercentageMultiplicative.calculate(series, tradingRecord));
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
        assertNumEquals(50.0, buyAndHoldPnlValue);
    }

    @Test
    public void betterThan() {

        // buy and hold of GrossReturnCriterion
        var buyAndHoldReturn = getCriterion(new GrossReturnCriterion());
        assertTrue(buyAndHoldReturn.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(buyAndHoldReturn.betterThan(numOf(0.6), numOf(0.9)));

        var buyAndHoldReturnPercentage = getCriterion(new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertTrue(buyAndHoldReturnPercentage.betterThan(numOf(30.0), numOf(10.0)));
        assertFalse(buyAndHoldReturnPercentage.betterThan(numOf(-40.0), numOf(-10.0)));

        var buyAndHoldReturnDecimal = getCriterion(new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertTrue(buyAndHoldReturnDecimal.betterThan(numOf(0.3), numOf(0.1)));
        assertFalse(buyAndHoldReturnDecimal.betterThan(numOf(-0.4), numOf(-0.1)));

        // sell and hold of GrossReturnCriterion
        var sellAndHoldReturn = getCriterion(TradeType.SELL, new GrossReturnCriterion());
        assertTrue(sellAndHoldReturn.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(sellAndHoldReturn.betterThan(numOf(0.6), numOf(0.9)));

        var sellAndHoldReturnPercentage = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        assertTrue(sellAndHoldReturnPercentage.betterThan(numOf(30.0), numOf(10.0)));
        assertFalse(sellAndHoldReturnPercentage.betterThan(numOf(-40.0), numOf(-10.0)));

        var sellAndHoldReturnDecimal = getCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
        assertTrue(sellAndHoldReturnDecimal.betterThan(numOf(0.3), numOf(0.1)));
        assertFalse(sellAndHoldReturnDecimal.betterThan(numOf(-0.4), numOf(-0.1)));

        // buy and hold of PnlPercentageCriterion
        var buyAndHoldPnlPercentage = getCriterion(new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertTrue(buyAndHoldPnlPercentage.betterThan(numOf(0.3), numOf(0.1)));
        assertFalse(buyAndHoldPnlPercentage.betterThan(numOf(-0.4), numOf(-0.1)));

        var buyAndHoldPnlPercentagePercentage = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertTrue(buyAndHoldPnlPercentagePercentage.betterThan(numOf(30.0), numOf(10.0)));
        assertFalse(buyAndHoldPnlPercentagePercentage.betterThan(numOf(-40.0), numOf(-10.0)));

        var buyAndHoldPnlPercentageMultiplicative = getCriterion(
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertTrue(buyAndHoldPnlPercentageMultiplicative.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(buyAndHoldPnlPercentageMultiplicative.betterThan(numOf(0.6), numOf(0.9)));

        // sell and hold of PnlPercentageCriterion
        var sellAndHoldPnlPercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL));
        assertTrue(sellAndHoldPnlPercentage.betterThan(numOf(0.3), numOf(0.1)));
        assertFalse(sellAndHoldPnlPercentage.betterThan(numOf(-0.4), numOf(-0.1)));

        var sellAndHoldPnlPercentagePercentage = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.PERCENTAGE));
        assertTrue(sellAndHoldPnlPercentagePercentage.betterThan(numOf(30.0), numOf(10.0)));
        assertFalse(sellAndHoldPnlPercentagePercentage.betterThan(numOf(-40.0), numOf(-10.0)));

        var sellAndHoldPnlPercentageMultiplicative = getCriterion(TradeType.SELL,
                new NetProfitLossPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE));
        assertTrue(sellAndHoldPnlPercentageMultiplicative.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(sellAndHoldPnlPercentageMultiplicative.betterThan(numOf(0.6), numOf(0.9)));
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
