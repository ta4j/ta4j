/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

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
import org.ta4j.core.num.NumFactory;

public class SqnCriterionTest extends AbstractCriterionTest {

    public SqnCriterionTest(NumFactory numFactory) {
        super(params -> new SqnCriterion(), numFactory);
    }

    @Test
    public void calculateWithWinningLongPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion sqnCriterion = getCriterion();
        assertNumEquals(4.242640687119286, sqnCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithLosingLongPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion sqnCriterion = getCriterion();
        assertNumEquals(-1.9798989873223332, sqnCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneWinningAndOneLosingLongPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 195, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion sqnCriterion = getCriterion();
        assertNumEquals(0.7353910524340095, sqnCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithWinningShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 100, 95, 95, 100).build();
        var tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion sqnCriterion = getCriterion();
        assertNumEquals(4.242640687119286, sqnCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithLosingShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 100, 105, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(3, series));

        AnalysisCriterion sqnCriterion = getCriterion();
        assertNumEquals(-4.242640687119286, sqnCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(50), numOf(45)));
        assertFalse(criterion.betterThan(numOf(45), numOf(50)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

    @Test
    public void boundedRecordUsesSelectedPopulation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = boundedRecord(series, 2, 8);

        // Profits [20, 5]: mean 12.5, standard deviation 7.5, count 2.
        assertNumEquals((12.5 / 7.5) * Math.sqrt(2), getCriterion().calculate(series, record));
    }

    @Test
    public void emptySelectedPopulationPreservesEmptyRecordResult() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
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
