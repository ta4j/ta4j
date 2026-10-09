/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.helpers;

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
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class AverageCriterionTest extends AbstractCriterionTest {

    public AverageCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 2 ? new AverageCriterion((AnalysisCriterion) params[0], (boolean) params[1])
                : new AverageCriterion((AnalysisCriterion) params[0]), numFactory);
    }

    @Test
    public void calculateStandardErrorPnL() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series, series.numFactory().one()),
                Trade.sellAt(2, series, series.numFactory().one()), Trade.buyAt(3, series, series.numFactory().one()),
                Trade.sellAt(5, series, series.numFactory().one()));

        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion());
        assertNumEquals(7.5, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void betterThanWithLessIsBetter() {
        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion(), true);
        assertFalse(criterion.betterThan(numOf(5000), numOf(4500)));
        assertTrue(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void betterThanWithLessIsNotBetter() {
        AnalysisCriterion criterion = getCriterion(new NetProfitLossCriterion());
        assertTrue(criterion.betterThan(numOf(5000), numOf(4500)));
        assertFalse(criterion.betterThan(numOf(4500), numOf(5000)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(new NetProfitLossCriterion()), 0);
    }

    @Test
    public void boundedRecordUsesSelectedPopulation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = boundedRecord(series, 2, 8);

        // Only profits 20 and 5 are selected: sum 25 / count 2.
        assertNumEquals(12.5, getCriterion(new NetProfitLossCriterion()).calculate(series, record));
    }

    @Test
    public void emptySelectedPopulationPreservesEmptyRecordResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = boundedRecord(series, 8, 8);

        // The source retains four closed positions, but none belongs to [8, 8].
        assertFalse(record.getPositions().isEmpty());
        assertNumEquals(0, getCriterion(new NetProfitLossCriterion()).calculate(series, record));
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
