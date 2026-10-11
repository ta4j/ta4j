/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class NumberOfConsecutivePositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfConsecutivePositionsCriterionTest(NumFactory numFactory) {
        super(params -> new NumberOfConsecutivePositionsCriterion((PositionFilter) params[0]), numFactory);
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        assertNumEquals(0, getCriterion(PositionFilter.LOSS).calculate(series, new BaseTradingRecord()));
        assertNumEquals(0, getCriterion(PositionFilter.PROFIT).calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoLongPositions() {
        var seriesLoss = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(110, 105, 100, 90, 80, 140)
                .build();
        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.buyAt(0, seriesLoss), Trade.sellAt(2, seriesLoss),
                Trade.buyAt(3, seriesLoss), Trade.sellAt(4, seriesLoss));
        assertNumEquals(2, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, tradingRecordLoss));

        var seriesProfit = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 130, 140)
                .build();
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.buyAt(1, seriesProfit),
                Trade.sellAt(3, seriesProfit), Trade.buyAt(3, seriesProfit), Trade.sellAt(4, seriesProfit));
        assertNumEquals(2, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, tradingRecordProfit));
    }

    @Test
    public void calculateWithOneLongPosition() {
        var seriesLoss = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(110, 105, 100, 90, 95, 105)
                .build();
        Position positionLoss = new Position(Trade.buyAt(1, seriesLoss), Trade.sellAt(3, seriesLoss));
        assertNumEquals(1, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, positionLoss));

        var seriesProfit = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 95, 105)
                .build();
        Position positionProfit = new Position(Trade.buyAt(1, seriesProfit), Trade.sellAt(3, seriesProfit));
        assertNumEquals(1, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, positionProfit));
    }

    @Test
    public void calculateWithTwoShortPositions() {
        var seriesLoss = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 110, 120, 95, 105)
                .build();
        TradingRecord tradingRecordLoss = new BaseTradingRecord(Trade.sellAt(0, seriesLoss), Trade.buyAt(1, seriesLoss),
                Trade.sellAt(3, seriesLoss), Trade.buyAt(5, seriesLoss));
        assertNumEquals(0, getCriterion(PositionFilter.LOSS).calculate(seriesLoss, tradingRecordLoss));

        var seriesProfit = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecordProfit = new BaseTradingRecord(Trade.sellAt(0, seriesProfit),
                Trade.buyAt(1, seriesProfit), Trade.sellAt(3, seriesProfit), Trade.buyAt(5, seriesProfit));
        assertNumEquals(0, getCriterion(PositionFilter.PROFIT).calculate(seriesProfit, tradingRecordProfit));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterionLoss = getCriterion(PositionFilter.LOSS);
        assertTrue(criterionLoss.betterThan(numOf(3), numOf(6)));
        assertFalse(criterionLoss.betterThan(numOf(7), numOf(4)));

        AnalysisCriterion criterionProfit = getCriterion(PositionFilter.PROFIT);
        assertFalse(criterionProfit.betterThan(numOf(3), numOf(6)));
        assertTrue(criterionProfit.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(PositionFilter.LOSS), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(PositionFilter.PROFIT), 0);
    }
}
