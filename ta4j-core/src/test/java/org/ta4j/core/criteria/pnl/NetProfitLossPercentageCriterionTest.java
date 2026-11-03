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
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NetProfitLossPercentageCriterionTest extends AbstractPnlCriterionTest {

    public NetProfitLossPercentageCriterionTest(NumFactory numFactory) {
        super(params -> new NetProfitLossPercentageCriterion(ReturnRepresentation.DECIMAL), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(0.105, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(-0.19325, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(0.075, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(0.125, result);
    }

    @Override
    protected void handleCalculateOnlyWithLossPositions(Num result) {
        assertNumEquals(-0.175, result);
    }

    @Override
    protected void handleCalculateProfitWithShortPositions(Num result) {
        assertNumEquals(-0.2121212121, result);
    }

    @Override
    protected void handleBetterThan(AnalysisCriterion criterion) {
        assertTrue(criterion.betterThan(numOf(0.05), numOf(0.03)));
        assertFalse(criterion.betterThan(numOf(0.03), numOf(0.05)));
    }

    @Override
    protected void handleCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

    @Override
    protected void handleCalculateWithOpenedPosition(Num result) {
        assertNumEquals(0.1, result);
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
        // Selected profit 25 divided by selected entry values 100 + 100.
        assertNumEquals(0.125, getCriterion().calculate(series, record));
    }

}
