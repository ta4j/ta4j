/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

public class TradeTest {

    Trade opEquals1, opEquals2, opNotEquals1, opNotEquals2;

    @Before
    public void setUp() {
        opEquals1 = Trade.buyAt(1, NaN, NaN);
        opEquals2 = Trade.buyAt(1, NaN, NaN);

        opNotEquals1 = Trade.sellAt(1, NaN, NaN);
        opNotEquals2 = Trade.buyAt(2, NaN, NaN);
    }

    @Test
    public void type() {
        assertEquals(TradeType.SELL, opNotEquals1.getType());
        assertFalse(opNotEquals1.isBuy());
        assertTrue(opNotEquals1.isSell());
        assertEquals(TradeType.BUY, opNotEquals2.getType());
        assertTrue(opNotEquals2.isBuy());
        assertFalse(opNotEquals2.isSell());
    }

    @Test
    public void overrideToString() {
        assertEquals(opEquals1.toString(), opEquals2.toString());

        assertNotEquals(opEquals1.toString(), opNotEquals1.toString());
        assertNotEquals(opEquals1.toString(), opNotEquals2.toString());
    }

    @Test
    public void initializeWithCostsTest() {
        var transactionCostModel = new LinearTransactionCostModel(0.05);
        var trade = new BaseTrade(0, TradeType.BUY, DoubleNum.valueOf(100), DoubleNum.valueOf(20),
                transactionCostModel);
        Num expectedCost = DoubleNum.valueOf(100);
        Num expectedValue = DoubleNum.valueOf(2000);
        Num expectedRawPrice = DoubleNum.valueOf(100);
        Num expectedNetPrice = DoubleNum.valueOf(105);

        assertNumEquals(expectedCost, trade.getCost());
        assertNumEquals(expectedValue, trade.getValue());
        assertNumEquals(expectedRawPrice, trade.getPricePerAsset());
        assertNumEquals(expectedNetPrice, trade.getNetPrice());
        assertTrue(transactionCostModel.equals(trade.getCostModel()));
    }

    @Test
    public void simulatedTradeSerializationKeepsCostModelAccessible() throws Exception {
        var numFactory = DoubleNumFactory.getInstance();
        Trade original = Trade.buyAt(0, numFactory.hundred(), numFactory.one(), new FixedTransactionCostModel(1.0));

        byte[] data;
        try (var output = new ByteArrayOutputStream(); var objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(original);
            objectOutput.flush();
            data = output.toByteArray();
        }

        Trade restored;
        try (var input = new ByteArrayInputStream(data); var objectInput = new ObjectInputStream(input)) {
            restored = (Trade) objectInput.readObject();
        }

        assertNotNull(restored.getCostModel());
        assertNumEquals(original.getCost(), restored.getCost());
        assertNumEquals(original.getNetPrice(), restored.getNetPrice());
    }

    @Test
    public void testReturnBarSeriesCloseOnNaN() {
        var series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 95, 100, 80, 85, 130)
                .build();
        Trade trade = new BaseTrade(1, TradeType.BUY, NaN);
        assertNumEquals(DoubleNum.valueOf(95), trade.getPricePerAsset(series));
    }

    @Test
    public void factoryBuyAtSeriesOverloads() {
        var numFactory = DoubleNumFactory.getInstance();
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Trade trade = Trade.buyAt(1, series);
        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(110), trade.getPricePerAsset());
        assertNumEquals(numFactory.one(), trade.getAmount());
        assertNumEquals(numFactory.numOf(110), trade.getNetPrice());
        assertEquals(series.getBar(1).getEndTime(), trade.getFills().getFirst().time());
        assertEquals(ExecutionSide.BUY, trade.getFills().getFirst().side());

        Trade tradeWithAmount = Trade.buyAt(1, series, numFactory.two());
        assertEquals(TradeType.BUY, tradeWithAmount.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithAmount.getAmount());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getNetPrice());

        var costModel = new FixedTransactionCostModel(1.0);
        Trade tradeWithCost = Trade.buyAt(1, series, numFactory.two(), costModel);
        assertEquals(TradeType.BUY, tradeWithCost.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithCost.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithCost.getAmount());
        assertNumEquals(numFactory.one(), tradeWithCost.getCost());
        assertNumEquals(numFactory.numOf(110.5), tradeWithCost.getNetPrice());
        assertTrue(costModel.equals(tradeWithCost.getCostModel()));
    }

    @Test
    public void factorySellAtSeriesOverloads() {
        var numFactory = DoubleNumFactory.getInstance();
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();

        Trade trade = Trade.sellAt(1, series);
        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(110), trade.getPricePerAsset());
        assertNumEquals(numFactory.one(), trade.getAmount());
        assertNumEquals(numFactory.numOf(110), trade.getNetPrice());
        assertEquals(series.getBar(1).getEndTime(), trade.getFills().getFirst().time());
        assertEquals(ExecutionSide.SELL, trade.getFills().getFirst().side());

        Trade tradeWithAmount = Trade.sellAt(1, series, numFactory.two());
        assertEquals(TradeType.SELL, tradeWithAmount.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithAmount.getAmount());
        assertNumEquals(numFactory.numOf(110), tradeWithAmount.getNetPrice());

        var costModel = new FixedTransactionCostModel(1.0);
        Trade tradeWithCost = Trade.sellAt(1, series, numFactory.two(), costModel);
        assertEquals(TradeType.SELL, tradeWithCost.getType());
        assertNumEquals(numFactory.numOf(110), tradeWithCost.getPricePerAsset());
        assertNumEquals(numFactory.two(), tradeWithCost.getAmount());
        assertNumEquals(numFactory.one(), tradeWithCost.getCost());
        assertNumEquals(numFactory.numOf(109.5), tradeWithCost.getNetPrice());
        assertTrue(costModel.equals(tradeWithCost.getCostModel()));
    }

    @Test
    public void factoryBuyAtPriceAmountWithoutCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.buyAt(0, numFactory.numOf(200), numFactory.numOf(4));

        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.zero(), trade.getCost());
        assertNumEquals(numFactory.numOf(200), trade.getNetPrice());
    }

    @Test
    public void factorySellAtPriceAmountWithoutCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.sellAt(0, numFactory.numOf(200), numFactory.numOf(4));

        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.zero(), trade.getCost());
        assertNumEquals(numFactory.numOf(200), trade.getNetPrice());
    }

    @Test
    public void factoryBuyAtPriceAmountWithCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        var costModel = new FixedTransactionCostModel(1.0);
        Trade trade = Trade.buyAt(0, numFactory.numOf(200), numFactory.numOf(4), costModel);

        assertEquals(TradeType.BUY, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.one(), trade.getCost());
        assertNumEquals(numFactory.numOf(200.25), trade.getNetPrice());
        assertTrue(costModel.equals(trade.getCostModel()));
    }

    @Test
    public void factorySellAtPriceAmountWithCostModel() {
        var numFactory = DoubleNumFactory.getInstance();
        var costModel = new FixedTransactionCostModel(1.0);
        Trade trade = Trade.sellAt(0, numFactory.numOf(200), numFactory.numOf(4), costModel);

        assertEquals(TradeType.SELL, trade.getType());
        assertNumEquals(numFactory.numOf(200), trade.getPricePerAsset());
        assertNumEquals(numFactory.numOf(4), trade.getAmount());
        assertNumEquals(numFactory.one(), trade.getCost());
        assertNumEquals(numFactory.numOf(199.75), trade.getNetPrice());
        assertTrue(costModel.equals(trade.getCostModel()));
    }

    @Test
    public void defaultAccessorsReturnNull() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.buyAt(0, numFactory.hundred(), numFactory.one());

        assertNull(trade.getTime());
        assertNull(trade.getId());
        assertNull(trade.getInstrument());
        assertNull(trade.getOrderId());
        assertNull(trade.getCorrelationId());
    }

    @Test
    public void zeroAmountDoesNotAdjustNetPrice() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade buyTrade = Trade.buyAt(0, numFactory.hundred(), numFactory.zero());
        Trade sellTrade = Trade.sellAt(0, numFactory.hundred(), numFactory.zero());

        assertNumEquals(numFactory.hundred(), buyTrade.getNetPrice());
        assertNumEquals(numFactory.hundred(), sellTrade.getNetPrice());
    }

    @Test
    public void defaultTradeFillsExposeSingleExecution() {
        var numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.buyAt(3, numFactory.hundred(), numFactory.two());

        List<TradeFill> fills = trade.getFills();
        assertEquals(1, fills.size());
        assertEquals(3, fills.getFirst().index());
        assertNumEquals(numFactory.hundred(), fills.getFirst().price());
        assertNumEquals(numFactory.two(), fills.getFirst().amount());
        assertEquals(ExecutionSide.BUY, fills.getFirst().side());
        assertNull(fills.getFirst().time());
    }

    @Test
    public void executionFillsOfFallsBackToScalarTradeFields() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        Trade trade = new BaseTrade(3, TradeType.BUY, numFactory.hundred(), numFactory.two()) {
            @Override
            public List<TradeFill> getFills() {
                return List.of();
            }
        };

        List<TradeFill> fills = Trade.executionFillsOf(trade);

        assertEquals(1, fills.size());
        assertEquals(3, fills.getFirst().index());
        assertNumEquals(numFactory.hundred(), fills.getFirst().price());
        assertNumEquals(numFactory.two(), fills.getFirst().amount());
        assertEquals(ExecutionSide.BUY, fills.getFirst().side());
    }

    @Test
    public void fromFillsCreatesBaseTradeForSingleFill() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(2, numFactory.hundred(), numFactory.one())),
                new FixedTransactionCostModel(1.0));

        assertTrue(trade instanceof BaseTrade);
        assertEquals(1, trade.getFills().size());
        assertEquals(2, trade.getIndex());
        assertNumEquals(numFactory.hundred(), trade.getPricePerAsset());
        assertNumEquals(numFactory.one(), trade.getAmount());
    }

    @Test
    public void fromFillsCreatesFillBackedBaseTradeForMultipleFills() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        Trade trade = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(1, numFactory.hundred(), numFactory.one()),
                new TradeFill(2, numFactory.numOf(101), numFactory.one())));

        assertTrue(trade instanceof BaseTrade);
        assertEquals(2, trade.getFills().size());
    }

    @Test
    public void fromFillsRejectsInvalidSingleFill() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        assertThrows(IllegalArgumentException.class,
                () -> Trade.fromFills(TradeType.BUY, List.of(new TradeFill(1, NaN, numFactory.one()))));
        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.zero()))));
        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.minusOne()))));
    }

    @Test
    public void fromFillInfersTypeFromSideAndUsesRecordedFees() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradeFill fill = new TradeFill(4, null, numFactory.hundred(), numFactory.two(), numFactory.numOf(0.5),
                ExecutionSide.SELL, null, null);

        Trade trade = Trade.fromFill(fill);

        assertEquals(TradeType.SELL, trade.getType());
        assertEquals(4, trade.getIndex());
        assertNumEquals(numFactory.hundred(), trade.getPricePerAsset());
        assertNumEquals(numFactory.two(), trade.getAmount());
        assertNumEquals(numFactory.numOf(0.5), trade.getCost());
        assertTrue(trade.getCostModel() instanceof RecordedTradeCostModel);
    }

    @Test
    public void fromFillWithExplicitTypeAcceptsMissingSide() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradeFill fill = new TradeFill(6, null, numFactory.numOf(105), numFactory.one(), numFactory.numOf(0.2), null,
                "order-6", "corr-6");

        Trade trade = Trade.fromFill(TradeType.BUY, fill);

        assertEquals(TradeType.BUY, trade.getType());
        assertEquals(6, trade.getIndex());
        assertEquals("order-6", trade.getOrderId());
        assertEquals("corr-6", trade.getCorrelationId());
        assertNumEquals(numFactory.numOf(0.2), trade.getCost());
        assertEquals(ExecutionSide.BUY, ((BaseTrade) trade).side());
    }

    @Test
    public void fromFillWithExplicitCostModelUsesProvidedCostModel() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        FixedTransactionCostModel costModel = new FixedTransactionCostModel(1.0);
        TradeFill fill = new TradeFill(8, null, numFactory.numOf(110), numFactory.numOf(4), ExecutionSide.BUY);

        Trade trade = Trade.fromFill(fill, costModel);

        assertSame(costModel, trade.getCostModel());
        assertNumEquals(numFactory.one(), trade.getCost());
        assertNumEquals(numFactory.numOf(110.25), trade.getNetPrice());
    }

    @Test
    public void fromFillRejectsSideThatConflictsWithExplicitType() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        TradeFill fill = new TradeFill(9, null, numFactory.hundred(), numFactory.one(), ExecutionSide.SELL);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Trade.fromFill(TradeType.BUY, fill));

        assertEquals("fill side must match trade type at index 9", exception.getMessage());
    }

    @Test
    public void baseTradeToStringSupportsDecimalNum() {
        var decimalFactory = DecimalNumFactory.getInstance();
        Trade trade = Trade.buyAt(0, decimalFactory.hundred(), decimalFactory.one());

        String json = trade.toString();
        assertTrue(json.contains("\"type\":\"BUY\""));
    }
}
