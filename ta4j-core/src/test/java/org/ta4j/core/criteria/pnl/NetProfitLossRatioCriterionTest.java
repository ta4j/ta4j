/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class NetProfitLossRatioCriterionTest extends AbstractPnlCriterionTest {

    public NetProfitLossRatioCriterionTest(NumFactory numFactory) {
        super(params -> new NetProfitLossRatioCriterion(), numFactory);
    }

    @Override
    protected void handleCalculateWithProfits(Num result) {
        assertNumEquals(1, result);
    }

    @Override
    protected void handleCalculateWithLosses(Num result) {
        assertNumEquals(0, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions(Num result) {
        assertNumEquals(1, result);
    }

    @Override
    protected void handleCalculateOnlyWithProfitPositions2(Num result) {
        assertNumEquals(1, result);
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
        assertNumEquals(1, result);
    }

    @Override
    protected void handleCalculateWithNoPositions(Num result) {
        assertNumEquals(0, result);
    }

    @Test
    public void boundedRecordUsesSelectedPopulation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 95, 100, 110, 160)
                .build();
        TradingRecord record = boundedRecord(series, 2, 8);

        // One selected profit 20 and one selected loss -5 give average-profit/loss
        // ratio 4.
        assertNumEquals(4, getCriterion().calculate(series, record));
    }

    @Test
    public void emptySelectedPopulationPreservesEmptyRecordResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 95, 100, 110, 160)
                .build();
        TradingRecord record = boundedRecord(series, 8, 8);

        // The source retains four closed positions, but none belongs to [8, 8].
        assertFalse(record.getPositions().isEmpty());
        assertNumEquals(0, getCriterion().calculate(series, record));
    }

    private TradingRecord boundedRecord(BarSeries series, int startIndex, int endIndex) {
        TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, startIndex, endIndex, new ZeroCostModel(),
                new ZeroCostModel());
        record.operate(Trade.buyAt(0, series, numFactory.one()));
        record.operate(Trade.sellAt(2, series, numFactory.one()));
        record.operate(Trade.buyAt(3, series, numFactory.one()));
        record.operate(Trade.sellAt(4, series, numFactory.one()));
        record.operate(Trade.buyAt(5, series, numFactory.one()));
        record.operate(Trade.sellAt(6, series, numFactory.one()));
        record.operate(Trade.buyAt(7, series, numFactory.one()));
        record.operate(Trade.sellAt(9, series, numFactory.one()));
        return record;
    }
}
