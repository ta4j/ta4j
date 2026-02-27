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
import org.ta4j.core.AnalysisContext.OpenPositionPolicy;
import org.ta4j.core.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.helpers.AverageCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;

/**
 * Unit tests for window-aware {@link AnalysisCriterion} calculations.
 */
public class AnalysisCriterionWindowedCalculationTest {

    @Test
    public void defaultWindowedCalculateUsesStrictHistoryPolicy() {
        var series = buildSeries(10);
        series.setMaximumBarCount(5);
        var record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        var criterion = new NumberOfPositionsCriterion();

        assertThrows(IllegalArgumentException.class,
                () -> criterion.calculate(series, record, AnalysisWindow.barRange(2, 6)));
    }

    @Test
    public void clampModeIntersectsWindowWithAvailableMovingSeriesRange() {
        var series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        var record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series), Trade.buyAt(7, series),
                Trade.sellAt(8, series));
        var criterion = new NumberOfPositionsCriterion();
        var context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

        var positions = criterion.calculate(series, record, AnalysisWindow.barRange(2, 8), context);
        assertNumEquals(2, positions);
    }

    @Test
    public void timeRangeUsesStartInclusiveEndExclusiveMembership() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series), Trade.buyAt(4, series),
                Trade.sellAt(6, series));
        var criterion = new NumberOfPositionsCriterion();
        var startInclusive = series.getBar(4).getEndTime();
        var endExclusive = series.getBar(7).getEndTime();

        var positions = criterion.calculate(series, record, AnalysisWindow.timeRange(startInclusive, endExclusive));
        assertNumEquals(1, positions);
    }

    @Test
    public void lookbackDurationUsesSeriesEndAnchorByDefault() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(8, series), Trade.sellAt(9, series));
        var criterion = new NumberOfPositionsCriterion();

        var positions = criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(7)));
        assertNumEquals(2, positions);
    }

    @Test
    public void fullyContainedPolicyExcludesBoundaryCrossingPosition() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(2, series), Trade.sellAt(5, series), Trade.buyAt(6, series),
                Trade.sellAt(8, series));
        var criterion = new NetProfitLossCriterion();
        var context = AnalysisContext.defaults().withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED);

        var pnl = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8), context);
        assertNumEquals(2, pnl);
    }

    @Test
    public void markToMarketPolicyIncludesOpenPositionAtWindowEnd() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(6, series));
        var criterion = new NetProfitLossCriterion();

        var excluded = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8));
        assertNumEquals(0, excluded);

        var context = AnalysisContext.defaults()
                .withOpenPositionPolicy(OpenPositionPolicy.MARK_TO_MARKET_AT_WINDOW_END);
        var included = criterion.calculate(series, record, AnalysisWindow.barRange(4, 8), context);
        assertNumEquals(2, included);
    }

    @Test
    public void clampModeWithNoIntersectionReturnsEmptyProjectedResult() {
        var series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        var record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        var criterion = new NumberOfPositionsCriterion();
        var context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

        var result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(0, result);
    }

    @Test
    public void clampModeWithNoIntersectionReturnsReturnNeutralValue() {
        var series = buildSeries(10);
        series.setMaximumBarCount(5); // available logical indices: 5..9
        var record = new BaseTradingRecord(Trade.buyAt(5, series), Trade.sellAt(6, series));
        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);

        var result = criterion.calculate(series, record, AnalysisWindow.barRange(20, 25), context);
        assertNumEquals(1, result);
    }

    @Test
    public void projectedTradingRecordIsReadOnly() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series));
        var criterion = new NumberOfPositionsCriterion();
        var context = AnalysisContext.defaults().withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP);
        var resolved = AnalysisWindowing.resolve(series, AnalysisWindow.barRange(0, 3), context);
        var projected = AnalysisWindowing.projectTradingRecord(series, record, resolved, context);

        assertNumEquals(1, criterion.calculate(series, projected));
        assertThrows(UnsupportedOperationException.class, () -> projected.enter(0));
    }

    @Test
    public void wholeWindowMatchesLegacyPnlCalculation() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series), Trade.buyAt(4, series),
                Trade.sellAt(8, series));
        var criterion = new NetProfitLossCriterion();
        var fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        var legacy = criterion.calculate(series, record);
        var windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyReturnCalculation() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series), Trade.buyAt(4, series),
                Trade.sellAt(8, series));
        var criterion = new NetReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
        var fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        var legacy = criterion.calculate(series, record);
        var windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyDrawdownCalculation() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series), Trade.buyAt(4, series),
                Trade.sellAt(8, series));
        var criterion = new MaximumDrawdownCriterion();
        var fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        var legacy = criterion.calculate(series, record);
        var windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    @Test
    public void wholeWindowMatchesLegacyHelperCalculation() {
        var series = buildSeries(10);
        var record = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(3, series), Trade.buyAt(4, series),
                Trade.sellAt(8, series));
        var criterion = new AverageCriterion(new NetProfitLossCriterion());
        var fullWindow = AnalysisWindow.barRange(series.getBeginIndex(), series.getEndIndex());

        var legacy = criterion.calculate(series, record);
        var windowed = criterion.calculate(series, record, fullWindow);
        assertNumEquals(legacy, windowed);
    }

    private static BarSeries buildSeries(int barCount) {
        var series = new BaseBarSeriesBuilder().withName("windowed-series").build();
        var base = Instant.parse("2026-02-01T00:00:00Z");
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
