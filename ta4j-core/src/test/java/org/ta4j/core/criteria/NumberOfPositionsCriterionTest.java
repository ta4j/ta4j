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
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class NumberOfPositionsCriterionTest extends AbstractCriterionTest {

    public NumberOfPositionsCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 0 ? new NumberOfPositionsCriterion()
                : new NumberOfPositionsCriterion((boolean) params[0]), numFactory);
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0, buyAndHold.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(2, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithStatusFilters() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());
        tradingRecord.enter(3, series.getBar(3).getClosePrice(), numFactory.one());

        AnalysisCriterion defaultCriterion = getCriterion();
        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(1, defaultCriterion.calculate(series, tradingRecord));
        assertNumEquals(1, closedCriterion.calculate(series, tradingRecord));
        assertNumEquals(1, openCriterion.calculate(series, tradingRecord));
        assertNumEquals(2, allCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithStatusFiltersAndLookbackWindow() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());
        tradingRecord.enter(3, series.getBar(3).getClosePrice(), numFactory.one());

        AnalysisWindow lookbackWindow = AnalysisWindow.lookbackBars(2);
        AnalysisWindow lookbackWindowWithOpenEntry = AnalysisWindow.lookbackBars(3);
        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(0, closedCriterion.calculate(series, tradingRecord, lookbackWindow));
        assertNumEquals(0, openCriterion.calculate(series, tradingRecord, lookbackWindow));
        assertNumEquals(0, allCriterion.calculate(series, tradingRecord, lookbackWindow));
        assertNumEquals(1, openCriterion.calculate(series, tradingRecord, lookbackWindowWithOpenEntry));
        assertNumEquals(1, allCriterion.calculate(series, tradingRecord, lookbackWindowWithOpenEntry));
    }

    @Test
    public void calculateWithStatusFiltersAndEmptySeriesWindow() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, numFactory.one(), numFactory.one());

        AnalysisWindow lookbackWindow = AnalysisWindow.lookbackBars(1);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(0, openCriterion.calculate(series, tradingRecord, lookbackWindow));
        assertNumEquals(0, allCriterion.calculate(series, tradingRecord, lookbackWindow));
    }

    @Test
    public void calculateWithOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        Position position = new Position();
        AnalysisCriterion positionsCriterion = getCriterion();

        assertNumEquals(1, positionsCriterion.calculate(series, position));
    }

    @Test
    public void calculatePositionWithOpenStatusFilter() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        Position emptyPosition = new Position();
        Position openPosition = new Position();
        openPosition.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);

        assertNumEquals(0, openCriterion.calculate(series, emptyPosition));
        assertNumEquals(1, openCriterion.calculate(series, openPosition));
    }

    @Test
    public void betterThanWithLessIsBetter() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void betterThanWithLessIsNotBetter() {
        AnalysisCriterion criterion = getCriterion(false);
        assertFalse(criterion.betterThan(numOf(3), numOf(6)));
        assertTrue(criterion.betterThan(numOf(7), numOf(4)));
    }
}
