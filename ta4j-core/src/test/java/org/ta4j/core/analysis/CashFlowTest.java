/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
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
    public void capturesRollingWindowUnderOneReadLease() {
        AtomicBoolean appendBeforeLock = new AtomicBoolean();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.rollingSeriesWithAppendBeforeReadLock(numFactory,
                appendBeforeLock, 1.5d, 2.5d, 3.5d);
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series));
        appendBeforeLock.set(true);

        CashFlow cashFlow = new CashFlow(series, record, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // The append inside the lease evicts the entry bar: the window is [1, 2]
        // and the entry is valued at the window's first close (2.5), so equity
        // is 1 at index 1 and 3.5 / 2.5 at index 2.
        assertEquals(1, cashFlow.getBeginIndex());
        assertNumEquals(numFactory.one(), cashFlow.getValue(1));
        assertNumEquals(numFactory.numOf(3.5d).dividedBy(numFactory.numOf(2.5d)), cashFlow.getValue(2));
        List<Num> materialized = cashFlow.stream().toList();
        series.barBuilder().closePrice(4.5d).add();
        // A rebased or recomputed curve over [2, 3] would start at 1 and end at
        // 4.5 / 3.5; the materialized one keeps its values.
        assertEquals(materialized, cashFlow.stream().toList());
        assertEquals(List.of(numFactory.one(), numFactory.numOf(3.5d).dividedBy(numFactory.numOf(2.5d))),
                cashFlow.stream().toList());
    }

    @Test
    public void sizeRemainsBoundToMaterializedValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord());

        series.barBuilder().closePrice(4d).add();
        assertEquals(3, cashFlow.getSize());
        series.setMaximumBarCount(1);
        assertEquals(3, cashFlow.getSize());
        assertEquals(3L, cashFlow.stream().count());
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
    public void getBarSeriesReturnsBorrowedInstance() {
        BarSeries sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CashFlow cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());

        assertSame(sampleBarSeries, cashFlow.getBarSeries());
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
    public void cashFlowWindowedMarkToMarketValuesPreWindowEntryAtWindowStartClose() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 120d, 110d, 90d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, 1, 3, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // The window [1, 3] is credited only with the move from its first close
        // (120): the 100 -> 120 gain before it is not equity of this window.
        assertNumEquals(1d, cashFlow.getValue(1));
        assertNumEquals(numFactory.numOf(110d).dividedBy(numFactory.numOf(120d)), cashFlow.getValue(2));
        assertNumEquals(numFactory.numOf(90d).dividedBy(numFactory.numOf(120d)), cashFlow.getValue(3));
    }

    @Test
    public void cashFlowWindowedRealizedKeepsWindowStartFlatForOpenPosition() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 120d, 110d).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(2, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, 1, 2, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(1d, cashFlow.getValue(1));
        assertNumEquals(1.1d, cashFlow.getValue(2));
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
        assertNumEquals(numOf(-2.8), cashFlow.getValue(6), 1e-12);
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
        assertNumEquals(numOf(0.45), flow.getValue(8), 1e-12);
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
        assertNumEquals(95d / 101d, cashFlow.getValue(2));
    }

    @Test
    public void evaluatesHoldingCostModelsWithoutHoldingTheSeriesLock() throws Exception {
        ReentrantReadWriteLock seriesLock = new ReentrantReadWriteLock();
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 11d, 12d).build();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, seriesLock);
        series.setMaximumBarCount(Integer.MAX_VALUE);
        CountDownLatch readerHoldsCache = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        CountDownLatch costRequested = new CountDownLatch(1);
        ConstrainedSeriesSupport.PausingCloseIndicator shared = new ConstrainedSeriesSupport.PausingCloseIndicator(
                series, readerHoldsCache, releaseReader);
        // A user cost model that reads the shared indicator.
        CostModel indicatorBackedCost = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                costRequested.countDown();
                return shared.getValue(finalIndex).multipliedBy(numFactory.zero());
            }

            @Override
            public Num calculate(Position position) {
                return calculate(position, position.getExit().getIndex());
            }

            @Override
            public Num calculate(Num price, Num amount) {
                return numFactory.zero();
            }

            @Override
            public boolean equals(CostModel otherModel) {
                return otherModel == this;
            }
        };
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series), new ZeroCostModel(),
                indicatorBackedCost);
        Bar appended = series.barBuilder().timePeriod(Duration.ofDays(1)).closePrice(13d).build();
        AtomicBoolean writerDone = new AtomicBoolean();
        ExecutorService threads = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "cash-flow-cost-lock-order");
            thread.setDaemon(true);
            return thread;
        });
        try {
            // A reader computes the shared indicator and holds its cache lock.
            Future<Num> reader = threads.submit(() -> shared.getValue(2));
            ConstrainedSeriesSupport.awaitLatch(readerHoldsCache);
            // The analysis reaches the cost model, which waits for that cache lock.
            Future<CashFlow> analysis = threads.submit(() -> new CashFlow(series, position));
            ConstrainedSeriesSupport.awaitLatch(costRequested);
            // A feed writer arrives; it must not queue behind an analysis lease.
            Future<?> writer = threads.submit(() -> {
                series.addBar(appended);
                writerDone.set(true);
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!writerDone.get() && !seriesLock.hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            releaseReader.countDown();

            writer.get(5, TimeUnit.SECONDS);
            assertNumEquals(12d, reader.get(5, TimeUnit.SECONDS));
            assertNumEquals(12d / 10d, analysis.get(5, TimeUnit.SECONDS).getValue(2));
        } finally {
            releaseReader.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    public void recapturesWhenAWindowBarIsReplacedWhileHoldingCostsAreEvaluated() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d, 120d).build();
        Bar lastBar = series.getLastBar();
        Bar replacement = series.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime())
                .closePrice(150d)
                .build();
        AtomicBoolean replaceOnNextCost = new AtomicBoolean(true);
        // A user cost model priced from the close it reads; a feed replaces that
        // bar right after the read, before the curve is built from bar data.
        CostModel closeBackedCost = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                Num cost = series.getBar(finalIndex).getClosePrice().multipliedBy(numFactory.numOf(0.1d));
                if (replaceOnNextCost.compareAndSet(true, false)) {
                    series.addBar(replacement, true);
                }
                return cost;
            }

            @Override
            public Num calculate(Position position) {
                return calculate(position, position.getExit().getIndex());
            }

            @Override
            public Num calculate(Num price, Num amount) {
                return numFactory.zero();
            }

            @Override
            public boolean equals(CostModel otherModel) {
                return otherModel == this;
            }
        };
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), closeBackedCost);
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        CashFlow raced = new CashFlow(series, record);
        CashFlow settled = new CashFlow(series, record);

        assertNumEquals(150d, series.getBar(2).getClosePrice());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void recapturesWhenAWindowBarChangesWithoutMovingTheRevision() {
        List<Bar> bars = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d)
                .build()
                .getBarData();
        Bar last = bars.get(2);
        Num[] lastClose = { last.getClosePrice() };
        // A custom bar class whose close changes in place without publishing the
        // mutation, so the series revision cannot reveal it.
        Bar mutableLast = new BaseBar(last.getTimePeriod(), last.getBeginTime(), last.getEndTime(), last.getOpenPrice(),
                last.getHighPrice(), last.getLowPrice(), last.getClosePrice(), last.getVolume(), last.getAmount(),
                last.getTrades()) {
            @Override
            public Num getClosePrice() {
                return lastClose[0];
            }
        };
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(bars.get(0), bars.get(1), mutableLast))
                .build();
        long revision = series.getBarHistoryRevision();
        AtomicBoolean updateOnNextCost = new AtomicBoolean(true);
        // A user cost model priced from the close it reads; a feed updates that
        // bar in place right after the read, before the curve is built.
        CostModel closeBackedCost = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                Num cost = series.getBar(finalIndex).getClosePrice().multipliedBy(numFactory.numOf(0.1d));
                if (updateOnNextCost.compareAndSet(true, false)) {
                    lastClose[0] = numFactory.numOf(150d);
                }
                return cost;
            }

            @Override
            public Num calculate(Position position) {
                return calculate(position, position.getExit().getIndex());
            }

            @Override
            public Num calculate(Num price, Num amount) {
                return numFactory.zero();
            }

            @Override
            public boolean equals(CostModel otherModel) {
                return otherModel == this;
            }
        };
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), closeBackedCost);
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        CashFlow raced = new CashFlow(series, record);
        CashFlow settled = new CashFlow(series, record);

        assertNumEquals(150d, series.getBar(2).getClosePrice());
        assertEquals(revision, series.getBarHistoryRevision());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void markToMarketHoldingCostEndsAtRealizedEquity() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series), new ZeroCostModel(),
                new FixedHoldingCostModel(4d));

        var markToMarket = new CashFlow(series, position, EquityCurveMode.MARK_TO_MARKET);
        var realized = new CashFlow(series, position, EquityCurveMode.REALIZED);

        assertNumEquals(0.98d, markToMarket.getValue(1));
        assertNumEquals(0.96d, markToMarket.getValue(2));
        assertNumEquals(realized.getValue(2), markToMarket.getValue(2));
    }

    @Test
    public void retainedMarksAccrueOnlyInWindowHoldingCostAtTheWholeHoldRate() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d)
                .build();
        CostModel transactionCost = new ZeroCostModel();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(12, series), transactionCost,
                new FixedHoldingCostModel(12d));
        CashFlow fullHistory = new CashFlow(series, position);
        series.setMaximumBarCount(3);

        CashFlow retained = new CashFlow(series, position);

        // 12 of holding cost over 12 held bars is 1 per bar. The window [10, 12]
        // values the position at close 100 net of the 10 accrued by then (90);
        // later marks are net of 11 and 12, so only in-window carry moves equity.
        assertNumEquals(1d, retained.getValue(10));
        assertNumEquals(numFactory.numOf(89d).dividedBy(numFactory.numOf(90d)), retained.getValue(11));
        assertNumEquals(numFactory.numOf(88d).dividedBy(numFactory.numOf(90d)), retained.getValue(12));
        // Each in-window step matches the full-history curve's step.
        for (int index = 11; index <= 12; index++) {
            assertNumEquals(fullHistory.getValue(index).dividedBy(fullHistory.getValue(index - 1)),
                    retained.getValue(index).dividedBy(retained.getValue(index - 1)), 1e-12);
        }
    }

    @Test
    public void markToMarketPreWindowEntryMatchesEntryAtTheRetainedWindowStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 99d, 121d, 110d, 132d)
                .build();
        // Oracle: with no holding cost, a long entered before the window is
        // worth the same as one bought at the window's first close (121).
        TradingRecord closedPredating = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(4, series));
        TradingRecord closedAtStart = new BaseTradingRecord(Trade.buyAt(3, series), Trade.sellAt(4, series));
        TradingRecord openPredating = new BaseTradingRecord(Trade.buyAt(2, series));
        TradingRecord openAtStart = new BaseTradingRecord(Trade.buyAt(3, series));
        series.setMaximumBarCount(3);

        CashFlow closed = new CashFlow(series, closedPredating, EquityCurveMode.MARK_TO_MARKET);
        CashFlow closedOracle = new CashFlow(series, closedAtStart, EquityCurveMode.MARK_TO_MARKET);
        CashFlow open = new CashFlow(series, openPredating, EquityCurveMode.MARK_TO_MARKET);
        CashFlow openOracle = new CashFlow(series, openAtStart, EquityCurveMode.MARK_TO_MARKET);

        assertEquals(3, series.getBeginIndex());
        // 110 / 121 at the exit, carried to the window end; open: 132 / 121.
        assertNumEquals(numFactory.numOf(110d).dividedBy(numFactory.numOf(121d)), closed.getValue(5));
        assertNumEquals(numFactory.numOf(132d).dividedBy(numFactory.numOf(121d)), open.getValue(5));
        for (int index = 3; index <= 5; index++) {
            assertNumEquals(closedOracle.getValue(index), closed.getValue(index));
            assertNumEquals(openOracle.getValue(index), open.getValue(index));
        }
    }

    @Test
    public void preWindowShortCarriesOnlyBorrowingAccruedInsideTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d, 100d, 100d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(),
                new LinearBorrowingCostModel(0.01d));
        record.enter(1, series.getBar(1).getClosePrice(), numFactory.one());
        series.setMaximumBarCount(3);

        CashFlow cashFlow = new CashFlow(series, record, EquityCurveMode.MARK_TO_MARKET);

        // Borrowing 1% of 100 per bar is 1 per bar. The window [3, 5] values the
        // short at 100 plus the 2 accrued by index 3 (102); the marks at 4 and 5
        // owe 103 and 104, so equity is 2 - 103/102 and 2 - 104/102: only the two
        // in-window periods of carry reduce it.
        assertEquals(3, series.getBeginIndex());
        assertNumEquals(1d, cashFlow.getValue(3));
        assertNumEquals(numFactory.numOf(101d).dividedBy(numFactory.numOf(102d)), cashFlow.getValue(4), 1e-12);
        assertNumEquals(numFactory.numOf(100d).dividedBy(numFactory.numOf(102d)), cashFlow.getValue(5), 1e-12);
    }

    @Test
    public void realizedKeepsEntryPriceCostBasisForPreWindowEntry() {
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(30d, 40d, 50d).build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, rolling), Trade.sellAt(2, rolling));
        rolling.setMaximumBarCount(2);

        CashFlow realized = new CashFlow(rolling, record, EquityCurveMode.REALIZED);
        CashFlow markToMarket = new CashFlow(rolling, record, EquityCurveMode.MARK_TO_MARKET);

        // Realized equity books proceeds against the 30 cost basis at the exit;
        // mark-to-market measures the window from its first close (40).
        assertEquals(1, rolling.getBeginIndex());
        assertNumEquals(1d, realized.getValue(1));
        assertNumEquals(numFactory.numOf(50d).dividedBy(numFactory.numOf(30d)), realized.getValue(2));
        assertNumEquals(numFactory.numOf(50d).dividedBy(numFactory.numOf(40d)), markToMarket.getValue(2));
    }

    @Test
    public void positionExitingAfterTheWindowAccruesHoldingCostOnlyInsideIt() {
        double[] closes = { 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d, 100d };
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("borrow-past-window", numFactory, 4,
                closes);
        CostModel borrowing = new LinearBorrowingCostModel(0.01d);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(), borrowing);
        record.enter(1, series.getBar(1).getClosePrice(), numFactory.one());
        record.exit(8, series.getBar(8).getClosePrice(), numFactory.one());
        // Oracle: the same short still open on a series truncated at the window end.
        BarSeries truncated = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 100d, 100d, 100d)
                .build();
        BaseTradingRecord openAtWindowEnd = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(), borrowing);
        openAtWindowEnd.enter(1, truncated.getBar(1).getClosePrice(), numFactory.one());
        CashFlow expected = new CashFlow(truncated, openAtWindowEnd);

        CashFlow materialized = new CashFlow(series, record);
        CashFlow calculated = new CashFlow(series, new BaseTradingRecord());
        calculated.calculatePosition(record.getPositions().getFirst(), 4);

        assertTrue(expected.getValue(4).isLessThan(numFactory.one()));
        for (int index = 0; index <= 4; index++) {
            assertNumEquals(expected.getValue(index), materialized.getValue(index));
            assertNumEquals(expected.getValue(index), calculated.getValue(index));
        }
    }

    @Test
    public void calculatePositionBumpsSameBarRatioOnlyWithinTheCapturedSeriesEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        // Filled below the close on the captured last bar: marked at that close.
        Position openOnLastBar = new Position(Trade.buyAt(2, numFactory.numOf(80d), numFactory.one()),
                new ZeroCostModel(), new ZeroCostModel());
        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord());

        // A live feed appends a bar after the curve was captured.
        series.barBuilder().closePrice(100d).add();
        cashFlow.calculatePosition(openOnLastBar, 2);

        assertEquals(2, cashFlow.getEndIndex());
        assertNumEquals(1.25d, cashFlow.getValue(2));
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
    public void cashFlowIncludesMultipleOpenLotsFromBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 12d, 14d).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
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

    @Test
    public void preservesLogicalOffsetForTradeAtNonzeroIndex() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));

        CashFlow cashFlow = new CashFlow(offset, record);

        assertEquals(10, cashFlow.getBarSeries().getBeginIndex());
        assertEquals(12, cashFlow.getBarSeries().getEndIndex());
        assertEquals(10, cashFlow.getBarSeries().getRemovedBarsCount());
        assertNumEquals(3, cashFlow.getValue(12));
    }

    @Test
    public void materializesLargeOffsetWindowWithoutAbsoluteAllocation() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        int beginIndex = 1_000_000_000;
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(beginIndex)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(beginIndex, offset),
                Trade.sellAt(beginIndex + 2, offset));

        CashFlow cashFlow = new CashFlow(offset, record);

        assertEquals(3, cashFlow.getSize());
        assertNumEquals(3, cashFlow.getValue(beginIndex + 2));
    }

    @Test
    public void valuesAreAddressableAtTerminalOffsetWithoutAbsoluteSizing() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        CashFlow cashFlow = new CashFlow(terminal, new BaseTradingRecord());

        assertEquals(Integer.MAX_VALUE, cashFlow.getBarSeries().getEndIndex());
        assertEquals(1, cashFlow.getSize());
        assertNumEquals(1, cashFlow.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void outOfWindowReadsReturnNeutralOne() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        CashFlow cashFlow = new CashFlow(offset, new BaseTradingRecord());

        assertNumEquals(1, cashFlow.getValue(9));
        assertNumEquals(1, cashFlow.getValue(13));
    }

    @Test
    public void disjointWindowReturnsNeutralValuesWithoutBuildingInvertedBuffer() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 20d, 30d).build();

        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord(), 10, 12, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(1, cashFlow.getValue(10));
        assertNumEquals(1, cashFlow.getValue(12));
    }

    @Test
    public void marksPositionExitingAfterTheWindowAtTheWindowClose() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("trailing-exit", numFactory, 1, 10d, 20d,
                30d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, 0, 1, null, null);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());

        CashFlow cashFlow = new CashFlow(series, tradingRecord);

        // The exit at 30 happens after the window: equity is marked at the last
        // window close (20), never at the later exit price.
        assertNumEquals(2, cashFlow.getValue(1));
        assertEquals(List.of(numFactory.one(), numFactory.numOf(2)), cashFlow.stream().toList());
        assertEquals(1, cashFlow.getEndIndex());
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries series = ConstrainedSeriesSupport.emptyLogicalSeries("empty-window", numFactory, 100d);
        Num one = numFactory.one();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), one),
                Trade.sellAt(0, numFactory.numOf(50d), one));

        CashFlow cashFlow = new CashFlow(series, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertEquals(0, cashFlow.getSize());
    }

    @Test
    public void sameBarPositionOnTerminalIndexDoesNotOverflow() {
        BarSeries series = ConstrainedSeriesSupport.terminalOneBarSeries("terminal", numFactory, 100d);
        var record = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE, series),
                Trade.sellAt(Integer.MAX_VALUE, series));

        CashFlow cashFlow = new CashFlow(series, record);

        assertNumEquals(1, cashFlow.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void neverPricesHoldingCostOfPositionsOutsideTheWindow() {
        BarSeries series = OutOfWindowPositions.series(numFactory);
        List<Num> flat = OutOfWindowPositions.values(new CashFlow(series, new BaseTradingRecord()));

        CashFlow curve = new CashFlow(series, OutOfWindowPositions.closedBeforeTheWindow(numFactory));
        assertEquals(flat, OutOfWindowPositions.values(curve));

        OutOfWindowPositions.calculateAll(curve);
        assertEquals(flat, OutOfWindowPositions.values(curve));
    }
}
