/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.AverageCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Unit tests for {@link AnalysisWindow} and window-aware
 * {@link AnalysisCriterion} calculations.
 */
public class AnalysisWindowTest {

    @Test
    public void barRangeFactoryCreatesBarRangeWindow() {
        AnalysisWindow window = AnalysisWindow.barRange(5, 12);

        assertTrue(window instanceof AnalysisWindow.BarRange);
        AnalysisWindow.BarRange barRange = (AnalysisWindow.BarRange) window;
        assertEquals(5, barRange.startIndexInclusive());
        assertEquals(12, barRange.endIndexInclusive());
    }

    @Test
    public void barRangeRejectsNegativeStart() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.barRange(-1, 10));
    }

    @Test
    public void barRangeRejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.barRange(10, 5));
    }

    @Test
    public void lookbackBarsFactoryCreatesLookbackBarsWindow() {
        AnalysisWindow window = AnalysisWindow.lookbackBars(30);

        assertTrue(window instanceof AnalysisWindow.LookbackBars);
        assertEquals(30, ((AnalysisWindow.LookbackBars) window).barCount());
    }

    @Test
    public void lookbackBarsRejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.lookbackBars(0));
    }

    @Test
    public void timeRangeFactoryCreatesTimeRangeWindow() {
        Instant start = Instant.parse("2026-02-10T00:00:00Z");
        Instant end = Instant.parse("2026-02-14T00:00:00Z");
        AnalysisWindow window = AnalysisWindow.timeRange(start, end);

        assertTrue(window instanceof AnalysisWindow.TimeRange);
        AnalysisWindow.TimeRange timeRange = (AnalysisWindow.TimeRange) window;
        assertEquals(start, timeRange.startInclusive());
        assertEquals(end, timeRange.endExclusive());
    }

    @Test
    public void timeRangeRejectsStartAtOrAfterEnd() {
        Instant instant = Instant.parse("2026-02-10T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.timeRange(instant, instant));
    }

    @Test
    public void lookbackDurationFactoryCreatesLookbackDurationWindow() {
        Duration duration = Duration.ofDays(7);
        AnalysisWindow window = AnalysisWindow.lookbackDuration(duration);

        assertTrue(window instanceof AnalysisWindow.LookbackDuration);
        assertEquals(duration, ((AnalysisWindow.LookbackDuration) window).duration());
    }

    @Test
    public void lookbackDurationRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.lookbackDuration(Duration.ZERO));
    }

    @Test
    public void windowedCalculateOverloadMatchesExplicitDefaultContext() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(6, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisWindow window = AnalysisWindow.barRange(4, 8);

        Num fromOverload = criterion.calculate(series, record, window);
        Num fromExplicitDefaults = criterion.calculate(series, record, window, AnalysisContext.defaults());

        assertNumEquals(fromExplicitDefaults, fromOverload);
    }

    @Test
    public void windowedCalculateRejectsNullSeries() {
        TradingRecord record = new BaseTradingRecord();
        AnalysisWindow window = AnalysisWindow.barRange(0, 1);
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        assertThrows(NullPointerException.class,
                () -> criterion.calculate(null, record, window, AnalysisContext.defaults()));
        assertThrows(NullPointerException.class, () -> criterion.calculate(null, record, window));
    }

    @Test
    public void windowedCalculateRejectsNullTradingRecord() {
        BarSeries series = buildSeries(2);
        AnalysisWindow window = AnalysisWindow.barRange(0, 1);
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        assertThrows(NullPointerException.class,
                () -> criterion.calculate(series, null, window, AnalysisContext.defaults()));
        assertThrows(NullPointerException.class, () -> criterion.calculate(series, null, window));
    }

    @Test
    public void windowedCalculateRejectsNullWindow() {
        BarSeries series = buildSeries(2);
        TradingRecord record = new BaseTradingRecord();
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        assertThrows(NullPointerException.class,
                () -> criterion.calculate(series, record, null, AnalysisContext.defaults()));
        assertThrows(NullPointerException.class, () -> criterion.calculate(series, record, null));
    }

    @Test
    public void windowedCalculateRejectsNullContext() {
        BarSeries series = buildSeries(2);
        TradingRecord record = new BaseTradingRecord();
        AnalysisWindow window = AnalysisWindow.barRange(0, 1);
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        assertThrows(NullPointerException.class, () -> criterion.calculate(series, record, window, null));
    }

    @Test
    public void defaultWindowedCalculateUsesStrictHistoryPolicy() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        assertThrows(IllegalArgumentException.class,
                () -> criterion.calculate(series, record, AnalysisWindow.barRange(2, 6)));
    }

    @Test
    public void clampModeIntersectsWindowWithAvailableMovingSeriesRange() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series),
                Trade.buyAt(7, series), Trade.sellAt(8, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(AnalysisContext.MissingHistoryPolicy.CLAMP);

        Num positions = criterion.calculate(series, record, AnalysisWindow.barRange(2, 8), context);
        assertNumEquals(2, positions);
    }

    @Test
    public void timeRangeUsesStartInclusiveEndExclusiveMembership() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        Instant startInclusive = series.getBar(4).getEndTime();
        Instant endExclusive = series.getBar(7).getEndTime();

        Num positions = criterion.calculate(series, record, AnalysisWindow.timeRange(startInclusive, endExclusive));
        assertNumEquals(1, positions);
    }

    @Test
    public void lookbackDurationUsesSeriesEndAnchorByDefault() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series), Trade.buyAt(8, series), Trade.sellAt(9, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        Num positions = criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(7)));
        assertNumEquals(2, positions);
    }

    @Test
    public void lookbackBarsUsesSeriesEndAnchorByDefault() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series),
                Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series), Trade.sellAt(9, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();

        Num positions = criterion.calculate(series, record, AnalysisWindow.lookbackBars(3));
        assertNumEquals(2, positions);
    }

    @Test
    public void lookbackBarsHonorsExplicitAsOfAnchor() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(5, series), Trade.buyAt(5, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        Instant asOf = series.getBar(6).getEndTime();
        AnalysisContext context = AnalysisContext.defaults().withAsOf(asOf);

        Num positions = criterion.calculate(series, record, AnalysisWindow.lookbackBars(3), context);
        assertNumEquals(2, positions);
    }

    @Test
    public void lookbackBarsStrictModeThrowsWhenAsOfIsOutsideAvailableHistory() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        Instant asOf = series.getBar(5).getEndTime().minus(Duration.ofDays(3));
        AnalysisContext context = AnalysisContext.defaults().withAsOf(asOf);

        assertThrows(IllegalArgumentException.class,
                () -> criterion.calculate(series, record, AnalysisWindow.lookbackBars(3), context));
    }

    @Test
    public void fullyContainedPolicyExcludesBoundaryCrossingPosition() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(2, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(8, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED);

        Num pnl = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8), context);
        assertNumEquals(2, pnl);
    }

    @Test
    public void markToMarketPolicyIncludesOpenPositionAtWindowEnd() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(6, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();

        Num excluded = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8));
        assertNumEquals(0, excluded);

        AnalysisContext context = AnalysisContext.defaults()
                .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);
        Num included = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8), context);
        assertNumEquals(2, included);
    }

    @Test
    public void fullyContainedPolicyExcludesMarkToMarketWhenEntryIsBeforeWindow() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(2, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8), context);
        assertNumEquals(0, result);
    }

    @Test
    public void fullyContainedPolicyIncludesEligibleLiveOpenLotsWithMarkToMarket() {
        BarSeries series = buildSeries(10);
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(6, new BaseTrade(6, series.getBar(6).getEndTime(), series.getBar(6).getClosePrice(),
                series.numFactory().one(), null, ExecutionSide.BUY, null, null));
        record.recordFill(7, new BaseTrade(7, series.getBar(7).getEndTime(), series.getBar(7).getClosePrice(),
                series.numFactory().one(), null, ExecutionSide.BUY, null, null));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(AnalysisContext.PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(7, 8), context);
        assertNumEquals(1, result);
    }

    @Test
    public void clampModeWithNoIntersectionReturnsEmptyProjectedResult() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(AnalysisContext.MissingHistoryPolicy.CLAMP);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(0, result);
    }

    @Test
    public void clampModeWithNoIntersectionReturnsReturnNeutralValue() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NetReturnCriterion criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(AnalysisContext.MissingHistoryPolicy.CLAMP);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(1, result);
    }

    @Test
    public void windowedCalculationDoesNotMutateSourceRecord() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(AnalysisContext.MissingHistoryPolicy.CLAMP);
        int originalTradeCount = record.getTrades().size();
        int originalPositionCount = record.getPositionCount();

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(0, 3), context);

        assertNumEquals(1, result);
        assertEquals(originalTradeCount, record.getTrades().size());
        assertEquals(originalPositionCount, record.getPositionCount());
    }

    @Test
    public void wholeWindowMatchesLegacyPnlCalculation() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(8, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisWindow fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        Num legacy = criterion.calculate(series, record);
        Num windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyReturnCalculation() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(8, series));
        NetReturnCriterion criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        AnalysisWindow fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        Num legacy = criterion.calculate(series, record);
        Num windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyDrawdownCalculation() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(8, series));
        MaximumDrawdownCriterion criterion = new MaximumDrawdownCriterion();
        AnalysisWindow fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        Num legacy = criterion.calculate(series, record);
        Num windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyHelperCalculation() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series),
                Trade.buyAt(4, series), Trade.sellAt(8, series));
        AverageCriterion criterion = new AverageCriterion(new NetProfitLossCriterion());
        AnalysisWindow fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        Num legacy = criterion.calculate(series, record);
        Num windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    private static BarSeries buildSeries(int barCount) {
        BarSeries series = new BaseBarSeriesBuilder().withName("windowed-series").build();
        Instant base = Instant.parse("2026-02-01T00:00:00Z");
        for (int i = 0; i < barCount; i++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(base.plus(Duration.ofDays(i)))
                    .openPrice(100 + i)
                    .highPrice(101 + i)
                    .lowPrice(99 + i)
                    .closePrice(100 + i)
                    .volume(1)
                    .amount(100 + i)
                    .trades(1)
                    .add();
        }
        return series;
    }
}
