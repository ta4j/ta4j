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

public class PositionsRatioCriterionTest extends AbstractCriterionTest {

    public PositionsRatioCriterionTest(NumFactory numFactory) {
        super(params -> new PositionsRatioCriterion((PositionFilter) params[0]), numFactory);
    }

    @Test
    public void factoriesCreateWinningAndLosingCriteria() {
        AnalysisCriterion winning = PositionsRatioCriterion.winningPositionsRatioCriterion();
        AnalysisCriterion losing = PositionsRatioCriterion.losingPositionsRatioCriterion();

        assertTrue(winning.betterThan(numOf(2), numOf(1)));
        assertTrue(losing.betterThan(numOf(1), numOf(2)));
    }

    @Test
    public void calculate() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 95d, 102d, 105d, 97d, 113d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        // there are 3 positions with 2 winning positions
        AnalysisCriterion winningPositionsRatio = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(2d / 3, winningPositionsRatio.calculate(series, tradingRecord));

        // there are 3 positions with 1 losing positions
        AnalysisCriterion losingPositionsRatio = getCriterion(PositionFilter.LOSS);
        assertNumEquals(1d / 3, losingPositionsRatio.calculate(series, tradingRecord));

    }

    @Test
    public void calculateWithShortPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 95d, 102d, 105d, 97d, 113d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series));

        // there are 3 positions with 1 winning positions
        AnalysisCriterion winningPositionsRatio = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(0.5, winningPositionsRatio.calculate(series, tradingRecord));

        // there are 3 positions with 1 losing positions
        AnalysisCriterion losingPositionsRatio = getCriterion(PositionFilter.LOSS);
        assertNumEquals(0.5, losingPositionsRatio.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 95d, 102d, 105d, 97d, 113d)
                .build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // 0 winning position
        AnalysisCriterion winningPositionsRatio = getCriterion(PositionFilter.PROFIT);
        assertNumEquals(numOf(0), winningPositionsRatio.calculate(series, position));

        // 1 winning position
        position = new Position(Trade.buyAt(1, series), Trade.sellAt(2, series));
        assertNumEquals(1, winningPositionsRatio.calculate(series, position));

        // 1 losing position
        position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion losingPositionsRatio = getCriterion(PositionFilter.LOSS);
        assertNumEquals(numOf(1), losingPositionsRatio.calculate(series, position));

        // 0 losing position
        position = new Position(Trade.buyAt(1, series), Trade.sellAt(2, series));
        assertNumEquals(0, losingPositionsRatio.calculate(series, position));

    }

    @Test
    public void betterThan() {
        AnalysisCriterion winningPositionsRatio = getCriterion(PositionFilter.PROFIT);
        assertTrue(winningPositionsRatio.betterThan(numOf(12), numOf(8)));
        assertFalse(winningPositionsRatio.betterThan(numOf(8), numOf(12)));

        AnalysisCriterion losingPositionsRatio = getCriterion(PositionFilter.LOSS);
        assertTrue(losingPositionsRatio.betterThan(numOf(8), numOf(12)));
        assertFalse(losingPositionsRatio.betterThan(numOf(12), numOf(8)));
    }

    @Test
    public void testCalculateOneOpenPositionShouldReturnZero() {
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(PositionFilter.PROFIT), 0);
        openedPositionUtils.testCalculateOneOpenPositionShouldReturnExpectedValue(numFactory,
                getCriterion(PositionFilter.LOSS), 0);
    }

    @Test
    public void positionLevelResultFollowsReturnRepresentation() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 95d, 102d, 105d, 97d, 113d)
                .build();
        // Single winning position: 102/95 > 1
        Position winningPosition = new Position(Trade.buyAt(1, series), Trade.sellAt(2, series));
        TradingRecord recordWithOneWinningPosition = new BaseTradingRecord(Trade.buyAt(1, series),
                Trade.sellAt(2, series));

        // MULTIPLICATIVE: 1 + 1 = 2.0, equal to the record-level result for the same
        // position
        var multiplicativeCriterion = new PositionsRatioCriterion(PositionFilter.PROFIT,
                ReturnRepresentation.MULTIPLICATIVE);
        var positionResult = multiplicativeCriterion.calculate(series, winningPosition);
        assertNumEquals(numOf(2), positionResult);
        assertNumEquals(multiplicativeCriterion.calculate(series, recordWithOneWinningPosition), positionResult);

        // PERCENTAGE: 1 * 100 = 100.0, equal to the record-level result
        var percentageCriterion = new PositionsRatioCriterion(PositionFilter.PROFIT, ReturnRepresentation.PERCENTAGE);
        var positionPercentage = percentageCriterion.calculate(series, winningPosition);
        assertNumEquals(numOf(100), positionPercentage);
        assertNumEquals(percentageCriterion.calculate(series, recordWithOneWinningPosition), positionPercentage);

        // DECIMAL keeps returning the raw ratio (0 or 1)
        var decimalCriterion = new PositionsRatioCriterion(PositionFilter.PROFIT, ReturnRepresentation.DECIMAL);
        assertNumEquals(numOf(1), decimalCriterion.calculate(series, winningPosition));
        assertNumEquals(numOf(0),
                decimalCriterion.calculate(series, new Position(Trade.buyAt(0, series), Trade.sellAt(1, series))));

        // Losing position under MULTIPLICATIVE: 1 + 0 = 1.0
        var losingPosition = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        assertNumEquals(numOf(1), multiplicativeCriterion.calculate(series, losingPosition));
    }

}
