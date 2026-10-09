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
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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

    @Test
    public void directBoundedRecordExcludesEarlierEntryAndFutureExit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 90, 100, 80, 100, 95, 100, 90, 70)
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
        // Only the losses entered at 3 and 5 are closed within [2, 8].
        assertNumEquals(2, getCriterion().calculate(series, record));
    }

}
