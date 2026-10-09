/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NetProfitCriterionTest extends AbstractPnlCriterionTest {

    public NetProfitCriterionTest(NumFactory numFactory) {
        super(params -> new NetProfitCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(21, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(15, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(25, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(10, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(0, result);
    }

    @Test
    public void directBoundedRecordExcludesEarlierEntryAndFutureExit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, 2, 8, new ZeroCostModel(),
                new ZeroCostModel());
        record.operate(Trade.buyAt(0, series, numFactory.one()));
        record.operate(Trade.sellAt(2, series, numFactory.one()));
        record.operate(Trade.buyAt(3, series, numFactory.one()));
        record.operate(Trade.sellAt(4, series, numFactory.one()));
        record.operate(Trade.buyAt(5, series, numFactory.one()));
        record.operate(Trade.sellAt(6, series, numFactory.one()));
        record.operate(Trade.buyAt(7, series, numFactory.one()));
        record.operate(Trade.sellAt(9, series, numFactory.one()));
        assertNumEquals(25, new NetProfitCriterion().calculate(series, record));
    }

    @Test
    public void explicitWindowPreservesDeclaredInclusionAndOpenPolicies() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, 2, 8, new ZeroCostModel(),
                new ZeroCostModel());
        record.operate(Trade.buyAt(0, series, numFactory.one()));
        record.operate(Trade.sellAt(2, series, numFactory.one()));
        record.operate(Trade.buyAt(3, series, numFactory.one()));
        record.operate(Trade.sellAt(4, series, numFactory.one()));
        record.operate(Trade.buyAt(5, series, numFactory.one()));
        record.operate(Trade.sellAt(6, series, numFactory.one()));
        record.operate(Trade.buyAt(7, series, numFactory.one()));
        record.operate(Trade.sellAt(9, series, numFactory.one()));
        NetProfitCriterion criterion = new NetProfitCriterion();
        AnalysisWindow window = AnalysisWindow.barRange(2, 8);
        AnalysisContext defaults = AnalysisContext.defaults();
        assertNumEquals(35, criterion.calculate(series, record, window, defaults));
        assertNumEquals(25, criterion.calculate(series, record, window,
                defaults.withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)));
        assertNumEquals(45, criterion.calculate(series, record, window,
                defaults.withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET)));
        TradingRecord unbounded = new BaseTradingRecord(record.getPositions());
        assertNumEquals(65, criterion.calculate(series, unbounded));
        assertEquals(4, record.getPositionCount());
        assertEquals(9, record.getPositions().getLast().getExit().getIndex());
    }

}
