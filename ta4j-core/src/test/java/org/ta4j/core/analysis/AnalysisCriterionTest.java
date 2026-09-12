
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
}
