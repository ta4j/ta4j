/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.criteria.ReturnRepresentation;

import java.lang.reflect.Proxy;
import static org.junit.Assert.assertNotSame;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.BaseBarSeriesBuilder;
import java.util.ArrayList;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CashFlowTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private static final int BEGIN = 2;
    private static final double[] CLOSES = { 100d, 102d, 105d, 103d, 110d };
    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

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
    public void baselineValueIsTheCarriedRatioOfPositionsClosedBeforeTheWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 121d, 133.1d)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));
        CashFlow fullHistory = new CashFlow(series, record);
        series.setMaximumBarCount(2);

        CashFlow retained = new CashFlow(series, record);

        assertEquals(2, retained.getBeginIndex());
        // The first trade closed before the window: its 1.1 enters the window.
        assertNumEquals(numFactory.numOf(1.1d), retained.getBaselineValue());
        assertNumEquals(numFactory.numOf(1.1d), retained.getValue(2));
        assertNumEquals(fullHistory.getValue(3), retained.getValue(3));
        // Without pruned history nothing enters the window.
        assertNumEquals(1, fullHistory.getBaselineValue());
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
    public void calculatePositionKeepsSameBarExitAtBoundedAnalysisEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 120d, 130d)
                .build();
        Position closedAtBoundedEnd = new Position(Trade.buyAt(2, numFactory.numOf(100d), numFactory.one()),
                Trade.sellAt(2, numFactory.numOf(120d), numFactory.one()));
        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord(), 0, 2, EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        cashFlow.calculatePosition(closedAtBoundedEnd, 2);

        assertEquals(2, cashFlow.getEndIndex());
        assertNumEquals(1.2d, cashFlow.getValue(2));
    }

    @Test
    public void keepsSameBarExitAtABoundedRecordsEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 100d, 120d, 130d)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, 0, 2, null, null);
        record.enter(2, numFactory.numOf(100d), numFactory.one());
        record.exit(2, numFactory.numOf(120d), numFactory.one());

        CashFlow cashFlow = new CashFlow(series, record);

        assertEquals(2, cashFlow.getEndIndex());
        assertNumEquals(1.2d, cashFlow.getValue(2));
    }

    @Test
    public void calculatePositionBumpsSameBarRatioOnlyWithinTheCapturedSeriesEnd() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 100d, 100d).build();
        Position openOnLastBar = new Position(Trade.buyAt(2, numFactory.numOf(80d), numFactory.one()),
                new ZeroCostModel(), new ZeroCostModel());
        CashFlow cashFlow = new CashFlow(series, new BaseTradingRecord());

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

    @Test
    public void calculatePositionRejectsBarsChangedSinceMaterialization() {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));
        CashFlow cashFlow = new CashFlow(series, record);
        Bar captured = series.getBar(1);
        series.replaceBar(1,
                series.barBuilder()
                        .timePeriod(captured.getTimePeriod())
                        .endTime(captured.getEndTime())
                        .closePrice(200d)
                        .build());
        Position later = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        // The curve holds values from the captured bars; pricing a new position
        // from the replaced bar would mix two bar histories.
        assertThrows(IllegalStateException.class, () -> cashFlow.calculatePosition(later, 2));
    }

    @Test
    public void boundedCurveIgnoresBarsChangingAfterItsFinalIndex() {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d)
                .build();
        Bar trailing = series.getBar(3);
        AtomicBoolean flip = new AtomicBoolean();
        // A feed keeps rewriting the last bar, after the curve's final index,
        // every time the holding cost is evaluated.
        CostModel rewritesTrailingBar = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                series.replaceBar(3,
                        series.barBuilder()
                                .timePeriod(trailing.getTimePeriod())
                                .endTime(trailing.getEndTime())
                                .closePrice(flip.getAndSet(!flip.get()) ? 140d : 150d)
                                .build());
                return numFactory.zero();
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
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), rewritesTrailingBar);
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        CashFlow bounded = new CashFlow(series, record, 0, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // Only bars [0, 1] are read, so changes to bar 3 never force a recapture.
        assertEquals(List.of(numFactory.one(), numFactory.numOf(1.1d)), bounded.stream().toList());
    }

    @Test
    public void readsTheRecordEndWithoutHoldingTheSeriesLock() {
        ReentrantReadWriteLock seriesLock = new ReentrantReadWriteLock();
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10d, 11d, 12d).build();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, seriesLock);
        AtomicBoolean readUnderSeriesLock = new AtomicBoolean();
        // A synchronized record would deadlock against a writer if its bound
        // were read while this thread holds the series read lock.
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel()) {
            @Override
            public Integer getEndIndex() {
                if (seriesLock.getReadHoldCount() > 0) {
                    readUnderSeriesLock.set(true);
                }
                return super.getEndIndex();
            }
        };
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        CashFlow cashFlow = new CashFlow(series, record);

        assertFalse(readUnderSeriesLock.get());
        assertNumEquals(12d / 10d, cashFlow.getValue(2));
    }

    @Test
    public void recapturesWhenAWindowBarChangesWhileTheCurveIsBuilt() {
        CloseMutatingSeries mutating = new CloseMutatingSeries(numFactory);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(),
                mutating.closeBackedCost());
        record.enter(0, mutating.series.getBar(0).getClosePrice(), numFactory.one());
        // The last close changes in place while the builder reads it, after the
        // window was verified and before the change can be published.
        mutating.changeCloseOnBuildRead();

        CashFlow raced = new CashFlow(mutating.series, record);
        CashFlow settled = new CashFlow(mutating.series, record);

        assertNumEquals(150d, mutating.series.getBar(2).getClosePrice());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void calculatePositionRejectsABarChangedWhileTheUpdateReadsIt() {
        CloseMutatingSeries mutating = new CloseMutatingSeries(numFactory);
        TradingRecord empty = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());
        CashFlow cashFlow = new CashFlow(mutating.series, empty);
        List<Num> before = cashFlow.stream().toList();
        Position open = new Position(TradeType.BUY, new ZeroCostModel(), mutating.closeBackedCost());
        open.operate(0, mutating.series.getBar(0).getClosePrice(), numFactory.one());
        mutating.changeCloseOnBuildRead();

        // Applying the position would price its mark from the changed close
        // against a holding cost priced from the old one.
        assertThrows(IllegalStateException.class, () -> cashFlow.calculatePosition(open, 2));
        assertEquals(before, cashFlow.stream().toList());
    }

    @Test
    public void calculatePositionPricesABoundedCurveOnlyThroughItsCapturedBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d)
                .build();
        List<Integer> costEndIndices = new ArrayList<>();
        // A holding cost that grows with the bars it spans, recording each end it
        // is priced through.
        CostModel perBarCost = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                costEndIndices.add(finalIndex);
                return numFactory.numOf(finalIndex - position.getEntry().getIndex());
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
        TradingRecord empty = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), perBarCost);
        CashFlow bounded = new CashFlow(series, empty, 0, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        Position open = new Position(TradeType.BUY, new ZeroCostModel(), perBarCost);
        open.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        BaseTradingRecord withOpen = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), perBarCost);
        withOpen.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        List<Num> expected = new CashFlow(series, withOpen, 0, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET).stream().toList();
        costEndIndices.clear();

        // Bars 2 and 3 are outside the curve, so the later request is capped at 1.
        bounded.calculatePosition(open, 3);

        assertEquals(List.of(1), costEndIndices);
        assertEquals(expected, bounded.stream().toList());
    }

    /**
     * A locked series whose last bar close can be changed in place, the way
     * {@code BaseBar.addPrice} changes it before publishing, during the second read
     * of that bar under the series read lock after the cost model ran.
     */
    private static final class CloseMutatingSeries {

        private final NumFactory numFactory;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Num[] lastClose;
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicBoolean costEvaluated = new AtomicBoolean();
        private int lockedReadsAfterCost;
        private final ConcurrentBarSeries series;

        private CloseMutatingSeries(NumFactory numFactory) {
            this.numFactory = numFactory;
            List<Bar> bars = new MockBarSeriesBuilder().withNumFactory(numFactory)
                    .withData(100d, 110d, 120d)
                    .build()
                    .getBarData();
            Bar last = bars.get(2);
            this.lastClose = new Num[] { last.getClosePrice() };
            Bar mutableLast = new BaseBar(last.getTimePeriod(), last.getBeginTime(), last.getEndTime(),
                    last.getOpenPrice(), last.getHighPrice(), last.getLowPrice(), last.getClosePrice(),
                    last.getVolume(), last.getAmount(), last.getTrades()) {
                @Override
                public Num getClosePrice() {
                    return lastClose[0];
                }
            };
            BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory)
                    .withBars(List.of(bars.get(0), bars.get(1), mutableLast))
                    .build();
            this.series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock, this::beforeBarRead);
        }

        private void changeCloseOnBuildRead() {
            costEvaluated.set(false);
            lockedReadsAfterCost = 0;
            armed.set(true);
        }

        private void beforeBarRead(int index) {
            if (index != 2 || !costEvaluated.get() || lock.getReadHoldCount() == 0) {
                return;
            }
            // The first locked read verifies the window; the second is the build.
            if (++lockedReadsAfterCost == 2 && armed.compareAndSet(true, false)) {
                lastClose[0] = numFactory.numOf(150d);
            }
        }

        private CostModel closeBackedCost() {
            return new CostModel() {
                @Override
                public Num calculate(Position position, int finalIndex) {
                    costEvaluated.set(true);
                    return series.getBar(finalIndex).getClosePrice().multipliedBy(numFactory.numOf(0.1d));
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
        }
    }

    @Test
    public void carriesRealizedPositionAcrossPrunedBeginButNotAnExplicitLaterStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series));
        series.setMaximumBarCount(2);

        CashFlow retainedHistory = new CashFlow(series, record);
        CashFlow laterWindow = new CashFlow(series, record, 2, 3, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(2, retainedHistory.getBeginIndex());
        assertNumEquals(1.1d, retainedHistory.getValue(2));
        assertNumEquals(1.1d, retainedHistory.getValue(3));
        assertNumEquals(1d, laterWindow.getValue(2));
        assertNumEquals(1d, laterWindow.getValue(3));
    }

    @Test
    public void recapturesWhenAHighPriceChangesDuringHoldingCostEvaluation() {
        List<Bar> bars = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d)
                .build()
                .getBarData();
        Bar last = bars.get(2);
        Num[] lastHigh = { numFactory.numOf(125d) };
        Bar mutableLast = new BaseBar(last.getTimePeriod(), last.getBeginTime(), last.getEndTime(), last.getOpenPrice(),
                lastHigh[0], last.getLowPrice(), last.getClosePrice(), last.getVolume(), last.getAmount(),
                last.getTrades()) {
            @Override
            public Num getHighPrice() {
                return lastHigh[0];
            }
        };
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(bars.get(0), bars.get(1), mutableLast))
                .build();
        long revision = series.getBarHistoryRevision();
        AtomicBoolean updateOnNextCost = new AtomicBoolean(true);
        CostModel highBackedCost = new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                Num high = new HighPriceIndicator(series).getValue(finalIndex);
                Num cost = high.multipliedBy(numFactory.numOf(0.1d));
                if (updateOnNextCost.compareAndSet(true, false)) {
                    lastHigh[0] = numFactory.numOf(150d);
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
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), highBackedCost);
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        CashFlow raced = new CashFlow(series, record);
        CashFlow settled = new CashFlow(series, record);

        assertNumEquals(150d, series.getBar(2).getHighPrice());
        assertEquals(revision, series.getBarHistoryRevision());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void matchesEquivalentLogicalWindowAcrossModesHandlingAndIncrementalPricing() {
        for (EquityCurveMode mode : EquityCurveMode.values()) {
            for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                BarSeries series = ConstrainedSeriesSupport.offsetSeries("bounded-cash-flow", numFactory, 2, 4, 0, 100d,
                        80d, 120d, 90d, 110d, 55d);
                BarSeries freshSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                        .withData(120d, 90d, 110d)
                        .build();

                BaseTradingRecord record = boundedRecord(2, 5);
                record.enter(0, numFactory.numOf(100d), numFactory.one());
                record.exit(1, numFactory.numOf(80d), numFactory.one());
                record.enter(1, numFactory.numOf(80d), numFactory.one());
                record.exit(3, numFactory.numOf(90d), numFactory.one());
                record.enter(4, numFactory.numOf(110d), numFactory.one());
                record.exit(4, numFactory.numOf(110d), numFactory.one());

                BaseTradingRecord equivalentRecord = boundedRecord(0, 2);
                Num entryPrice = mode == EquityCurveMode.MARK_TO_MARKET ? numFactory.numOf(120d)
                        : numFactory.numOf(80d);
                equivalentRecord.enter(0, entryPrice, numFactory.one());
                equivalentRecord.exit(1, numFactory.numOf(90d), numFactory.one());
                equivalentRecord.enter(2, numFactory.numOf(110d), numFactory.one());
                equivalentRecord.exit(2, numFactory.numOf(110d), numFactory.one());

                CashFlow actual = new CashFlow(series, record, 2, 4, mode, handling);
                CashFlow expected = new CashFlow(freshSeries, equivalentRecord, 0, 2, mode, handling);
                String context = "mode=" + mode + ", handling=" + handling;
                assertEquals(context, expected.stream().toList(), actual.stream().toList());

                BaseTradingRecord emptyRecord = boundedRecord(2, 5);
                CashFlow incremental = new CashFlow(series, emptyRecord, 2, 4, mode, handling);
                incremental.calculatePosition(record.getPositions().get(1), 4);
                incremental.calculatePosition(record.getPositions().get(2), 4);
                assertEquals(context + " incremental", actual.stream().toList(), incremental.stream().toList());
            }
        }
    }

    @Test
    public void treatsAnExitAfterTheLogicalEndAsOpenAtTheWindowClose() {
        for (EquityCurveMode mode : EquityCurveMode.values()) {
            for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("late-exit-cash-flow", numFactory,
                        4, 100d, 80d, 120d, 90d, 110d, 55d);
                BarSeries freshSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                        .withData(120d, 90d, 110d)
                        .build();
                BaseTradingRecord record = boundedRecord(2, 5);
                record.enter(3, numFactory.numOf(90d), numFactory.one());
                record.exit(5, numFactory.numOf(55d), numFactory.one());
                BaseTradingRecord equivalentRecord = boundedRecord(0, 3);
                equivalentRecord.enter(1, numFactory.numOf(90d), numFactory.one());

                CashFlow actual = new CashFlow(series, record, 2, 4, mode, handling);
                CashFlow expected = new CashFlow(freshSeries, equivalentRecord, 0, 2, mode, handling);
                String context = "mode=" + mode + ", handling=" + handling;
                assertEquals(context, expected.stream().toList(), actual.stream().toList());

                CashFlow incremental = new CashFlow(series, boundedRecord(2, 5), 2, 4, mode, handling);
                if (handling != OpenPositionHandling.IGNORE) {
                    incremental.calculatePosition(record.getPositions().get(0), 4);
                }
                assertEquals(context + " incremental", actual.stream().toList(), incremental.stream().toList());
            }
        }
    }

    @Test
    public void recapturesWhenABarBeforeTheWindowChangesDuringHoldingCostEvaluation() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);

        CashFlow raced = new CashFlow(race.series(), race.recordWithPosition(), PreWindowCostRace.WINDOW_BEGIN,
                PreWindowCostRace.WINDOW_END, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        CashFlow settled = new CashFlow(race.series(), race.recordWithPosition(), PreWindowCostRace.WINDOW_BEGIN,
                PreWindowCostRace.WINDOW_END, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(150d, race.entryClose());
        assertEquals(settled.stream().toList(), raced.stream().toList());
    }

    @Test
    public void rejectsAnIncrementalPositionWhoseHoldingCostReadsABarThatChangedBeforeTheWindow() {
        PreWindowCostRace race = new PreWindowCostRace(numFactory);
        CashFlow curve = new CashFlow(race.series(), race.emptyRecord(), PreWindowCostRace.WINDOW_BEGIN,
                PreWindowCostRace.WINDOW_END, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();

        assertThrows(IllegalStateException.class,
                () -> curve.calculatePosition(race.position(), PreWindowCostRace.WINDOW_END));

        assertEquals(before, curve.stream().toList());
    }

    private BaseTradingRecord boundedRecord(int startIndex, int endIndex) {
        return new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, new ZeroCostModel(), new ZeroCostModel());
    }

    @Test
    public void borrowsSeriesWhileKeepingCapturedValuesAndBounds() {
        BarSeries sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CashFlow cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());
        int originalSize = cashFlow.getSize();
        int originalEnd = cashFlow.getEndIndex();
        List<Num> capturedValues = cashFlow.stream().toList();
        BarSeries firstReturnedSeries = cashFlow.getBarSeries();

        appendOneBar(sampleBarSeries, 4);
        appendOneBar(firstReturnedSeries, 5);

        assertEquals(originalSize, cashFlow.getSize());
        assertEquals(originalEnd, cashFlow.getEndIndex());
        assertEquals(capturedValues, cashFlow.stream().toList());
        assertSame(sampleBarSeries, cashFlow.getBarSeries());
        assertSame(firstReturnedSeries, cashFlow.getBarSeries());
    }

    @Test
    public void cashFlowWindowedMarkToMarketSeedsWindowStartForOpenPosition() {
        var sampleBarSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 120d, 110d, 90d)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, sampleBarSeries), Trade.sellAt(3, sampleBarSeries));

        var cashFlow = new CashFlow(sampleBarSeries, tradingRecord, 1, 3, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(1d, cashFlow.getValue(1));
        assertNumEquals(110d / 120d, cashFlow.getValue(2));
        assertNumEquals(90d / 120d, cashFlow.getValue(3));
    }

    private static void appendOneBar(final BarSeries targetSeries, final Number closePrice) {
        Duration period = targetSeries.getLastBar().getTimePeriod();
        targetSeries.barBuilder()
                .timePeriod(period)
                .endTime(targetSeries.getLastBar().getEndTime().plus(period))
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(1)
                .add();
    }

    @Test
    public void cashFlowWindowedSeriesMatchesUnwindowedInsideWindow() {
        FuturesContract contract = linearPerpetual(numFactory);
        BarSeries full = series(numFactory, 0);
        BarSeries windowed = series(numFactory, BEGIN);
        BaseTradingRecord fullRecord = futuresRecord(contract, 0);
        BaseTradingRecord windowedRecord = futuresRecord(contract, BEGIN);

        CashFlow fullCashFlow = new CashFlow(full, fullRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow windowedCashFlow = new CashFlow(windowed, windowedRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertEquals(5, fullCashFlow.getSize());
        assertEquals(5, windowedCashFlow.getSize());
        assertNumEquals(numFactory.one(), windowedCashFlow.getValue(0));
        assertNumEquals(numFactory.one(), windowedCashFlow.getValue(BEGIN - 1));
        for (int index = 0; index < CLOSES.length; index++) {
            assertNumEquals(fullCashFlow.getValue(index), windowedCashFlow.getValue(BEGIN + index));
        }
    }

    private static BaseTradingRecord futuresRecord(FuturesContract contract, int indexOffset) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(500))
                .build();
        record.operate(fill(contract, indexOffset, ExecutionSide.BUY, 1_000, 100));
        record.operate(fill(contract, indexOffset + 4, ExecutionSide.SELL, 1_000, 110));
        return record;
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount,
            double price) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    private static BarSeries series(NumFactory numFactory, int beginIndex) {
        List<Bar> bars = new ArrayList<>();
        Instant endTime = T0;
        for (double close : CLOSES) {
            Num price = numFactory.numOf(close);
            bars.add(new BaseBar(Duration.ofMinutes(1), endTime.minus(Duration.ofMinutes(1)), endTime, price, price,
                    price, price, numFactory.zero(), numFactory.zero(), 0));
            endTime = endTime.plus(Duration.ofMinutes(1));
        }
        return new BaseBarSeriesBuilder().withNumFactory(numFactory).withBeginIndex(beginIndex).withBars(bars).build();
    }

    private static FuturesContract linearPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
    }

    @Test
    public void futuresMarkToMarketEquityIsNormalizedByAccountCapital() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertEquals(EquityCurveMode.MARK_TO_MARKET, cashFlow.getEquityCurveMode());
            assertEquals(5, cashFlow.getSize());
            assertTrue(cashFlow.hasInitialReturn());
            assertNumEquals(1.0, cashFlow.getValue(0));
            assertNumEquals(1.04, cashFlow.getValue(1));
            assertNumEquals(1.1, cashFlow.getValue(2));
            assertNumEquals(1.06, cashFlow.getValue(3));
            assertNumEquals(1.2, cashFlow.getValue(4));

            CashFlow window = new CashFlow(barSeries, record, 2, 3, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(1.1, window.getValue(2));
            assertNumEquals(1.06, window.getValue(3));
        }
    }

    @Test
    public void futuresInitialReturnRequiresActivityOnFirstBar() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1_000, 105, List.of()));

            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertFalse(cashFlow.hasInitialReturn());
        }
    }

    @Test
    public void futuresRealizedCashFlowAndIgnoredOpenPositionsKeepPaidCashOnly() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 2))));
            record.recordCashFlow(FuturesAnalysisTestSupport.variationMargin(contract, 3, 20));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 3))));

            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow ignored = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.IGNORE);
            CashFlow marked = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            for (int index = 0; index < 3; index++) {
                assertNumEquals(0.996, realized.getValue(index));
                assertNumEquals(0.996, ignored.getValue(index));
            }
            assertNumEquals(1.036, realized.getValue(3));
            assertNumEquals(1.19, realized.getValue(4));
            assertNumEquals(1.19, ignored.getValue(4));
            assertNumEquals(0.996, marked.getValue(0));
            assertNumEquals(1.036, marked.getValue(1));
            assertNumEquals(1.096, marked.getValue(2));
            assertNumEquals(1.056, marked.getValue(3));
            assertNumEquals(1.19, marked.getValue(4));
        }
    }

    @Test
    public void futuresCashFlowWithNonpositiveMarkToMarketEquityUsesActualEquity() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 95, 96);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100_000, 100, List.of()));

            CashFlow marked = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1.0, marked.getValue(0));
            assertNumEquals(-9.0, marked.getValue(1));
            assertNumEquals(-7.0, marked.getValue(2));
            assertNumEquals(1.0, realized.getValue(0));
            assertNumEquals(1.0, realized.getValue(1));
            assertNumEquals(1.0, realized.getValue(2));
        }
    }

    @Test
    public void futuresCashFlowRequiresExplicitAccountCapital() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

            assertThrows(IllegalStateException.class, () -> new CashFlow(barSeries, record,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));

            BaseTradingRecord spot = new BaseTradingRecord();
            spot.operate(0, testFactory.numOf(100), testFactory.one());
            spot.operate(2, testFactory.numOf(110), testFactory.one());
            new CashFlow(barSeries, spot, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        }
    }

    @Test
    public void singleFuturesPositionCashFlowUsesEntrySettlementNotional() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 1_000, 100);

            assertNumEquals(1_000.0, contract.settlementNotional(testFactory.numOf(1_000), testFactory.numOf(100)));
            CashFlow cashFlow = new CashFlow(barSeries, position, EquityCurveMode.MARK_TO_MARKET);

            assertNumEquals(1.0, cashFlow.getValue(0));
            assertNumEquals(1.02, cashFlow.getValue(1));
            assertNumEquals(1.05, cashFlow.getValue(2));
            assertNumEquals(1.03, cashFlow.getValue(3));
            assertNumEquals(1.1, cashFlow.getValue(4));
        }
    }

    @Test
    public void partiallyClosedFuturesCashFlowsUseEachSliceEntryNotional() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 10_000, 11_000);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 1_000_000);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 4, 10_000,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 4))));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 11_000,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));

            Position closedSlice = record.getPositions().getFirst();
            Position openRemainder = record.getOpenPositions().getFirst();
            assertNumEquals(100.0, contract.settlementNotional(closedSlice.getEntry().getAmount(),
                    closedSlice.getEntry().getPricePerAsset()));
            assertNumEquals(300.0, contract.settlementNotional(openRemainder.getEntry().getAmount(),
                    openRemainder.getEntry().getPricePerAsset()));

            CashFlow closedCashFlow = new CashFlow(barSeries, closedSlice, EquityCurveMode.MARK_TO_MARKET);
            CashFlow openCashFlow = new CashFlow(barSeries, openRemainder, EquityCurveMode.MARK_TO_MARKET);
            assertNumEquals(0.99, closedCashFlow.getValue(0));
            assertNumEquals(1.08, closedCashFlow.getValue(1));
            assertNumEquals(0.99, openCashFlow.getValue(0));
            assertNumEquals(1.09, openCashFlow.getValue(1));

            TradingRecord publicRecord = new BaseTradingRecord(List.of(closedSlice));
            assertThrows(IllegalStateException.class, () -> new CashFlow(barSeries, publicRecord,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
        }
    }

    @Test
    public void futuresCashFlowRejectsMarkPriceFromAnotherSeries() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BarSeries otherSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            assertThrows(IllegalArgumentException.class,
                    () -> new CashFlow(barSeries, record, new ClosePriceIndicator(otherSeries), 4,
                            EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));

            CashFlow constantMark = new CashFlow(barSeries, record,
                    new ConstantIndicator<>(barSeries, testFactory.numOf(108)), 4, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(1.16, constantMark.getValue(0));
            assertNumEquals(1.16, constantMark.getValue(3));
            assertNumEquals(1.2, constantMark.getValue(4));
        }
    }

    @Test
    public void emptyFundedFuturesCashFlowIsFlat() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            CashFlow cashFlow = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            for (int index = 0; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(1.0, cashFlow.getValue(index));
            }
        }
    }

    @Test
    public void futuresCashFlowDoesNotMaterializeDiscardedHistory() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            barSeries.setMaximumBarCount(3);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 90, List.of()));

            CashFlow cashFlow = new CashFlow(barSeries, record, 0, 4, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1, cashFlow.getValue(0));
            assertNumEquals(1, cashFlow.getValue(1));
            assertNumEquals(1.3, cashFlow.getValue(2));
        }
    }

    @Test
    public void whollyDeferredFuturesPositionCashFlowIsNeutral() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            Trade entry = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1_000, 100, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

            CashFlow cashFlow = new CashFlow(barSeries, position, EquityCurveMode.MARK_TO_MARKET);

            for (int index = 0; index <= barSeries.getEndIndex(); index++) {
                assertNumEquals(1, cashFlow.getValue(index));
            }
        }
    }

    @Test
    public void boundedSpotCashFlowCapturesOnlyRequestedRetainedWindow() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d, 140d, 150d)
                .build();
        source.setMaximumBarCount(3);
        AtomicBoolean fullBarDataRequested = new AtomicBoolean();
        BarSeries boundedSource = withoutBarData(source, fullBarDataRequested);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(source.getBeginIndex(), source));

        CashFlow cashFlow = new CashFlow(boundedSource, record, 4, 5, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(fullBarDataRequested.get());
        assertEquals(2, cashFlow.getSize());
        assertEquals(4, cashFlow.getBeginIndex());
        assertNumEquals(1d, cashFlow.getValue(4));
        assertNumEquals(150d / 140d, cashFlow.getValue(5));
    }

    @Test
    public void boundedFuturesCashFlowCapturesOnlyRequestedRetainedWindow() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries source = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            source.setMaximumBarCount(3);
            AtomicBoolean fullBarDataRequested = new AtomicBoolean();
            BarSeries boundedSource = withoutBarData(source, fullBarDataRequested);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 4, ExecutionSide.SELL, 1_000, 110, List.of()));

            CashFlow cashFlow = new CashFlow(boundedSource, record, 3, 4, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertFalse(fullBarDataRequested.get());
            assertEquals(2, cashFlow.getSize());
            assertEquals(3, cashFlow.getBeginIndex());
            assertNumEquals(1.06, cashFlow.getValue(3));
            assertNumEquals(1.2, cashFlow.getValue(4));
        }
    }

    @Test
    public void boundedEmptyCashFlowDoesNotRequestBarData() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        AtomicBoolean fullBarDataRequested = new AtomicBoolean();
        CashFlow cashFlow = new CashFlow(withoutBarData(source, fullBarDataRequested), new BaseTradingRecord(), 2, 3,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(fullBarDataRequested.get());
        assertEquals(0, cashFlow.getSize());
        assertNumEquals(1, cashFlow.getValue(2));
    }

    @Test
    public void boundedFuturesCashFlowAfterRetainedBarsMaterializesNoBars() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries source = FuturesAnalysisTestSupport.markToMarketSeries(testFactory);
            source.setMaximumBarCount(3);
            AtomicBoolean fullBarDataRequested = new AtomicBoolean();
            BarSeries boundedSource = withoutBarData(source, fullBarDataRequested);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 500);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));

            CashFlow cashFlow = new CashFlow(boundedSource, record, 7, 8, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);

            assertFalse(fullBarDataRequested.get());
            assertEquals(0, cashFlow.getSize());
            assertNumEquals(1, cashFlow.getValue(7));
        }
    }

    @Test
    public void boundedCashFlowBeforeRetainedBarsMaterializesOnlyNormalizedRange() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d, 140d, 150d)
                .build();
        source.setMaximumBarCount(3);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(3, source));
        AtomicBoolean fullBarDataRequested = new AtomicBoolean();
        CashFlow bounded = new CashFlow(withoutBarData(source, fullBarDataRequested), record, 0, 1,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(fullBarDataRequested.get());
        assertEquals(0, bounded.getSize());
        assertEquals(3, bounded.getBeginIndex());
        // The window ends before the retained bars, so the clamped index stays neutral.
        assertNumEquals(1, bounded.getValue(3));
        assertNumEquals(1, bounded.getValue(2));
    }

    @Test
    public void boundedCashFlowAfterRetainedBarsMaterializesNoBars() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d, 140d, 150d)
                .build();
        source.setMaximumBarCount(3);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(3, source));
        AtomicBoolean fullBarDataRequested = new AtomicBoolean();
        CashFlow bounded = new CashFlow(withoutBarData(source, fullBarDataRequested), record, 7, 8,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(fullBarDataRequested.get());
        assertEquals(0, bounded.getSize());
        assertNumEquals(1, bounded.getValue(3));
        assertNumEquals(1, bounded.getValue(7));
    }

    @Test
    public void boundedCashFlowWithReversedEndpointsCapturesEmptyRange() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 110d, 120d, 130d, 140d, 150d)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(3, source));
        AtomicBoolean fullBarDataRequested = new AtomicBoolean();
        CashFlow bounded = new CashFlow(withoutBarData(source, fullBarDataRequested), record, 4, 2,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        assertFalse(fullBarDataRequested.get());
        assertEquals(0, bounded.getSize());
        assertEquals(4, bounded.getBeginIndex());
        // Reversed bounds capture no observations; out-of-window equity stays neutral.
        assertNumEquals(1, bounded.getValue(4));
        assertNumEquals(1, bounded.getValue(5));
    }

    private static BarSeries withoutBarData(BarSeries delegate, AtomicBoolean requested) {
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getBarData")) {
                        requested.set(true);
                    }
                    return method.invoke(delegate, args);
                });
    }

    @Test
    public void futuresCurvesHonorLogicalEndBeforeLaterExitAndMarks() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 200);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(100))
                .endIndex(1)
                .build();
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
        record.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 100, 200, List.of()));

        CashFlow cashFlow = new CashFlow(series, record);
        CumulativePnL pnl = new CumulativePnL(series, record);
        Returns returns = new Returns(series, record, org.ta4j.core.criteria.ReturnRepresentation.DECIMAL);

        assertEquals(1, cashFlow.getEndIndex());
        assertEquals(1, pnl.getEndIndex());
        assertEquals(1, returns.getEndIndex());
        assertNumEquals(1.1, cashFlow.getValue(1));
        assertNumEquals(10, pnl.getValue(1));
        assertNumEquals(0.1, returns.getValue(1));
    }

    @Test
    public void futuresCurvesRecaptureWhenCustomMarkChangesCapturedBarOutsideReadLock() {
        for (int curveType = 0; curveType < 3; curveType++) {
            BaseBarSeries source = (BaseBarSeries) FuturesAnalysisTestSupport.series(numFactory, 100, 110, 130);
            ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
            ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 100);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
            AtomicBoolean changed = new AtomicBoolean();
            Indicator<Num> mark = new Indicator<Num>() {
                @Override
                public Num getValue(int index) {
                    assertEquals("custom mark must run outside the series read lock", 0, lock.getReadHoldCount());
                    Num value = series.getBar(index).getClosePrice();
                    if (index == 1 && changed.compareAndSet(false, true)) {
                        Bar original = series.getBar(index);
                        series.replaceBar(index,
                                series.barBuilder()
                                        .timePeriod(original.getTimePeriod())
                                        .endTime(original.getEndTime())
                                        .closePrice(120)
                                        .build());
                    }
                    return value;
                }

                @Override
                public int getCountOfUnstableBars() {
                    return 0;
                }

                @Override
                public BarSeries getBarSeries() {
                    return series;
                }
            };
            if (curveType == 0) {
                CashFlow curve = new CashFlow(series, record, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                        OpenPositionHandling.MARK_TO_MARKET);
                assertNumEquals(1.2, curve.getValue(1));
                assertNumEquals(1.3, curve.getValue(2));
            } else if (curveType == 1) {
                CumulativePnL curve = new CumulativePnL(series, record, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                        OpenPositionHandling.MARK_TO_MARKET);
                assertNumEquals(20, curve.getValue(1));
                assertNumEquals(30, curve.getValue(2));
            } else {
                Returns curve = new Returns(series, record, mark, 2,
                        org.ta4j.core.criteria.ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET,
                        OpenPositionHandling.MARK_TO_MARKET);
                assertNumEquals(0.2, curve.getValue(1));
                assertNumEquals(130d / 120d - 1d, curve.getValue(2));
            }
            assertTrue(changed.get());
        }
    }

    @Test
    public void incrementalFuturesPositionSharesConstructorSettlementEconomics() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        BaseTradingRecord complete = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        BaseTradingRecord empty = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        CashFlow curve = new CashFlow(series, empty);
        curve.calculatePosition(complete.getPositions().getFirst(), 1);
        assertNumEquals(1.2, curve.getValue(1));
    }

    @Test
    public void incrementalFuturesPositionsShareAccountCapitalAndHonorEachCutoff() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 100, 120);
        BaseTradingRecord complete = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        BaseTradingRecord initial = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        initial.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()));
        initial.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 110, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1_000, 100, List.of()));
        complete.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1_000, 120, List.of()));
        Position second = complete.getPositions().get(1);
        CashFlow curve = new CashFlow(series, initial);
        curve.calculatePosition(second, 3);
        // Profits 100 and 200 add to the same 500-capital account.
        assertNumEquals(1.6, curve.getValue(3));
        assertEquals(new CashFlow(series, complete).stream().toList(), curve.stream().toList());
        CashFlow truncated = new CashFlow(series, initial);
        truncated.calculatePosition(second, 2);
        // The second exit is not recognized after the incremental cutoff.
        assertNumEquals(1.2, truncated.getValue(3));
    }

    @Test
    public void incrementalFuturesMarkRunsUnlockedAndRejectsChangedWindowAtomically() {
        BaseBarSeries source = (BaseBarSeries) FuturesAnalysisTestSupport.series(numFactory, 100, 110, 130);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord empty = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 100);
        Position incoming = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        AtomicBoolean changed = new AtomicBoolean();
        Indicator<Num> mark = new Indicator<Num>() {
            @Override
            public Num getValue(int index) {
                assertEquals("incremental mark must run outside the read lock", 0, lock.getReadHoldCount());
                Num value = series.getBar(index).getClosePrice();
                if (index == 1 && changed.compareAndSet(false, true)) {
                    Bar original = series.getBar(index);
                    series.replaceBar(index,
                            series.barBuilder()
                                    .timePeriod(original.getTimePeriod())
                                    .endTime(original.getEndTime())
                                    .closePrice(120)
                                    .build());
                }
                return value;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public BarSeries getBarSeries() {
                return series;
            }
        };
        CashFlow curve = new CashFlow(series, empty, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        assertThrows(IllegalStateException.class, () -> curve.calculatePosition(incoming, 2));
        assertTrue(changed.get());
        assertEquals(before, curve.stream().toList());
    }

    @Test
    public void incrementalFuturesCarryExposureAndSettlementPastPreWindowPartialExit() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 110, 120);
        for (int caseIndex = 0; caseIndex < 3; caseIndex++) {
            Trade entry = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 2_000, 100, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            List<TradeFill> exits = caseIndex == 2
                    ? List.of(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 2_000, 110, List.of()))
                    : caseIndex == 1 ? List.of(
                            FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 100, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1_000, 120, List.of()))
                            : List.of(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1_000, 100,
                                    List.of()));
            Position incoming = new Position(entry,
                    Trade.fromFills(Trade.TradeType.SELL, exits, RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BaseTradingRecord full = new BaseTradingRecord() {
                @Override
                public FuturesContract getFuturesContract() {
                    return contract;
                }

                @Override
                public Num getInitialCapital() {
                    return numFactory.numOf(500);
                }

                @Override
                public Integer getStartIndex() {
                    return 2;
                }

                @Override
                public Integer getEndIndex() {
                    return 3;
                }

                @Override
                public List<Position> getPositions() {
                    return List.of(incoming);
                }
            };
            BaseTradingRecord empty = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(500))
                    .startIndex(2)
                    .endIndex(3)
                    .build();
            CashFlow direct = new CashFlow(series, full);
            CashFlow incremental = new CashFlow(series, empty);
            incremental.calculatePosition(incoming, 3);
            assertNumEquals(caseIndex == 2 ? 1.4 : 1.4, direct.getValue(3));
            assertNumEquals(direct.getValue(3), incremental.getValue(3), 1e-10);
        }
    }
}
