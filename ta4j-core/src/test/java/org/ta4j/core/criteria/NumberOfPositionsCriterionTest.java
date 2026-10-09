/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisContext;
import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0, buyAndHold.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoPositions() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(2, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithStatusFilters() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
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
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
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
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
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
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        Position emptyPosition = new Position();
        Position openPosition = new Position();
        openPosition.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        Position closedPosition = new Position();
        closedPosition.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        closedPosition.operate(2, series.getBar(2).getClosePrice(), numFactory.one());
        AnalysisCriterion positionsCriterion = getCriterion();

        assertNumEquals(1, positionsCriterion.calculate(series, emptyPosition));
        assertNumEquals(1, positionsCriterion.calculate(series, openPosition));
        assertNumEquals(1, positionsCriterion.calculate(series, closedPosition));
    }

    @Test
    public void calculatePositionWithOpenStatusFilter() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        Position emptyPosition = new Position();
        Position openPosition = new Position();
        openPosition.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        Position closedPosition = new Position();
        closedPosition.operate(0, series.getBar(0).getClosePrice(), numFactory.one());
        closedPosition.operate(2, series.getBar(2).getClosePrice(), numFactory.one());
        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(0, closedCriterion.calculate(series, emptyPosition));
        assertNumEquals(0, closedCriterion.calculate(series, openPosition));
        assertNumEquals(1, closedCriterion.calculate(series, closedPosition));
        assertNumEquals(0, openCriterion.calculate(series, emptyPosition));
        assertNumEquals(1, openCriterion.calculate(series, openPosition));
        assertNumEquals(0, allCriterion.calculate(series, emptyPosition));
        assertNumEquals(1, allCriterion.calculate(series, openPosition));
        assertNumEquals(1, allCriterion.calculate(series, closedPosition));
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

    @Test
    public void countsLotsActiveAtLogicalEndInsteadOfLaterRecordState() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("position-count-window", numFactory, 5,
                100d, 110d, 110d, 110d, 110d, 120d, 120d, 120d, 130d, 130d, 130d, 130d);
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(120), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(10, Instant.EPOCH.plusSeconds(10), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(11, Instant.EPOCH.plusSeconds(11), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));

        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(0, closedCriterion.calculate(series, record));
        assertNumEquals(2, openCriterion.calculate(series, record));
        assertNumEquals(2, allCriterion.calculate(series, record));
        AnalysisWindow visibleWindow = AnalysisWindow.lookbackBars(6);
        assertNumEquals(0, closedCriterion.calculate(series, record, visibleWindow));
        assertNumEquals(2, openCriterion.calculate(series, record, visibleWindow));
        assertNumEquals(2, allCriterion.calculate(series, record, visibleWindow));
    }

    @Test
    public void countsHistoricalClosedLotsAsOpenAtLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord();
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);

        assertNumEquals(2, openCriterion.calculate(series, record));
    }

    @Test
    public void countsHistoricalClosedLotsAsOpenInLookbackWindow() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord();
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisWindow visibleWindow = AnalysisWindow.lookbackBars(6);

        assertNumEquals(2, openCriterion.calculate(series, record, visibleWindow));
    }

    @Test
    public void excludesHistoricalLotsFromClosedCountInLookbackWindow() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord();
        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisWindow visibleWindow = AnalysisWindow.lookbackBars(6);

        assertNumEquals(0, closedCriterion.calculate(series, record, visibleWindow));
    }

    @Test
    public void classifiesPositionByLogicalEnd() {
        BarSeries series = multiLotSeries();
        Position position = multiLotRecord().getPositions().getFirst();
        AnalysisCriterion closedCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        AnalysisCriterion openCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.OPEN);
        AnalysisCriterion allCriterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.ALL);

        assertNumEquals(0, closedCriterion.calculate(series, position));
        assertNumEquals(1, openCriterion.calculate(series, position));
        assertNumEquals(1, allCriterion.calculate(series, position));
    }

    private BarSeries multiLotSeries() {
        return ConstrainedSeriesSupport.trailingConstrainedSeries("position-count-multi-lot", numFactory, 5, 100d, 110d,
                110d, 110d, 110d, 120d, 120d, 120d, 130d, 130d, 130d, 130d);
    }

    private BaseTradingRecord multiLotRecord() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(120), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(10, Instant.EPOCH.plusSeconds(10), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(11, Instant.EPOCH.plusSeconds(11), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        return record;
    }

    @Test
    public void directClosedCountExcludesEntriesBeforeRecordStart() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
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
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion(
                NumberOfPositionsCriterion.PositionStatusFilter.CLOSED);
        assertNumEquals(2, criterion.calculate(series, record));
        assertNumEquals(4, criterion.calculate(series, new BaseTradingRecord(record.getPositions())));
        AnalysisWindow window = AnalysisWindow.barRange(2, 8);
        AnalysisContext context = AnalysisContext.defaults();
        assertNumEquals(3, criterion.calculate(series, record, window, context));
        assertNumEquals(2, criterion.calculate(series, record, window,
                context.withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)));
    }

}
