/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.AnalysisContext.MissingHistoryPolicy;
import org.ta4j.core.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.AverageCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * Unit tests for window-aware {@link AnalysisCriterion} calculations.
 */
public class AnalysisCriterionWindowedCalculationTest {

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
        AnalysisContext context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

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
    public void fullyContainedPolicyExcludesBoundaryCrossingPosition() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(2, series), Trade.sellAt(5, series),
                Trade.buyAt(6, series), Trade.sellAt(8, series));
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED);

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
    public void clampModeWithNoIntersectionReturnsEmptyProjectedResult() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisContext context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(0, result);
    }

    @Test
    public void clampModeWithNoIntersectionReturnsReturnNeutralValue() {
        BarSeries series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        NetReturnCriterion criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        AnalysisContext context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

        Num result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(1, result);
    }

    @Test
    public void projectedTradingRecordIsReadOnly() {
        BarSeries series = buildSeries(10);
        TradingRecord record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series));
        NumberOfPositionsCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisContext context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);
        AnalysisWindowing.ResolvedWindow resolved = AnalysisWindowing.resolve(series, AnalysisWindow.barRange(0, 3),
                context);
        TradingRecord projected = AnalysisWindowing.projectTradingRecord(series, record, resolved, context);

        assertNumEquals(1, criterion.calculate(series, projected));
        assertThrows(UnsupportedOperationException.class, () -> projected.enter(0));
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
