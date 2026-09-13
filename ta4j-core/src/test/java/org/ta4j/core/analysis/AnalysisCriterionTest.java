
/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.pnl.NetProfitLossPercentageCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies window projection of native futures positions for analysis criteria.
 */
public class AnalysisCriterionTest {

    @Test
    public void windowProjectionKeepsOnlyThePositionsSelectedByThePolicy() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 100, 110, 110, 115, 120);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 1_000);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 2, ExecutionSide.SELL, 100, 110,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.BUY, 100, 110,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));
            record.operate(FuturesAnalysisTestSupport.fill(contract, 5, ExecutionSide.SELL, 100, 120,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));

            NetReturnCriterion criterion = new NetReturnCriterion();
            AnalysisContext exitInWindow = AnalysisContext.defaults();
            assertNumEquals(1.016, criterion.calculate(barSeries, record));
            assertNumEquals(1.008, criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 5), exitInWindow));
            assertNumEquals(1.008, criterion.calculate(barSeries, record, AnalysisWindow.barRange(0, 2), exitInWindow));
            assertNumEquals(1.008,
                    criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 5), AnalysisContext.defaults()
                            .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED)));
            assertNumEquals(1.0, criterion.calculate(barSeries, record, AnalysisWindow.barRange(3, 4), exitInWindow));
        }
    }

    @Test
    public void windowProjectionClosesOpenFuturesPositionsAtTheMark() {
        for (org.ta4j.core.num.NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 105, 110, 110, 115, 120);
            BaseTradingRecord record = FuturesAnalysisTestSupport.fundedRecord(contract, testFactory, 1_000);
            record.operate(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100,
                    List.of(FuturesAnalysisTestSupport.commission(testFactory, 1))));
            record.recordCashFlow(FuturesAnalysisTestSupport.variationMargin(contract, 1, 3));

            NetReturnCriterion criterion = new NetReturnCriterion();
            AnalysisWindow window = AnalysisWindow.barRange(0, 2);
            AnalysisContext marked = AnalysisContext.defaults()
                    .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);

            assertNumEquals(1.009, criterion.calculate(barSeries, record, window, marked));

            CashFlow equity = new CashFlow(barSeries, record, EquityCurveMode.MARK_TO_MARKET,
                    OpenPositionHandling.MARK_TO_MARKET);
            CashFlow realized = new CashFlow(barSeries, record, EquityCurveMode.REALIZED,
                    OpenPositionHandling.MARK_TO_MARKET);
            assertNumEquals(1.009, equity.getValue(2));
            assertNumEquals(1.002, realized.getValue(2));
            assertNumEquals(1.0, criterion.calculate(barSeries, record, window, AnalysisContext.defaults()));
        }
    }

    @Test
    public void windowProjectionIgnoresDeferredEntryFills() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 100, 105);
            TradeFill executed = FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of());
            TradeFill deferred = FuturesAnalysisTestSupport.fill(contract, -1, ExecutionSide.BUY, 1_000, 100,
                    List.of());
            Trade entry = Trade.fromFills(TradeType.BUY, List.of(executed, deferred), RecordedTradeCostModel.INSTANCE);
            BaseTradingRecord record = new BaseTradingRecord(
                    new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel()));

            NetProfitLossPercentageCriterion criterion = new NetProfitLossPercentageCriterion(
                    ReturnRepresentation.MULTIPLICATIVE);
            AnalysisContext marked = AnalysisContext.defaults()
                    .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);

            // Only the executed 1000 contracts are marked and only their settlement
            // notional
            // of 1000 USD is the exposure: 1 + 50 / 1000.
            assertNumEquals(1.05, criterion.calculate(barSeries, record, AnalysisWindow.barRange(0, 2), marked));
        }
    }

    @Test
    public void windowProjectionUsesExitFillIndicesForInclusion() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 100, 110, 115, 120, 125, 130);
            AnalysisContext marked = AnalysisContext.defaults()
                    .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);
            NetProfitCriterion criterion = new NetProfitCriterion();

            // The exit trade aggregates to its first fill index (1), yet it also
            // executes 50 contracts inside the window; an included position
            // contributes its whole realized profit: 0.01 * (50 * 10 + 50 * 20).
            BaseTradingRecord imported = new BaseTradingRecord(new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100,
                                    List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFills(TradeType.SELL, List.of(
                            FuturesAnalysisTestSupport.fill(contract, 1, ExecutionSide.SELL, 50, 110, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 50, 120, List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel()));

            assertNumEquals(15, criterion.calculate(barSeries, imported, AnalysisWindow.barRange(2, 4), marked));

            // An exit fill beyond the window end breaks full containment.
            BaseTradingRecord spanning = new BaseTradingRecord(new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100,
                                    List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFills(TradeType.SELL, List.of(
                            FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 50, 120, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 6, ExecutionSide.SELL, 50, 125, List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel()));

            assertNumEquals(0,
                    criterion.calculate(barSeries, spanning, AnalysisWindow.barRange(0, 4), AnalysisContext.defaults()
                            .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED)));
        }
    }

    @Test
    public void markedFullyContainedWindowKeepsExposureAfterPartialExit() {
        for (NumFactory testFactory : FuturesAnalysisTestSupport.factories()) {
            FuturesContract contract = FuturesAnalysisTestSupport.linearBtcPerpetual(testFactory);
            BarSeries barSeries = FuturesAnalysisTestSupport.series(testFactory, 100, 100, 110, 120, 120, 120, 125);
            BaseTradingRecord spanning = new BaseTradingRecord(new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(FuturesAnalysisTestSupport.fill(contract, 0, ExecutionSide.BUY, 100, 100,
                                    List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFills(TradeType.SELL, List.of(
                            FuturesAnalysisTestSupport.fill(contract, 3, ExecutionSide.SELL, 50, 120, List.of()),
                            FuturesAnalysisTestSupport.fill(contract, 6, ExecutionSide.SELL, 50, 125, List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel()));

            AnalysisContext markedFullyContained = AnalysisContext.defaults()
                    .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET)
                    .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED);

            assertNumEquals(20, new NetProfitCriterion().calculate(barSeries, spanning, AnalysisWindow.barRange(0, 4),
                    markedFullyContained));
        }
    }
}
