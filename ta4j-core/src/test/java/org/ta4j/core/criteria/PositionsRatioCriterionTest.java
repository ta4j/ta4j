/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

}
