/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class GrossReturnCriterionTest extends AbstractPnlCriterionTest {

    public GrossReturnCriterionTest(NumFactory numFactory) {
        super(params -> new GrossReturnCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(1.26, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(0.665, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(1.155, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(1.26, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(0.665, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(0.5413533835, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 1);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(1.10, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(1, result);
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
        GrossReturnCriterion criterion = new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        // Compound only 120/100 and 105/100.
        assertNumEquals(1.26, criterion.calculate(series, record));
        AnalysisWindow window = AnalysisWindow.barRange(2, 8);
        AnalysisContext context = AnalysisContext.defaults();
        assertNumEquals(1.386, criterion.calculate(series, record, window, context));
        assertNumEquals(1.26, criterion.calculate(series, record, window,
                context.withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)));
        assertNumEquals(1.8018, criterion.calculate(series, new BaseTradingRecord(record.getPositions())));
    }

}
