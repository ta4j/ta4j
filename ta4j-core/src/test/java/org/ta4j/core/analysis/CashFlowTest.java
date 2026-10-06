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
import org.ta4j.core.TradeFee;
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
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;

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
    public void spotUpdateSurvivesFollowingNativePosition() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord nativeRecord = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100, List.of()));
        nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 110, List.of()));
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        CashFlow curve = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500), 1,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        curve.calculatePosition(spot, 1);
        assertNumEquals(1.1, curve.getValue(1));
        curve.calculatePosition(nativeRecord.getPositions().getFirst(), 1);
        assertNumEquals(1.12, curve.getValue(1));
        curve.calculatePosition(spot, 1);
        assertNumEquals(1.232, curve.getValue(1));
        curve.calculatePosition(nativeRecord.getPositions().getFirst(), 1);
        assertNumEquals(1.252, curve.getValue(1));

        // Constructor state must participate in the same update ordering.
        CashFlow seeded = new CashFlow(series, nativeRecord, 1, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        seeded.calculatePosition(spot, 1);
        assertNumEquals(1.122, seeded.getValue(1));
        seeded.calculatePosition(nativeRecord.getPositions().getFirst(), 1);
        assertNumEquals(1.142, seeded.getValue(1));
    }

    @Test
    public void interveningSpotUpdateRetainsNativeFeeComponents() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord fees = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, TradeType.BUY, false);
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        CashFlow curve = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500), 1,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        curve.calculatePosition(fees.getOpenPositions().getFirst(), 1);
        curve.calculatePosition(spot, 1);
        // The spot ratio scales the already present fees by 1.1; the next
        // native lot adds its rebate without scaling. Retain the residual 1.1.
        Trade entry = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -1.1e16))),
                RecordedTradeCostModel.INSTANCE);
        Position rebate = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        curve.calculatePosition(rebate, 1);
        assertNumEquals(1.0978, curve.getValue(1));
    }

    @Test
    public void spotCarryRemainsInNativeValuesAndBaseline() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110, 100, 110);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        BaseTradingRecord nativeRecord = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
        nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 110, List.of()));
        CashFlow curve = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500), 3,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        curve.calculatePosition(spot, 3);
        curve.calculatePosition(nativeRecord.getPositions().getFirst(), 3);
        assertEquals(2, curve.getBeginIndex());
        assertNumEquals(1.1, curve.getBaselineValue());
        assertNumEquals(1.1, curve.getValue(2));
        assertNumEquals(1.12, curve.getValue(3));
    }

    @Test
    public void spotUpdateOnNativeCurveStillRequiresPositiveEntryEquity() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        CashFlow curve = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500), 1,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        Trade entry = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, 1000))),
                RecordedTradeCostModel.INSTANCE);
        curve.calculatePosition(new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel()), 1);
        assertNumEquals(-1, curve.getValue(0));
        List<Num> before = curve.stream().toList();
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        curve.calculatePosition(spot, 1);
        assertEquals(before, curve.stream().toList());
        Trade rebate = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 110,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -1000))),
                RecordedTradeCostModel.INSTANCE);
        curve.calculatePosition(new Position(rebate, RecordedTradeCostModel.INSTANCE, new ZeroCostModel()), 1);
        assertNumEquals(1.02, curve.getValue(1));
    }

    @Test
    public void failedSpotUpdateDoesNotPublishNativeComponentsOrFactors() {
        for (boolean nativeOrigin : new boolean[] { false, true }) {
            CloseMutatingSeries mutating = new CloseMutatingSeries(numFactory);
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                    .toBuilder()
                    .contractSize(numFactory.one())
                    .build();
            BaseTradingRecord nativeRecord = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
            nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100, List.of()));
            CashFlow curve = new CashFlow(mutating.series, nativeOrigin ? nativeRecord : new BaseTradingRecord(), 2,
                    EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
            if (!nativeOrigin)
                curve.calculatePosition(nativeRecord.getOpenPositions().getFirst(), 2);
            List<Num> before = curve.stream().toList();
            Num baseline = curve.getBaselineValue();
            Position spot = new Position(TradeType.BUY, new ZeroCostModel(), mutating.closeBackedCost());
            spot.operate(0, numFactory.hundred(), numFactory.one());
            mutating.changeCloseOnBuildRead();
            assertThrows(IllegalStateException.class, () -> curve.calculatePosition(spot, 2));
            assertEquals(before, curve.stream().toList());
            assertNumEquals(baseline, curve.getBaselineValue());
            // Restore the same borrowed bar's value, then force reconstruction from
            // retained components; a failed spot update must not affect that state.
            mutating.lastClose[0] = numFactory.numOf(120);
            BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 110, List.of()));
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 1, 110, List.of()));
            curve.calculatePosition(zero.getPositions().getFirst(), 2);
            assertEquals(before, curve.stream().toList());
            assertNumEquals(baseline, curve.getBaselineValue());
        }
    }

    @Test
    public void zeroProfitNativeUpdateKeepsEnteringBaselineSeparateFromHead() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord historical = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(numFactory, 1))));
        historical.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 100, List.of()));
        CashFlow curve = new CashFlow(series, historical, 3, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0.998, curve.getBaselineValue());
        Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(2, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
        curve.calculatePosition(spot, 3);
        assertNumEquals(0.998, curve.getBaselineValue());
        assertNumEquals(1.0978, curve.getValue(2));
        BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
        zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 100, List.of()));
        curve.calculatePosition(zero.getPositions().getFirst(), 3);
        assertNumEquals(0.998, curve.getBaselineValue());
        assertNumEquals(1.0978, curve.getValue(2));
    }

    @Test
    public void cashComponentsPersistForOrdinaryAndNativeConstructorOrigins() {
        for (int origin = 0; origin < 3; origin++) {
            BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100);
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                    .toBuilder()
                    .contractSize(numFactory.one())
                    .build();
            Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                    Trade.sellAt(1, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(), new ZeroCostModel());
            TradingRecord initial = origin == 2 ? FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500)
                    : origin == 1 ? new BaseTradingRecord(spot) : new BaseTradingRecord();
            double capital = origin == 2 ? 500 : 100;
            double initialFactor = origin == 1 ? 1.1 : 1;
            List<Position> nativeLots = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, TradeType.BUY, false)
                    .getOpenPositions();
            for (boolean spotFirst : new boolean[] { false, true }) {
                CashFlow curve = new CashFlow(series, initial, 2, EquityCurveMode.REALIZED,
                        OpenPositionHandling.MARK_TO_MARKET);
                if (spotFirst)
                    curve.calculatePosition(spot, 2);
                for (Position lot : nativeLots)
                    curve.calculatePosition(lot, 2);
                if (!spotFirst)
                    curve.calculatePosition(spot, 2);
                double expected = spotFirst ? initialFactor * 1.1 - 1 / capital : (initialFactor - 1 / capital) * 1.1;
                assertNumEquals(expected, curve.getValue(1));
                BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100, List.of()));
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 1, 100, List.of()));
                curve.calculatePosition(zero.getPositions().getFirst(), 2);
                assertNumEquals(expected, curve.getValue(2));
                assertNumEquals(1, curve.getBaselineValue());
            }
        }
    }

    @Test
    public void unequalFallbackCapitalDoesNotRoundAwayNativeResidual() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        CashFlow curve = new CashFlow(series, new BaseTradingRecord(), 2, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        for (boolean first : new boolean[] { true, false }) {
            double price = first ? 100 : 200;
            List<TradeFee> fees = first
                    ? List.of(FuturesAnalysisTestSupport.commission(numFactory, 1e16),
                            FuturesAnalysisTestSupport.commission(numFactory, 1))
                    : List.of(FuturesAnalysisTestSupport.commission(numFactory, -2e16));
            Trade entry = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, price, fees),
                    RecordedTradeCostModel.INSTANCE);
            Trade exit = Trade.fromFill(
                    FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, price, List.of()),
                    RecordedTradeCostModel.INSTANCE);
            curve.calculatePosition(new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel()), 2);
        }
        // -(1e16+1)/100 + 2e16/200 = -.01, preserving the existing per-position
        // fallback.
        assertNumEquals(0.99, curve.getValue(1));
        assertNumEquals(1, curve.getBaselineValue());
    }

    @Test
    public void enteringEquityCoversBothOriginsAndBothHeadUpdateOrders() {
        for (boolean funded : new boolean[] { false, true }) {
            for (boolean spotFirst : new boolean[] { false, true }) {
                BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
                series.setMaximumBarCount(2);
                FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                        .toBuilder()
                        .contractSize(numFactory.one())
                        .build();
                BaseTradingRecord historical = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
                historical.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, 1))));
                historical.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 1, 100, List.of()));
                CashFlow curve = new CashFlow(series,
                        funded ? FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500)
                                : new BaseTradingRecord(),
                        3, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
                Position spot = new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                        Trade.sellAt(2, numFactory.numOf(110), numFactory.one()), new ZeroCostModel(),
                        new ZeroCostModel());
                if (spotFirst)
                    curve.calculatePosition(spot, 3);
                curve.calculatePosition(historical.getPositions().getFirst(), 3);
                if (!spotFirst)
                    curve.calculatePosition(spot, 3);
                double entering = 1 - 1d / (funded ? 500 : 100);
                double head = spotFirst ? 1.1 - 1d / (funded ? 500 : 100) : entering * 1.1;
                assertNumEquals(entering, curve.getBaselineValue());
                assertNumEquals(head, curve.getValue(2));
                BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100, List.of()));
                zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 1, 100, List.of()));
                curve.calculatePosition(zero.getPositions().getFirst(), 3);
                assertNumEquals(entering, curve.getBaselineValue());
                assertNumEquals(head, curve.getValue(3));
            }
        }
    }

    @Test
    public void enteringAndHeadNativeExposureShareOneMarkRead() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 110, 120);
        series.setMaximumBarCount(2);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(numFactory, 1))));
        int[] reads = new int[2];
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> reads[index - 2]++);
        CashFlow curve = new CashFlow(series, record, mark, 3, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        // Equity before the head uses its available mark: (500 + 10 - 1)/500.
        assertNumEquals(1.018, curve.getBaselineValue());
        assertNumEquals(1.018, curve.getValue(2));
        assertNumEquals(1.038, curve.getValue(3));
        assertEquals(1, reads[0]);
        assertEquals(1, reads[1]);
    }

    @Test
    public void normalizedEquityStillRejectsUnrepresentableAccountPnl() {
        NumFactory source = DecimalNumFactory.getInstance();
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(source)
                .toBuilder()
                .contractSize(source.one())
                .build();
        for (double fee : new double[] { 1e308, Double.MIN_VALUE }) {
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, source, 500);
            List<TradeFee> fees = fee == Double.MIN_VALUE ? List.of(
                    FuturesAnalysisTestSupport.commission(source, 0).toBuilder().amount(source.numOf("1e-400")).build())
                    : List.of(FuturesAnalysisTestSupport.commission(source, fee),
                            FuturesAnalysisTestSupport.commission(source, fee));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1, 100, fees));
            BarSeries target = FuturesAnalysisTestSupport.series(DoubleNumFactory.getInstance(), 100, 100);
            assertThrows(IllegalArgumentException.class, () -> new CashFlow(target, record, 1, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET));
        }
    }

    @Test
    public void incrementalCrossLotFeeComponentsShareConstructorEconomics() {
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        // CumulativePnL owns the full constructor-state/closed-state matrix.
        for (boolean seedFirst : new boolean[] { false, true }) {
            boolean closed = seedFirst;
            BaseTradingRecord complete = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, Trade.TradeType.BUY,
                    closed);
            List<Position> positions = closed ? complete.getPositions() : complete.getOpenPositions();
            BaseTradingRecord initial = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
            if (seedFirst) {
                Position first = positions.getFirst();
                initial.operate(first.getEntry().getFills().getFirst());
                if (closed) {
                    initial.operate(first.getExit().getFills().getFirst());
                }
            }
            CashFlow constructor = new CashFlow(series, complete, 3, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow incremental = new CashFlow(series, initial, 3, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertEquals(2, positions.size());
            for (int lot = seedFirst ? 1 : 0; lot < positions.size(); lot++) {
                incremental.calculatePosition(positions.get(lot), 3);
            }
            for (int index = 1; index <= 3; index++) {
                assertNumEquals(0.998, constructor.getValue(index));
                assertNumEquals(0.998, incremental.getValue(index));
            }
        }
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

    @Test
    public void futuresMaterializationRetriesAfterAMarkReadClearsTheWindow() {
        BarSeries source = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
        AtomicBoolean cleared = new AtomicBoolean();
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            assertEquals(0, lock.getReadHoldCount());
            if (cleared.compareAndSet(false, true))
                series.clear();
        });
        CashFlow curve = new CashFlow(series, record, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertTrue(cleared.get());
        assertEquals(0, curve.getSize());
    }

    @Test
    public void incrementalFuturesMarkFailureReportsChangedWindowWithoutPublishing() {
        BarSeries source = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        AtomicBoolean cleared = new AtomicBoolean();
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            assertEquals(0, lock.getReadHoldCount());
            if (cleared.compareAndSet(false, true))
                series.clear();
        });
        CashFlow curve = new CashFlow(series, record, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> curve.calculatePosition(position, 1));
        assertTrue(failure.getMessage().contains("changed inside this curve's window"));
        assertTrue(cleared.get());
        assertEquals(before, curve.stream().toList());
    }

    @Test
    public void futuresMarkFailuresKeepTheirIdentityWhenTheWindowIsUnchanged() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 110);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory);
        BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 500);
        RuntimeException original = new IllegalArgumentException("mark source failed");
        Indicator<Num> mark = FuturesAnalysisTestSupport.markWithReadAction(series, index -> {
            throw original;
        });
        CashFlow curve = new CashFlow(series, record, mark, 2, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        List<Num> before = curve.stream().toList();
        Position position = FuturesAnalysisTestSupport.openPosition(contract, 0, 100, 100);
        assertSame(original, assertThrows(RuntimeException.class, () -> curve.calculatePosition(position, 1)));
        assertEquals(before, curve.stream().toList());
        record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100, List.of()));
        assertSame(original, assertThrows(RuntimeException.class, () -> new CashFlow(series, record, mark, 2,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET)));
    }

    @Test
    public void crossLotFeeResidualReducesAccountEquity() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        BaseTradingRecord record = FuturesAnalysisTestSupport.crossLotFeeRecord(contract, TradeType.BUY, true);
        CashFlow flow = new CashFlow(series, record);
        assertNumEquals(1, flow.getValue(0));
        assertNumEquals(0.998, flow.getValue(1));
        assertNumEquals(0.998, flow.getValue(2));
        assertNumEquals(0.998, flow.getValue(3));
    }

    @Test
    public void realizedHistoricalComponentLossSeedsRetainedBaseline() {
        BarSeries retained = FuturesAnalysisTestSupport.series(numFactory, 100, 1e16 + 100, 100, 100);
        retained.setMaximumBarCount(2);
        BaseTradingRecord record = FuturesAnalysisTestSupport.roundedPreWindowProfitRecord(numFactory);
        assertNumEquals(0, record.getPositions().getFirst().getRealizedProfit(1));
        CashFlow cash = new CashFlow(retained, record, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0.998, cash.getValue(2));
        assertNumEquals(0.998, cash.getBaselineValue());
        assertTrue(!cash.hasInitialReturn());
    }

    @Test
    public void mixedFactoryComponentsHaveOneRepresentableEquityTotal() {
        BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100);
        for (boolean reverse : new boolean[] { false, true }) {
            CashFlow cash = new CashFlow(series,
                    FuturesAnalysisTestSupport.mixedFactoryRecord(numFactory, reverse, true));
            assertNumEquals(1.002, cash.getValue(0));
            assertNumEquals(1.002, cash.getValue(1));
        }
    }

    @Test
    public void roundedRealizedCashPublishesState() {
        assertRoundedCashPublication(EquityCurveMode.REALIZED, true);
    }

    @Test
    public void roundedRealizedCashIgnoresZeroContribution() {
        assertRoundedCashPublication(EquityCurveMode.REALIZED, false);
    }

    @Test
    public void roundedMarkedCashPublishesState() {
        assertRoundedCashPublication(EquityCurveMode.MARK_TO_MARKET, true);
    }

    @Test
    public void roundedMarkedCashIgnoresZeroContribution() {
        assertRoundedCashPublication(EquityCurveMode.MARK_TO_MARKET, false);
    }

    private void assertRoundedCashPublication(EquityCurveMode mode, boolean assertImmediate) {
        NumFactory factory = DecimalNumFactory
                .getInstance(new java.math.MathContext(2, java.math.RoundingMode.HALF_UP));
        BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100);
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                .toBuilder()
                .contractSize(factory.one())
                .build();
        CashFlow curve = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, factory, 500), 2, mode,
                OpenPositionHandling.MARK_TO_MARKET);
        BaseTradingRecord first = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 500);
        first.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100,
                List.of(FuturesAnalysisTestSupport.commission(factory, -24))));
        curve.calculatePosition(first.getOpenPositions().getFirst(), 2);
        assertNumEquals(1, curve.getValue(1));
        Position spot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                Trade.sellAt(1, factory.numOf(110), factory.one()), new ZeroCostModel(), new ZeroCostModel());
        curve.calculatePosition(spot, 2);
        if (assertImmediate) {
            // Exact normalized retained state: 1.1 + 0.048*1.1 = 1.1528 -> 1.2.
            assertNumEquals(1.2, curve.getValue(1));
        } else {
            List<Num> beforeZero = curve.stream().toList();
            BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 500);
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100, List.of()));
            zero.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 1, 100, List.of()));
            curve.calculatePosition(zero.getPositions().getFirst(), 2);
            assertNumEquals(1.2, curve.getValue(1));
            assertEquals(beforeZero, curve.stream().toList());
        }
    }

    @Test
    public void retainedCashPublicationUsesIndependentOrderedComponents() {
        List<NumFactory> precisions = numFactory instanceof DecimalNumFactory
                ? List.of(DecimalNumFactory.getInstance(new java.math.MathContext(2, java.math.RoundingMode.HALF_UP)),
                        DecimalNumFactory.getInstance(new java.math.MathContext(3, java.math.RoundingMode.HALF_UP)),
                        numFactory)
                : List.of(numFactory);
        for (NumFactory factory : precisions) {
            for (boolean retained : new boolean[] { false, true }) {
                for (EquityCurveMode mode : EquityCurveMode.values()) {
                    for (int origin = 0; origin < 5; origin++) {
                        for (int[] order : List.of(new int[] { 0, 1, 2 }, new int[] { 1, 0, 2 })) {
                            BarSeries series = FuturesAnalysisTestSupport.series(factory, 100, 100, 100, 100);
                            if (retained)
                                series.setMaximumBarCount(2);
                            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(factory)
                                    .toBuilder()
                                    .contractSize(factory.one())
                                    .build();
                            BaseTradingRecord nativeRecord = FuturesAnalysisTestSupport.fundedRecord(contract, factory,
                                    150);
                            nativeRecord.operate(FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.BUY, 1, 100,
                                    List.of(FuturesAnalysisTestSupport.commission(factory, -7.2))));
                            Position nativeLot = nativeRecord.getOpenPositions().getFirst();
                            Position spot = new Position(Trade.buyAt(0, factory.hundred(), factory.one()),
                                    Trade.sellAt(2, factory.numOf(110), factory.one()), new ZeroCostModel(),
                                    new ZeroCostModel());
                            BaseTradingRecord initial = origin == 0 ? new BaseTradingRecord()
                                    : origin == 1 ? new BaseTradingRecord(spot)
                                            : origin == 2
                                                    ? FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150)
                                                    : nativeRecord;
                            CashFlow curve = origin == 4
                                    ? new CashFlow(series,
                                            FuturesAnalysisTestSupport.openPosition(contract, -1, 1, 100), mode)
                                    : new CashFlow(series, initial, 3, mode, OpenPositionHandling.MARK_TO_MARKET);
                            BaseTradingRecord zero = FuturesAnalysisTestSupport.fundedRecord(contract, factory, 150);
                            zero.operate(
                                    FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 2, 100, List.of()));
                            zero.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 2, 100,
                                    List.of()));
                            java.math.BigDecimal nativeContribution = new java.math.BigDecimal("7.2")
                                    .divide(java.math.BigDecimal.valueOf(origin < 2 || origin == 4 ? 100 : 150));
                            java.math.BigDecimal spotFactor = new java.math.BigDecimal("1.1");
                            java.math.BigDecimal spotState = origin == 1 ? spotFactor : java.math.BigDecimal.ONE;
                            java.math.BigDecimal nativeState = origin == 3 ? nativeContribution
                                    : java.math.BigDecimal.ZERO;
                            boolean nativeSeen = origin == 3;
                            List<Position> updates = List.of(nativeLot, spot, zero.getPositions().getFirst());
                            for (int next : order) {
                                if ((next == 0 && origin == 3) || (next == 1 && origin == 1))
                                    continue;
                                List<Num> before = curve.stream().toList();
                                Num beforeBaseline = curve.getBaselineValue();
                                curve.calculatePosition(updates.get(next), 3);
                                if (next == 0) {
                                    nativeState = nativeState.add(nativeContribution);
                                    nativeSeen = true;
                                } else if (next == 1) {
                                    spotState = spotState.multiply(spotFactor);
                                    nativeState = nativeState.multiply(spotFactor);
                                } else {
                                    assertEquals(before, curve.stream().toList());
                                    assertNumEquals(beforeBaseline, curve.getBaselineValue());
                                }
                                // Native cash is additive; later spot gains scale only
                                // the components already accepted. Entering equity is
                                // before the spot exit on the retained head.
                                Num expected = factory.numOf(spotState.add(nativeState));
                                assertNumEquals(expected, curve.getValue(2));
                                assertNumEquals(expected, curve.getValue(3));
                                assertNumEquals(factory.numOf(java.math.BigDecimal.ONE
                                        .add(retained && nativeSeen ? nativeContribution : java.math.BigDecimal.ZERO)),
                                        curve.getBaselineValue());
                            }
                            List<Num> frozen = curve.stream().toList();
                            Num baseline = curve.getBaselineValue();
                            curve.calculatePosition(zero.getPositions().getFirst(), 3);
                            assertEquals(frozen, curve.stream().toList());
                            assertNumEquals(baseline, curve.getBaselineValue());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void deferredConstructorAcceptsExecutedCashContribution() {
        assertDeferredConstructorAcceptsExecutedCashContribution(false);
    }

    @Test
    public void deferredConstructorAcceptsExecutedInitialReturn() {
        assertDeferredConstructorAcceptsExecutedCashContribution(true);
    }

    private void assertDeferredConstructorAcceptsExecutedCashContribution(boolean metadataOnly) {
        List<Bar> bars = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Instant end = T0.plusSeconds(index);
            bars.add(new BaseBar(Duration.ofSeconds(1), end.minusSeconds(1), end, numFactory.hundred(),
                    numFactory.hundred(), numFactory.hundred(), numFactory.hundred(), numFactory.zero(),
                    numFactory.zero(), 0));
        }
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withBars(bars).build();
        series.setMaximumBarCount(2);
        assertEquals(2, series.getBeginIndex());
        assertEquals(3, series.getEndIndex());
        FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                .toBuilder()
                .contractSize(numFactory.one())
                .build();
        Trade deferredEntry = Trade
                .fromFill(FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1, 100, List.of())
                        .toBuilder()
                        .time(T0)
                        .build(), RecordedTradeCostModel.INSTANCE);
        Position deferred = new Position(deferredEntry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        Trade executed = Trade.fromFill(FuturesAnalysisTestSupport
                .fill(contract, 2, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -10)))
                .toBuilder()
                .time(T0.plusSeconds(2))
                .build(), RecordedTradeCostModel.INSTANCE);
        Position active = new Position(executed, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        assertNumEquals(0, FuturesPerformanceSupport.fallbackCapital(deferred));
        assertNumEquals(100, FuturesPerformanceSupport.entryNotional(active));
        assertNumEquals(10,
                active.getProfitComponents(3, numFactory.hundred()).stream().reduce(numFactory.zero(), Num::plus));
        CashFlow direct = new CashFlow(series, active, EquityCurveMode.REALIZED);
        CashFlow empty = new CashFlow(series, new BaseTradingRecord(), EquityCurveMode.REALIZED);
        empty.calculatePosition(active, 3);
        CashFlow funded = new CashFlow(series, FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 150),
                EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        funded.calculatePosition(active, 3);
        for (int index = 2; index <= 3; index++) {
            assertNumEquals(1.1, direct.getValue(index));
            assertNumEquals(1.1, empty.getValue(index));
            assertNumEquals(numFactory.numOf(16).dividedBy(numFactory.numOf(15)), funded.getValue(index));
        }
        assertNumEquals(1, direct.getBaselineValue());
        assertNumEquals(1, empty.getBaselineValue());
        assertNumEquals(1, funded.getBaselineValue());
        assertTrue(direct.hasInitialReturn());
        assertTrue(empty.hasInitialReturn());
        assertTrue(funded.hasInitialReturn());
        CashFlow curve = new CashFlow(series, deferred, EquityCurveMode.REALIZED);
        assertNumEquals(1, curve.getValue(2));
        assertNumEquals(1, curve.getValue(3));
        assertNumEquals(1, curve.getBaselineValue());
        assertFalse(curve.hasInitialReturn());
        curve.calculatePosition(active, 3);
        assertNumEquals(1, curve.getBaselineValue());
        assertEquals(2, curve.getSize());
        if (metadataOnly) {
            assertTrue("values=" + curve.stream().toList() + ", baseline=" + curve.getBaselineValue()
                    + ", initialReturn=" + curve.hasInitialReturn(), curve.hasInitialReturn());
        } else {
            assertNumEquals(1.1, curve.getValue(2));
            assertNumEquals(1.1, curve.getValue(3));
        }
    }

    @Test
    public void deferredConstructorTransitionsPreserveCapitalAndPublishedState() {
        for (boolean retained : new boolean[] { false, true }) {
            for (EquityCurveMode mode : EquityCurveMode.values()) {
                for (int origin = 0; origin < 4; origin++) {
                    BarSeries series = FuturesAnalysisTestSupport.series(numFactory, 100, 100, 100, 100);
                    if (retained)
                        series.setMaximumBarCount(2);
                    FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(numFactory)
                            .toBuilder()
                            .contractSize(numFactory.one())
                            .build();
                    Position position = FuturesAnalysisTestSupport.openPosition(contract, origin == 1 ? -1 : 2,
                            origin == 2 ? 2 : 1, 100);
                    BaseTradingRecord record = origin == 0 ? new BaseTradingRecord()
                            : FuturesAnalysisTestSupport.fundedRecord(contract, numFactory, 150);
                    if (origin == 2) {
                        record.operate(
                                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 2, 100, List.of()));
                    }
                    for (int constructor = 0; constructor < 13; constructor++) {
                        boolean singlePosition = constructor == 2 || constructor == 4 || constructor == 12;
                        if ((origin == 1) != singlePosition && origin != 2)
                            continue;
                        CashFlow curve = transitionCashConstructor(series, record, position, mode, constructor);
                        CashFlow control = transitionCashConstructor(series, record, position, mode, constructor);
                        assertNoExecutedCashUpdatePreservesState(curve, contract);
                        assertRejectedCashTransitionPreservesState(curve, contract);
                        for (double rebate : new double[] { 0, 10 }) {
                            Trade entry = Trade
                                    .fromFill(
                                            FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY,
                                                    rebate == 0 ? 2 : 1, 100, List.of(FuturesAnalysisTestSupport
                                                            .commission(numFactory, -rebate))),
                                            RecordedTradeCostModel.INSTANCE);
                            Position active = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
                            List<Num> before = curve.stream().toList();
                            Num baseline = curve.getBaselineValue();
                            curve.calculatePosition(active, 3);
                            control.calculatePosition(active, 3);
                            assertEquals(control.stream().toList(), curve.stream().toList());
                            assertNumEquals(control.getBaselineValue(), curve.getBaselineValue());
                            assertEquals(control.getSize(), curve.getSize());
                            assertEquals(retained, curve.hasInitialReturn());
                            if (rebate == 0) {
                                // Executed notional200 with no P&L does not set the
                                // unresolved origin's later per-position denominator.
                                assertEquals(before, curve.stream().toList());
                                assertNumEquals(baseline, curve.getBaselineValue());
                            } else {
                                int capital = singlePosition ? origin == 2 ? 200 : 100 : origin == 0 ? 100 : 150;
                                Num expected = numFactory.one()
                                        .plus(numFactory.numOf(10).dividedBy(numFactory.numOf(capital)));
                                assertNumEquals(expected, curve.getValue(2));
                                assertNumEquals(expected, curve.getValue(3));
                                assertNumEquals(1, curve.getBaselineValue());
                            }
                            assertNoExecutedCashUpdatePreservesState(curve, contract);
                            assertZeroPnLCashUpdatePreservesState(curve, contract);
                            assertZeroPnLCashUpdatePreservesState(control, contract);
                            assertRejectedCashTransitionPreservesState(curve, contract);
                        }
                        // A valid update after the established-state rejection also
                        // exposes any unpublished accumulator or capital mutation.
                        Trade laterEntry = Trade.fromFill(
                                FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.BUY, 2, 100,
                                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -20))),
                                RecordedTradeCostModel.INSTANCE);
                        Position later = new Position(laterEntry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
                        curve.calculatePosition(later, 3);
                        control.calculatePosition(later, 3);
                        assertEquals(control.stream().toList(), curve.stream().toList());
                        assertNumEquals(control.getBaselineValue(), curve.getBaselineValue());
                        assertEquals(control.getSize(), curve.getSize());
                        assertEquals(control.hasInitialReturn(), curve.hasInitialReturn());
                        int fixedCapital = singlePosition ? origin == 2 ? 200 : 0 : origin == 0 ? 0 : 150;
                        Num expected = fixedCapital == 0 ? numFactory.numOf(1.2)
                                : numFactory.one().plus(numFactory.numOf(30).dividedBy(numFactory.numOf(fixedCapital)));
                        assertNumEquals(expected, curve.getValue(3));
                        assertNoExecutedCashUpdatePreservesState(curve, contract);
                    }
                }
            }
        }
    }

    private CashFlow transitionCashConstructor(BarSeries series, TradingRecord record, Position position,
            EquityCurveMode mode, int constructor) {
        return switch (constructor) {
        case 0 -> new CashFlow(series, record, 3, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 1 -> new CashFlow(series, record, 0, 3, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 2 -> new CashFlow(series, position, mode);
        case 3 -> new CashFlow(series, record, 3, mode);
        case 4 -> new CashFlow(series, position);
        case 5 -> new CashFlow(series, record);
        case 6 -> new CashFlow(series, record, mode);
        case 7 -> new CashFlow(series, record, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 8 -> new CashFlow(series, record, 3);
        case 9 -> new CashFlow(series, record, OpenPositionHandling.MARK_TO_MARKET);
        case 10 ->
            new CashFlow(series, record, new ClosePriceIndicator(series), 3, mode, OpenPositionHandling.MARK_TO_MARKET);
        case 11 -> new CashFlow(series, record, new ClosePriceIndicator(series), 0, 3, mode,
                OpenPositionHandling.MARK_TO_MARKET);
        case 12 -> new CashFlow(series, position, new ClosePriceIndicator(series), mode);
        default -> throw new AssertionError("Unknown constructor");
        };
    }

    private void assertNoExecutedCashUpdatePreservesState(CashFlow curve, FuturesContract contract) {
        List<Num> published = curve.stream().toList();
        Num baseline = curve.getBaselineValue();
        int size = curve.getSize();
        boolean head = curve.hasInitialReturn();
        for (int entryIndex : new int[] { -1, curve.getEndIndex() + 1 }) {
            curve.calculatePosition(FuturesAnalysisTestSupport.openPosition(contract, entryIndex, 1, 100),
                    curve.getEndIndex());
            assertEquals(published, curve.stream().toList());
            assertNumEquals(baseline, curve.getBaselineValue());
            assertEquals(size, curve.getSize());
            assertEquals(head, curve.hasInitialReturn());
        }
    }

    private void assertRejectedCashTransitionPreservesState(CashFlow curve, FuturesContract contract) {
        List<Num> published = curve.stream().toList();
        Num baseline = curve.getBaselineValue();
        int size = curve.getSize();
        boolean head = curve.hasInitialReturn();
        RuntimeException failure = new IllegalArgumentException("holding source failed during cash accumulation");
        AtomicBoolean stagedFailure = new AtomicBoolean();
        Trade entry = Trade.fromFill(
                FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 1, 100,
                        List.of(FuturesAnalysisTestSupport.commission(numFactory, -30))),
                RecordedTradeCostModel.INSTANCE);
        ZeroCostModel holding = new ZeroCostModel() {
            @Override
            public Num calculate(Position position, int index) {
                // Prepricing succeeds at3; the injected failure is in staged
                // accumulation at2, before any state may be published.
                if (index == 2) {
                    stagedFailure.set(true);
                    throw failure;
                }
                return numFactory.zero();
            }
        };
        Position rejected = new Position(entry, RecordedTradeCostModel.INSTANCE, holding);
        assertSame(failure, assertThrows(RuntimeException.class, () -> curve.calculatePosition(rejected, 3)));
        assertTrue(stagedFailure.get());
        assertEquals(published, curve.stream().toList());
        assertNumEquals(baseline, curve.getBaselineValue());
        assertEquals(size, curve.getSize());
        assertEquals(head, curve.hasInitialReturn());
    }

    private void assertZeroPnLCashUpdatePreservesState(CashFlow curve, FuturesContract contract) {
        List<Num> published = curve.stream().toList();
        Num baseline = curve.getBaselineValue();
        int size = curve.getSize();
        boolean head = curve.hasInitialReturn();
        Trade entry = Trade.fromFill(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.BUY, 2, 100, List.of()),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 2, 100, List.of()),
                RecordedTradeCostModel.INSTANCE);
        Position zero = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        assertNumEquals(200, FuturesPerformanceSupport.entryNotional(zero));
        assertNumEquals(0,
                zero.getProfitComponents(3, numFactory.hundred()).stream().reduce(numFactory.zero(), Num::plus));
        curve.calculatePosition(zero, 3);
        assertEquals(published, curve.stream().toList());
        assertNumEquals(baseline, curve.getBaselineValue());
        assertEquals(size, curve.getSize());
        assertEquals(head, curve.hasInitialReturn());
    }

}
