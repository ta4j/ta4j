/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class ExpectancyCriterionTest extends AbstractCriterionTest {

    public ExpectancyCriterionTest(NumFactory numFactory) {
        super(params -> new ExpectancyCriterion(), numFactory);
    }

    @Test
    public void calculateOnlyWithProfitPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 150, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion avgLoss = getCriterion();
        assertNumEquals(1.0, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithMixedPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 80, 130, 150, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion avgLoss = getCriterion();
        assertNumEquals(0.25, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 80, 70, 60, 50).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        AnalysisCriterion avgLoss = getCriterion();
        assertNumEquals(0, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(160, 140, 120, 100, 80, 60).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(5, series));

        AnalysisCriterion avgLoss = getCriterion();
        assertNumEquals(1.0, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithMixedShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(160, 200, 120, 100, 80, 60).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(2, series), Trade.buyAt(5, series));

        AnalysisCriterion avgLoss = getCriterion();
        assertNumEquals(0.25, avgLoss.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }

}
