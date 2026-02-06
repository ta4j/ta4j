/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Indicator;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CashFlowTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CashFlowTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void cashFlowSize() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        var cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());
        assertEquals(5, cashFlow.getSize());

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1, cashFlow.getValue(2));
        assertNumEquals(1, cashFlow.getValue(3));
        assertNumEquals(1, cashFlow.getValue(4));
    }

    @Test
    public void cashFlowBuyWithOnlyOnePosition() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
    }

    @Test
    public void cashFlowRealizedKeepsEntryValueUntilExit() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, EquityCurveMode.REALIZED);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(3, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowRealizedIgnoresOpenPositions() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, EquityCurveMode.REALIZED);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowMarkToMarketOpenPositionRespectsFinalIndexAndPadsAfterwards() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2)); // padded with last computed value at finalIndex
    }

    @Test
    public void cashFlowMarkToMarketCanIgnoreOpenPositions() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowMarkToMarketIncludesOpenPositionsByDefault() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, EquityCurveMode.MARK_TO_MARKET);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(3, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowWithSellAndBuyTrades() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(2, 1, 3, 5, 6, 3, 20)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries),
                Trade.buyAt(3, sampleBarSeries), Trade.sellAt(4, sampleBarSeries), Trade.sellAt(5, sampleBarSeries),
                Trade.buyAt(6, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals("0.5", cashFlow.getValue(1));
        assertNumEquals("0.5", cashFlow.getValue(2));
        assertNumEquals("0.5", cashFlow.getValue(3));
        assertNumEquals("0.6", cashFlow.getValue(4));
        assertNumEquals("0.6", cashFlow.getValue(5));
        assertNumEquals("-2.8", cashFlow.getValue(6));
    }

    @Test
    public void cashFlowSell() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 4, 8, 16, 32)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(2, sampleBarSeries), Trade.buyAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1, cashFlow.getValue(2));
        assertNumEquals(0, cashFlow.getValue(3));
        assertNumEquals(0, cashFlow.getValue(4));
        assertNumEquals(0, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowShortSell() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 4, 8, 16, 32)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries),
                Trade.sellAt(2, sampleBarSeries), Trade.buyAt(4, sampleBarSeries), Trade.buyAt(4, sampleBarSeries),
                Trade.sellAt(5, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(4, cashFlow.getValue(2));
        assertNumEquals(0, cashFlow.getValue(3));
        assertNumEquals(-8, cashFlow.getValue(4));
        assertNumEquals(-8, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowShortSellWith20PercentGain() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(110, 100, 90, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(1, sampleBarSeries), Trade.buyAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1.1, cashFlow.getValue(2));
        assertNumEquals(1.2, cashFlow.getValue(3));
    }

    @Test
    public void cashFlowShortSellWith20PercentLoss() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(90, 100, 110, 120).build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(1, sampleBarSeries), Trade.buyAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(0.9, cashFlow.getValue(2));
        assertNumEquals(0.8, cashFlow.getValue(3));
    }

    @Test
    public void cashFlowShortSellWith100PercentLoss() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(1, sampleBarSeries), Trade.buyAt(11, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(0.9, cashFlow.getValue(2));
        assertNumEquals(0.8, cashFlow.getValue(3));
        assertNumEquals(0.7, cashFlow.getValue(4));
        assertNumEquals(0.6, cashFlow.getValue(5));
        assertNumEquals(0.5, cashFlow.getValue(6));
        assertNumEquals(0.4, cashFlow.getValue(7));
        assertNumEquals(0.3, cashFlow.getValue(8));
        assertNumEquals(0.2, cashFlow.getValue(9));
        assertNumEquals(0.1, cashFlow.getValue(10));
        assertNumEquals(0.0, cashFlow.getValue(11));
    }

    @Test
    public void cashFlowShortSellWithOver100PercentLoss() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 150, 200, 210)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(0, sampleBarSeries), Trade.buyAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(0.5, cashFlow.getValue(1));
        assertNumEquals(0.0, cashFlow.getValue(2));
        assertNumEquals(-0.1, cashFlow.getValue(3));
    }

    @Test
    public void cashFlowShortSellBigLossWithNegativeCashFlow() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(3, 20).build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(0, sampleBarSeries), Trade.buyAt(1, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(-4.6667, cashFlow.getValue(1));
    }

    @Test
    public void cashFlowValueWithOnlyOnePositionAndAGapBefore() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 1d, 2d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(1, sampleBarSeries), Trade.sellAt(2, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowValueWithOnlyOnePositionAndAGapAfter() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 2d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertEquals(3, cashFlow.getSize());
        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowValueWithTwoPositionsAndLongTimeWithoutTrades() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 4d, 8d, 16d, 32d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(1, sampleBarSeries), Trade.sellAt(2, sampleBarSeries),
                Trade.buyAt(4, sampleBarSeries), Trade.sellAt(5, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
        assertNumEquals(2, cashFlow.getValue(3));
        assertNumEquals(2, cashFlow.getValue(4));
        assertNumEquals(4, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowValue() {
        // First sample series
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(3d, 2d, 5d, 1000d, 5000d, 0.0001d, 4d, 7d, 6d, 7d, 8d, 5d, 6d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries),
                Trade.buyAt(6, sampleBarSeries), Trade.sellAt(8, sampleBarSeries), Trade.buyAt(9, sampleBarSeries),
                Trade.sellAt(11, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2d / 3, cashFlow.getValue(1));
        assertNumEquals(5d / 3, cashFlow.getValue(2));
        assertNumEquals(5d / 3, cashFlow.getValue(3));
        assertNumEquals(5d / 3, cashFlow.getValue(4));
        assertNumEquals(5d / 3, cashFlow.getValue(5));
        assertNumEquals(5d / 3, cashFlow.getValue(6));
        assertNumEquals(5d / 3 * 7d / 4, cashFlow.getValue(7));
        assertNumEquals(5d / 3 * 6d / 4, cashFlow.getValue(8));
        assertNumEquals(5d / 3 * 6d / 4, cashFlow.getValue(9));
        assertNumEquals(5d / 3 * 6d / 4 * 8d / 7, cashFlow.getValue(10));
        assertNumEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(11));
        assertNumEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(12));

        // Second sample series
        sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(5d, 6d, 3d, 7d, 8d, 6d, 10d, 15d, 6d)
                .build();
        tradingRecord = new BaseTradingRecord(Trade.buyAt(4, sampleBarSeries), Trade.sellAt(5, sampleBarSeries),
                Trade.buyAt(6, sampleBarSeries), Trade.sellAt(8, sampleBarSeries));

        var flow = new CashFlow(sampleBarSeries, tradingRecord);
        assertNumEquals(1, flow.getValue(0));
        assertNumEquals(1, flow.getValue(1));
        assertNumEquals(1, flow.getValue(2));
        assertNumEquals(1, flow.getValue(3));
        assertNumEquals(1, flow.getValue(4));
        assertNumEquals("0.75", flow.getValue(5));
        assertNumEquals("0.75", flow.getValue(6));
        assertNumEquals("1.125", flow.getValue(7));
        assertNumEquals("0.45", flow.getValue(8));
    }

    @Test
    public void cashFlowValueWithNoPositions() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d)
                .build();
        var cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());
        assertNumEquals(1, cashFlow.getValue(4));
        assertNumEquals(1, cashFlow.getValue(7));
        assertNumEquals(1, cashFlow.getValue(9));
    }

    @Test
    public void cashFlowWithZeroCostsProducesConsistentValuesForCompressedSeries() {
        double[] originalPrices = { 100, 105, 110, 115, 120 };
        double[] compressedPrices = { 100, 110, 120 };

        var originalSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(originalPrices).build();
        var compressedSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(compressedPrices).build();

        var originalRecord = new BaseTradingRecord(Trade.buyAt(0, originalSeries),
                Trade.sellAt(originalSeries.getEndIndex(), originalSeries));
        var compressedRecord = new BaseTradingRecord(Trade.buyAt(0, compressedSeries),
                Trade.sellAt(compressedSeries.getEndIndex(), compressedSeries));

        var originalCashFlow = new CashFlow(originalSeries, originalRecord);
        var compressedCashFlow = new CashFlow(compressedSeries, compressedRecord);

        assertNumEquals(originalCashFlow.getValue(2), compressedCashFlow.getValue(1));
        assertNumEquals(originalCashFlow.getValue(4), compressedCashFlow.getValue(2));
    }

    @Test
    public void reallyLongCashFlow() {
        int size = 1000000;
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Collections.nCopies(size, 10d))
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries),
                Trade.sellAt(size - 1, sampleBarSeries));
        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);
        assertNumEquals(1, cashFlow.getValue(size - 1));
    }

    @Test
    public void cashFlowBuyExitSameBarShouldNotReturnNaN() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d).build();

        var entryPrice = numFactory.hundred();
        var exitPrice = numFactory.numOf(90);
        var amount = numFactory.one();

        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, entryPrice, amount),
                Trade.sellAt(0, exitPrice, amount));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(0.9, cashFlow.getValue(1));
    }

    @Test
    public void cashFlowIgnoresOpenPositionWhenConfigured() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 120d, 180d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(1, sampleBarSeries),
                Trade.buyAt(1, sampleBarSeries));

        var markToMarket = new CashFlow(sampleBarSeries, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        var ignore = new CashFlow(sampleBarSeries, tradingRecord, OpenPositionHandling.IGNORE);

        assertNumEquals(1.8, markToMarket.getValue(2));
        assertNumEquals(1.2, ignore.getValue(2));
    }

    @Test
    public void cashFlowFromPositionUsesMarkToMarketCurve() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var position = new Position(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, position);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(3, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowFromPositionPreservesCostModels() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        var transactionCost = new FixedTransactionCostModel(1d);
        var holdingCost = new FixedHoldingCostModel(4d);
        var amount = numFactory.one();
        var entry = Trade.buyAt(0, sampleBarSeries.getBar(0).getClosePrice(), amount, transactionCost);
        var exit = Trade.sellAt(2, sampleBarSeries.getBar(2).getClosePrice(), amount, transactionCost);
        var position = new Position(entry, exit, transactionCost, holdingCost);

        var cashFlow = new CashFlow(sampleBarSeries, position);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(98d / 101d, cashFlow.getValue(1));
        assertNumEquals(97d / 101d, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowFromPositionUsesRealizedCurve() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        var position = new Position(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, position, EquityCurveMode.REALIZED);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(3, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowMarkToMarketDoesNotUseFutureExitPriceWhenExitAfterFinalIndex() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), numFactory.one());

        var cashFlow = new CashFlow(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        var expected = series.getBar(2).getClosePrice().dividedBy(series.getBar(0).getClosePrice());
        assertTrue(cashFlow.getValue(2).isEqual(expected));
        assertNumEquals(expected, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowIgnoreSkipsPositionsThatAreOpenAtFinalIndex() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 11d, 12d, 13d, 100d).build();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(4, series.getBar(4).getClosePrice(), numFactory.one());

        var cashFlow = new CashFlow(series, tradingRecord, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        assertNumEquals(series.numFactory().one(), cashFlow.getValue(2));
    }

    @Test
    public void cashFlowIncludesMultipleOpenLotsFromLiveTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 12d, 14d).build();
        var record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(0, new LiveTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.recordFill(1, new LiveTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));

        var cashFlow = new CashFlow(series, record, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        var expectedAt1 = series.getBar(1).getClosePrice().dividedBy(series.getBar(0).getClosePrice());
        var ratioFirst = series.getBar(2).getClosePrice().dividedBy(series.getBar(0).getClosePrice());
        var ratioSecond = series.getBar(2).getClosePrice().dividedBy(series.getBar(1).getClosePrice());
        var expectedAt2 = ratioFirst.multipliedBy(ratioSecond);

        assertNumEquals(expectedAt1, cashFlow.getValue(1));
        assertNumEquals(expectedAt2, cashFlow.getValue(2));
    }

    private record FixedHoldingCostModel(double fee) implements CostModel {

        @Override
        public Num calculate(Position position, int finalIndex) {
            return cost(position);
        }

        @Override
        public Num calculate(Position position) {
            return cost(position);
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return price.getNumFactory().numOf(fee);
        }

        @Override
        public boolean equals(CostModel otherModel) {
            if (otherModel instanceof FixedHoldingCostModel(double fee1)) {
                return fee1 == fee;
            }
            return false;
        }

        private Num cost(Position position) {
            return position.getEntry().getPricePerAsset().getNumFactory().numOf(fee);
        }
    }

}
