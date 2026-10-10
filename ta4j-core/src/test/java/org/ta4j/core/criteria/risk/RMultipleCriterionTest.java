/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.risk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class RMultipleCriterionTest extends AbstractCriterionTest {

    private final PositionRiskModel riskModel;

    public RMultipleCriterionTest(NumFactory numFactory) {
        super(params -> new RMultipleCriterion((PositionRiskModel) params[0]), numFactory);
        this.riskModel = (series, position) -> numFactory.numOf(5);
    }

    @Test
    public void calculateWithNoPositions() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();

        assertNumEquals(0, getCriterion(riskModel).calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithWinningPosition() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 105).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        assertNumEquals(2, getCriterion(riskModel).calculate(series, position));
    }

    @Test
    public void calculateWithLosingPosition() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(110, 100, 105).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        assertNumEquals(-2, getCriterion(riskModel).calculate(series, position));
    }

    @Test
    public void calculateWithTradingRecordAveragesRMultiples() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 100, 95).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        assertNumEquals(0.5, getCriterion(riskModel).calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithTradingRecordSkipsInvalidRiskButCountsBreakEvenTrade() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 100, 100, 105, 110)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        PositionRiskModel selectiveRiskModel = (s, position) -> {
            int entryIndex = position.getEntry().getIndex();
            if (entryIndex == 0 || entryIndex == 2) {
                return numFactory.numOf(5);
            }
            if (entryIndex == 4) {
                return numFactory.zero();
            }
            return numFactory.numOf(5);
        };

        assertNumEquals(1, getCriterion(selectiveRiskModel).calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithTradingRecordReturnsZeroWhenAllRisksInvalid() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 95, 100).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        PositionRiskModel invalidRiskModel = (s, position) -> {
            int entryIndex = position.getEntry().getIndex();
            if (entryIndex == 0) {
                return numFactory.zero();
            }
            return numFactory.numOf(-2);
        };

        assertNumEquals(0, getCriterion(invalidRiskModel).calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion(riskModel);
        assertTrue(criterion.betterThan(numOf(1), numOf(0)));
        assertFalse(criterion.betterThan(numOf(0), numOf(1)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory, getCriterion(riskModel),
                0);
    }
}
