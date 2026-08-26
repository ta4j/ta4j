/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.num.DecimalNumFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.ta4j.core.Position;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.criteria.CalmarRatioCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.SortinoRatioCriterion;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownBarLengthCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MonteCarloMaximumDrawdownCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Verifies that {@link EquityCurveCache} reuses one curve instance per
 * configuration key across repeated requests.
 */
public class EquityCurveCacheTest {

    private final DoubleNumFactory numFactory = DoubleNumFactory.getInstance();

    private BarSeries series() {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 2d, 4d, 3d, 5d, 4d, 6d, 5d, 7d)
                .build();
    }

    private TradingRecord closedPositionsRecord(BarSeries series) {
        return new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series), Trade.buyAt(6, series), Trade.sellAt(9, series));
    }

    @Test
    public void bundleReusesOneCurveInstancePerConfigurationKey() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CashFlow cashFlowMarkToMarket = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowAgain = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowRealized = equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        assertSame(cashFlowMarkToMarket, cashFlowAgain);
        assertNotSame(cashFlowMarkToMarket, cashFlowRealized);

        InvestedInterval investedInterval = equityCurveCache.investedInterval(OpenPositionHandling.MARK_TO_MARKET);
        assertSame(investedInterval, equityCurveCache.investedInterval(OpenPositionHandling.MARK_TO_MARKET));

        CumulativePnL cumulativePnL = equityCurveCache.cumulativePnL(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertSame(cumulativePnL,
                equityCurveCache.cumulativePnL(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
    }

    @Test
    public void bundleNormalizesRealizedModeCacheKeys() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        // REALIZED curves ignore open positions regardless of the requested
        // handling, so different handlings must share one cache entry.
        assertSame(equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET),
                equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE));
        assertSame(equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET),
                equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE));
    }

    @Test
    public void concurrentAccessReturnsSingleInstancePerKey() throws Exception {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Callable<Object>> tasks = new ArrayList<>();
        List<CashFlow> cashFlowResults = Collections.synchronizedList(new ArrayList<>());
        List<InvestedInterval> investedIntervalResults = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await();
                cashFlowResults.add(
                        equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
                investedIntervalResults.add(equityCurveCache.investedInterval(OpenPositionHandling.MARK_TO_MARKET));
                return null;
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (Future<Object> future : executor.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, new HashSet<>(cashFlowResults).size());
        assertEquals(1, new HashSet<>(investedIntervalResults).size());
        assertSame(cashFlowResults.getFirst(),
                equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
    }

    @Test
    public void cachedCurvesAreUnaffectedByInPlaceBarEdits() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);
        CashFlow cashFlow = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        Num before = cashFlow.getValue(5);

        // Without a structural input change the memoized curve is served as is;
        // an in-place bar edit can neither corrupt nor replace it.
        series.getBar(2).addPrice(numFactory.numOf(1000));

        CashFlow cached = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertSame(cashFlow, cached);
        assertNumEquals(before, cached.getValue(5));
    }

    @Test
    public void rebuildReflectsInPlaceEditsMadeBeforeStructuralChange() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);
        equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        series.getBar(2).addPrice(numFactory.numOf(1000));
        ((BaseTradingRecord) tradingRecord).operate(Trade.buyAt(10, series));

        CashFlow rebuilt = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow direct = new CashFlow(series, tradingRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(direct.getValue(5), rebuilt.getValue(5));
    }

    @Test
    public void bundledCurvesUsePrivateBarCopies() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        InvestedInterval investedInterval = equityCurveCache.investedInterval(OpenPositionHandling.MARK_TO_MARKET);
        investedInterval.getBarSeries().getBar(2).addPrice(numFactory.numOf(1000));

        CashFlow cashFlow = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNotSame(series.getBar(i), cashFlow.getBarSeries().getBar(i));
            assertNotSame(series.getBar(i), investedInterval.getBarSeries().getBar(i));
        }
        CashFlow direct = new CashFlow(series, tradingRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(direct.getValue(5), cashFlow.getValue(5));
    }

    @Test
    public void bundleRebuildsCurvesAfterSeriesAppend() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CashFlow cachedCurve = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        Duration period = series.getLastBar().getTimePeriod();
        series.barBuilder()
                .timePeriod(period)
                .endTime(series.getLastBar().getEndTime().plus(period))
                .openPrice(8d)
                .highPrice(8d)
                .lowPrice(8d)
                .closePrice(8d)
                .volume(1)
                .add();

        CashFlow rebuiltCurve = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityCurveCache(series, tradingRecord)
                .cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET)
                .getValue(series.getEndIndex()), rebuiltCurve.getValue(series.getEndIndex()));
    }

    @Test
    public void bundleRebuildsCurvesAfterNewTrades() {
        BarSeries series = series();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CumulativePnL cachedCurve = equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        tradingRecord.operate(Trade.buyAt(3, series));
        tradingRecord.operate(Trade.sellAt(5, series));

        CumulativePnL rebuiltCurve = equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityCurveCache(series, tradingRecord)
                .cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE)
                .getValue(series.getEndIndex()), rebuiltCurve.getValue(series.getEndIndex()));
    }

    @Test
    public void bundleRebuildsCurvesWhenCostModelsChange() {
        CostModel transactionCostModel = new ZeroCostModel();
        AtomicReference<CostModel> currentTransactionCostModel = new AtomicReference<>(transactionCostModel);
        BarSeries series = series();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY, transactionCostModel,
                new ZeroCostModel()) {
            @Override
            public CostModel getTransactionCostModel() {
                return currentTransactionCostModel.get();
            }
        };
        tradingRecord.operate(Trade.buyAt(0, series));
        tradingRecord.operate(Trade.sellAt(2, series));
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CumulativePnL cachedCurve = equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        currentTransactionCostModel.set(new LinearTransactionCostModel(0.01));

        CumulativePnL rebuiltCurve = equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityCurveCache(series, tradingRecord)
                .cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE)
                .getValue(series.getEndIndex()), rebuiltCurve.getValue(series.getEndIndex()));
    }

    @Test
    public void bundleReusesCurveWhenCostModelReturnsEquivalentInstance() {
        CostModel transactionCostModel = new ZeroCostModel();
        AtomicReference<CostModel> currentTransactionCostModel = new AtomicReference<>(transactionCostModel);
        BarSeries series = series();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY, transactionCostModel,
                new ZeroCostModel()) {
            @Override
            public CostModel getTransactionCostModel() {
                return currentTransactionCostModel.get();
            }
        };
        tradingRecord.operate(Trade.buyAt(0, series));
        tradingRecord.operate(Trade.sellAt(2, series));
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CashFlow cachedCurve = equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        // A record returning a fresh but semantically equal model instance must
        // not invalidate the cache: identity is not part of the fingerprint.
        currentTransactionCostModel.set(new ZeroCostModel());

        CashFlow reusedCurve = equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);
        assertSame(cachedCurve, reusedCurve);
    }

    @Test
    public void bundleDisablesReuseForSeriesWithoutRevisionTracking() {
        // Bars must share the anonymous series' default DecimalNumFactory.
        BarSeries trackedSeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(1d, 2d, 3d, 2d, 4d, 3d, 5d, 4d, 6d, 5d, 7d)
                .build();
        BarSeries untrackedSeries = new BaseBarSeries(trackedSeries.getName(), trackedSeries.getBarData()) {
            // Reports the documented unsupported revision default of -1:
            // retained-bar replacement cannot be ruled out, so reuse must be off.
            @Override
            public long getBarHistoryRevision() {
                return -1;
            }
        };
        TradingRecord tradingRecord = closedPositionsRecord(trackedSeries);
        EquityCurveCache equityCurveCache = new EquityCurveCache(untrackedSeries, tradingRecord);

        CashFlow firstCurve = equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);
        CashFlow secondCurve = equityCurveCache.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        // Revision -1 cannot rule out retained-bar replacement, so reuse is
        // disabled and every request observes a freshly computed curve.
        assertNotSame(firstCurve, secondCurve);
        assertNumEquals(firstCurve.getValue(untrackedSeries.getEndIndex()),
                secondCurve.getValue(untrackedSeries.getEndIndex()));
    }

    @Test
    public void scopeMatchesOnlyIdenticalInputs() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);

        EquityCurveCache found = EquityCurveCache.evaluate(series, tradingRecord, () -> {
            TradingRecord otherRecord = closedPositionsRecord(series);
            assertNull("a mismatched record must not resolve to the active bundle",
                    EquityCurveCache.current(series, otherRecord));
            return EquityCurveCache.current(series, tradingRecord);
        });

        assertNotNull(found);
        assertSame(series, found.getBarSeries());
        assertSame(tradingRecord, found.getTradingRecord());
        assertNull("no bundle may stay active after the evaluation", EquityCurveCache.current(series, tradingRecord));
    }

    @Test
    public void nestedScopesResolveInnermostFirstAndRestoreOuterState() {
        BarSeries series = series();
        TradingRecord outerRecord = closedPositionsRecord(series);
        TradingRecord innerRecord = closedPositionsRecord(series);

        EquityCurveCache innerBundle = EquityCurveCache.evaluate(series, outerRecord, () -> {
            EquityCurveCache outerBundle = EquityCurveCache.current(series, outerRecord);
            EquityCurveCache nested = EquityCurveCache.evaluate(series, innerRecord,
                    () -> EquityCurveCache.current(series, innerRecord));
            assertNotSame("each scope must get its own bundle", outerBundle, nested);
            return EquityCurveCache.current(series, outerRecord);
        });

        assertNotNull(innerBundle);
        assertNull("no bundle may stay active after the evaluations", EquityCurveCache.current(series, outerRecord));
    }

    @Test
    public void builtInCriteriaInsideScopeMatchDirectCalculation() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        List<AnalysisCriterion> criteria = List.of(new MaximumDrawdownCriterion(),
                new MaximumDrawdownCriterion(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE),
                new MaximumDrawdownBarLengthCriterion(), new MaximumAbsoluteDrawdownCriterion(),
                new ReturnOverMaxDrawdownCriterion(), new MonteCarloMaximumDrawdownCriterion(),
                new CalmarRatioCriterion(), new SharpeRatioCriterion(0.05), new SortinoRatioCriterion(0.02));

        for (int i = 0; i < criteria.size(); i++) {
            AnalysisCriterion criterion = criteria.get(i);
            Num direct = criterion.calculate(series, tradingRecord);
            Num shared = EquityCurveCache.evaluate(series, tradingRecord,
                    () -> criterion.calculate(series, tradingRecord));
            assertNumEquals(direct, shared);
        }
    }

    @Test
    public void bundleCachedCurvesRejectMutation() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityCurveCache equityCurveCache = new EquityCurveCache(series, tradingRecord);

        CashFlow cashFlow = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        CumulativePnL cumulativePnL = equityCurveCache.cumulativePnL(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        // The cached instances stay readable...
        assertNumEquals(cashFlow.getValue(series.getEndIndex()), cashFlow.getValue(series.getEndIndex()));
        assertNumEquals(cumulativePnL.getValue(series.getEndIndex()), cumulativePnL.getValue(series.getEndIndex()));
        assertSame(cashFlow, equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE));

        // ...but their accumulating operations must not alter shared state.
        TradingRecord emptyRecord = new BaseTradingRecord();
        assertThrows(UnsupportedOperationException.class,
                () -> cashFlow.calculate(emptyRecord, series.getEndIndex(), OpenPositionHandling.IGNORE));
        assertThrows(UnsupportedOperationException.class,
                () -> cumulativePnL.calculate(emptyRecord, series.getEndIndex(), OpenPositionHandling.IGNORE));
        for (Position position : closedPositionsRecord(series).getPositions()) {
            assertThrows(UnsupportedOperationException.class, () -> cashFlow.calculatePosition(position, 3));
            assertThrows(UnsupportedOperationException.class, () -> cumulativePnL.calculatePosition(position, 3));
        }
    }

    @Test
    public void bundleCurvesPreservePrunedSeriesIndices() {
        BarSeries pruned = series();
        TradingRecord tradingRecord = closedPositionsRecord(pruned);
        pruned.setMaximumBarCount(5);
        assertEquals(6, pruned.getBeginIndex());

        EquityCurveCache equityCurveCache = new EquityCurveCache(pruned, tradingRecord);
        CashFlow bundled = equityCurveCache.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);

        // Retained bars keep their absolute indices, and realized returns from
        // positions closed before the window carry into every retained cell.
        assertNumEquals(numFactory.numOf(4.5), bundled.getValue(6));
        assertNumEquals(numFactory.numOf(3.6), bundled.getValue(7));
        assertNumEquals(numFactory.numOf(5.3999999999999995), bundled.getValue(8));
        assertNumEquals(numFactory.numOf(4.5), bundled.getValue(9));
        assertNumEquals(numFactory.numOf(4.5), bundled.getValue(10));

        CashFlow direct = new CashFlow(pruned, tradingRecord, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        for (int i = pruned.getBeginIndex(); i <= pruned.getEndIndex(); i++) {
            assertNumEquals(direct.getValue(i), bundled.getValue(i));
        }
    }
}
