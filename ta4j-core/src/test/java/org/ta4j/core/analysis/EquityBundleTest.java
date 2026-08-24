/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

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
import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Verifies that {@link EquityBundle} reuses one curve instance per
 * configuration key across repeated requests.
 */
public class EquityBundleTest {

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
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CashFlow cashFlowMarkToMarket = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowAgain = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CashFlow cashFlowRealized = equityBundle.cashFlow(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        assertSame(cashFlowMarkToMarket, cashFlowAgain);
        assertNotSame(cashFlowMarkToMarket, cashFlowRealized);

        InvestedInterval investedInterval = equityBundle.investedInterval(OpenPositionHandling.MARK_TO_MARKET);
        assertSame(investedInterval, equityBundle.investedInterval(OpenPositionHandling.MARK_TO_MARKET));

        CumulativePnL cumulativePnL = equityBundle.cumulativePnL(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertSame(cumulativePnL,
                equityBundle.cumulativePnL(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
    }

    @Test
    public void concurrentAccessReturnsSingleInstancePerKey() throws Exception {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Callable<Object>> tasks = new ArrayList<>();
        List<CashFlow> cashFlowResults = Collections.synchronizedList(new ArrayList<>());
        List<InvestedInterval> investedIntervalResults = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await();
                cashFlowResults.add(
                        equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
                investedIntervalResults.add(equityBundle.investedInterval(OpenPositionHandling.MARK_TO_MARKET));
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
                equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET));
    }

    @Test
    public void bundleRebuildsCurvesAfterSeriesAppend() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CashFlow cachedCurve = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
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

        CashFlow rebuiltCurve = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityBundle(series, tradingRecord)
                .cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET)
                .getValue(series.getEndIndex()), rebuiltCurve.getValue(series.getEndIndex()));
    }

    @Test
    public void bundleRebuildsCurvesAfterNewTrades() {
        BarSeries series = series();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CumulativePnL cachedCurve = equityBundle.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        tradingRecord.operate(Trade.buyAt(3, series));
        tradingRecord.operate(Trade.sellAt(5, series));

        CumulativePnL rebuiltCurve = equityBundle.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityBundle(series, tradingRecord)
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
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CumulativePnL cachedCurve = equityBundle.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        currentTransactionCostModel.set(new ZeroCostModel());

        CumulativePnL rebuiltCurve = equityBundle.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);
        assertNotSame(cachedCurve, rebuiltCurve);
        assertNumEquals(new EquityBundle(series, tradingRecord)
                .cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE)
                .getValue(series.getEndIndex()), rebuiltCurve.getValue(series.getEndIndex()));
    }

    @Test
    public void bundleRejectsMismatchedInputs() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        assertSame(series, equityBundle.getBarSeries());
        assertSame(tradingRecord, equityBundle.getTradingRecord());

        TradingRecord otherRecord = closedPositionsRecord(series);
        assertThrows(IllegalArgumentException.class, () -> equityBundle.requireInputsFor(series, otherRecord));
        assertThrows(IllegalArgumentException.class,
                () -> new MaximumDrawdownCriterion().calculate(series, otherRecord, equityBundle));
    }

    @Test
    public void bundleCachedCurvesRejectMutation() {
        BarSeries series = series();
        TradingRecord tradingRecord = closedPositionsRecord(series);
        EquityBundle equityBundle = new EquityBundle(series, tradingRecord);

        CashFlow cashFlow = equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        CumulativePnL cumulativePnL = equityBundle.cumulativePnL(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        // The cached instances stay readable...
        assertNumEquals(cashFlow.getValue(series.getEndIndex()), cashFlow.getValue(series.getEndIndex()));
        assertNumEquals(cumulativePnL.getValue(series.getEndIndex()), cumulativePnL.getValue(series.getEndIndex()));
        assertSame(cashFlow, equityBundle.cashFlow(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE));

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
}
