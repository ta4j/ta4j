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

public class NumberOfLosingPositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfLosingPositionsCriterionTest(NumFactory numFactory) {
        super(params -> new NumberOfLosingPositionsCriterion(), numFactory);
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        assertNumEquals(0, getCriterion().calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoLongPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series));

        assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneLongPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        Position position = new Position(Trade.buyAt(1, series), Trade.sellAt(3, series));

        assertNumEquals(1, getCriterion().calculate(series, position));
    }

    @Test
    public void calculateWithTwoShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(1, series),
                Trade.sellAt(3, series), Trade.buyAt(5, series));

        assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(), 0);
    }
}
