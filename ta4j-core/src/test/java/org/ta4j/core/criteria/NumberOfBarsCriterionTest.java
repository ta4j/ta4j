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
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class NumberOfBarsCriterionTest extends AbstractCriterionTest {

    public NumberOfBarsCriterionTest(NumFactory numFactory) {
        super(params -> new NumberOfBarsCriterion(), numFactory);
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        AnalysisCriterion numberOfBars = getCriterion();
        assertNumEquals(0, numberOfBars.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion numberOfBars = getCriterion();
        assertNumEquals(6, numberOfBars.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        Position t = new Position(Trade.buyAt(2, series), Trade.sellAt(5, series));
        AnalysisCriterion numberOfBars = getCriterion();
        assertNumEquals(4, numberOfBars.calculate(series, t));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(6), numOf(2)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }
}
