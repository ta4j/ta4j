/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.ParameterValue;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.RunCounts;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

public class SimpleMovingAverageRangeBacktestTest {

    @Test
    public void test() {
        SimpleMovingAverageRangeBacktest.main(null);
    }

    @Test
    public void smaResearchUsesMultiParameterCandidatesAndHoldoutValidation() {
        BarSeries series = buildSwingSeries(80);

        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series, 20);

        assertEquals(60, report.counts().proposed());
        assertEquals(18, report.counts().rejected());
        assertEquals(42, report.counts().attempted());
        assertEquals(42, report.counts().successful());
        assertEquals(TerminationReason.SEARCH_SPACE_EXHAUSTED, report.terminationReason());
        assertFalse(report.trainingLeaderboard().isEmpty());
        assertTrue(report.trainingLeaderboard().size() <= 3);
        assertFalse(report.holdoutLeaderboard().isEmpty());
        assertEquals(0, report.trainingWindow().startIndex());
        assertEquals(59, report.trainingWindow().endIndex());
        assertTrue(report.holdoutWindow().isPresent());
        assertEquals(60, report.holdoutWindow().orElseThrow().startIndex());
        assertEquals(79, report.holdoutWindow().orElseThrow().endIndex());
    }

    @Test
    public void smaResearchNarrativeExplainsCandidateSpaceHoldoutAndTakeaway() {
        // The narrative formatter reads only counts and leaderboards, so build a
        // report fixture instead of re-running the 60-proposal sweep this test
        // is not about.
        ParameterResearchReport report = narrativeReportFixture();

        String narrative = SimpleMovingAverageRangeBacktest.formatResearchNarrative(report, 2);

        assertTrue(narrative.contains("SMA trend parameter research"));
        assertTrue(narrative.contains("Candidate space: 60 proposals, 18 rejected, 42 evaluated"));
        assertTrue(narrative.contains("Training top candidates:"));
        assertTrue(narrative.contains("Validation top candidates:"));
        assertTrue(narrative.contains("Takeaway:"));
        assertTrue(narrative.contains("Net Profit="));
        assertTrue(narrative.contains("Return Over Max Drawdown="));
    }

    private static ParameterResearchReport narrativeReportFixture() {
        BarSeries series = buildSwingSeries(80);
        Num trainingScore = series.numFactory().numOf(2);
        Num holdoutScore = series.numFactory().numOf(1);
        ParameterSet parameters = new ParameterSet(List.of(new ParameterValue("fastBarCount", "5", false, ""),
                new ParameterValue("slowBarCount", "10", false, ""),
                new ParameterValue("stopLossPercentage", "3", false, "")));
        RankedCandidate candidate = new RankedCandidate(parameters.stableId(), parameters, 1, 1, trainingScore,
                holdoutScore, holdoutScore.minus(trainingScore),
                Map.of("Net Profit", series.numFactory().numOf(120), "Return Over Max Drawdown",
                        series.numFactory().numOf(4)),
                Map.of("Net Profit", series.numFactory().numOf(80), "Return Over Max Drawdown",
                        series.numFactory().numOf(3)));
        return new ParameterResearchReport("sma-narrative-fixture", SearchPlan.grid(200), "sma-objective-fixture",
                new ResearchWindow(buildSwingSeries(60), 0, 59, ResearchWindow.WindowPhase.TRAINING, "training"),
                Optional.of(new ResearchWindow(buildSwingSeries(20), 60, 79, ResearchWindow.WindowPhase.HOLDOUT,
                        "holdout")),
                2, List.of(candidate), List.of(candidate), TerminationReason.SEARCH_SPACE_EXHAUSTED,
                new RunCounts(60, 18, 0, 0, 0, 42, 0, 42, 0, 0, 0), List.of(), 0L, 0L, List.of());
    }

    @Test
    public void smaResearchRankingIsInvariantToPriceScale() {
        ParameterResearchReport original = SimpleMovingAverageRangeBacktest.runSmaResearch(buildSwingSeries(80), 20);
        ParameterResearchReport scaled = SimpleMovingAverageRangeBacktest.runSmaResearch(buildSwingSeries(80, 0.01),
                20);

        List<RankedCandidate> originalLeaderboard = original.trainingLeaderboard();
        List<RankedCandidate> scaledLeaderboard = scaled.trainingLeaderboard();
        assertFalse(originalLeaderboard.isEmpty());
        assertFalse(scaledLeaderboard.isEmpty());
        assertEquals(originalLeaderboard.size(), scaledLeaderboard.size());
        for (int i = 0; i < originalLeaderboard.size(); i++) {
            assertEquals(originalLeaderboard.get(i).parameters(), scaledLeaderboard.get(i).parameters());
        }
    }

    private static BarSeries buildSwingSeries(int size) {
        return buildSwingSeries(size, 1.0);
    }

    private static BarSeries buildSwingSeries(int size, double scale) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = scale * (100 + (i * 0.15) + (Math.sin(i / 4.0) * 6.0) + (Math.cos(i / 11.0) * 2.0));
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }
}
