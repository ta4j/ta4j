/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.ObjectiveEvaluation;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

public class RelationshipObjectiveSearchExampleTest {

    @Test
    public void test() {
        RelationshipObjectiveSearchExample.main(null);
    }

    @Test
    public void gridResearchEvaluatesFullSpaceAndValidatesOnHoldout() {
        BarSeries series = buildMomentumSeries(200);

        ParameterResearchReport report = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.grid(32), 40);

        assertEquals(29, report.counts().proposed());
        assertEquals(29, report.counts().attempted());
        assertEquals(29, report.counts().successful());
        assertEquals(TerminationReason.SEARCH_SPACE_EXHAUSTED, report.terminationReason());
        assertTrue(report.failedEvaluations().isEmpty());
        assertEquals(3, report.trainingLeaderboard().size());
        assertEquals(3, report.holdoutLeaderboard().size());
        assertEquals(0, report.trainingWindow().startIndex());
        assertEquals(159, report.trainingWindow().endIndex());
        assertTrue(report.holdoutWindow().isPresent());
        assertEquals(160, report.holdoutWindow().orElseThrow().startIndex());
        assertEquals(199, report.holdoutWindow().orElseThrow().endIndex());
        for (RankedCandidate candidate : report.trainingLeaderboard()) {
            assertF1(candidate.trainingScore());
            assertTrue(candidate.trainingMetrics().containsKey("Precision"));
            assertTrue(candidate.trainingMetrics().containsKey("Recall"));
        }
        for (RankedCandidate candidate : report.holdoutLeaderboard()) {
            assertF1(candidate.holdoutScore());
        }
    }

    @Test
    public void eventIndicatorsHonorNonZeroSeriesBegin() {
        // Training windows inherit the source series' begin index; the fixed
        // event indicators used to read their arrays at absolute indices, so
        // every shifted window failed with IndexOutOfBoundsException.
        BarSeries full = buildMomentumSeries(200);
        BarSeries shifted = full.getSubSeries(40, full.getEndIndex());

        ParameterResearchReport report = RelationshipObjectiveSearchExample.runRelationshipResearch(shifted,
                SearchPlan.grid(32), 40);

        assertEquals(29, report.counts().proposed());
        assertEquals(29, report.counts().attempted());
        assertEquals(29, report.counts().successful());
        assertTrue(report.failedEvaluations().isEmpty());
        assertFalse(report.trainingLeaderboard().isEmpty());
    }

    @Test
    public void eventLoopsTerminateAtTerminalSeriesIndex() {
        // A series ending at Integer.MAX_VALUE must not wrap the loop index
        // negative; both helpers iterate by a bounded local offset.
        BarSeries series = new BaseBarSeriesBuilder()
                .withBars(List.of(terminalBar("2024-01-01T00:00:00Z"), terminalBar("2024-01-02T00:00:00Z"),
                        terminalBar("2024-01-03T00:00:00Z")))
                .withBeginIndex(Integer.MAX_VALUE - 2)
                .build();

        Indicator<Boolean> momentum = RelationshipObjectiveSearchExample.momentumCrossUpEvents(series, 1);
        assertFalse(momentum.getValue(Integer.MAX_VALUE - 1));
        assertFalse(momentum.getValue(Integer.MAX_VALUE));

        Indicator<Boolean> rally = RelationshipObjectiveSearchExample.rallyAheadEvents(series);
        assertFalse(rally.getValue(Integer.MAX_VALUE));
    }

    @Test
    public void seededEnginesAreDeterministicAcrossRuns() {
        BarSeries series = buildMomentumSeries(200);

        ParameterResearchReport geneticA = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.genetic(29, 42L), 40);
        ParameterResearchReport geneticB = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.genetic(29, 42L), 40);
        assertFalse(geneticA.trainingLeaderboard().isEmpty());
        assertEquals(leaderboardKey(geneticA), leaderboardKey(geneticB));

        ParameterResearchReport swarmA = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.particleSwarm(29, 42L), 40);
        ParameterResearchReport swarmB = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.particleSwarm(29, 42L), 40);
        assertFalse(swarmA.trainingLeaderboard().isEmpty());
        assertEquals(leaderboardKey(swarmA), leaderboardKey(swarmB));
    }

    @Test
    public void narrativeExplainsSpaceHoldoutAndTakeaway() {
        BarSeries series = buildMomentumSeries(200);
        ParameterResearchReport report = RelationshipObjectiveSearchExample.runRelationshipResearch(series,
                SearchPlan.grid(32), 40);

        String narrative = RelationshipObjectiveSearchExample.formatResearchNarrative(report, 2);

        assertTrue(narrative.contains("Momentum/rally synchronization parameter research"));
        assertTrue(narrative.contains("Candidate space:"));
        assertTrue(narrative.contains("Training top candidates:"));
        assertTrue(narrative.contains("Validation top candidates:"));
        assertTrue(narrative.contains("Takeaway:"));
        assertTrue(narrative.contains("F1="));
        assertTrue(narrative.contains("Precision="));
        assertTrue(narrative.contains("Recall="));
    }

    @Test
    public void terminalPredictionsAreExcludedFromSynchronizationF1() {
        BarSeries series = buildMomentumSeries(200);
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        BarSeries windowSeries = series.getSubSeries(begin, end + 1);
        int windowBegin = windowSeries.getBeginIndex();
        int windowEnd = windowSeries.getEndIndex();
        Indicator<Boolean> reference = RelationshipObjectiveSearchExample.rallyAheadEvents(windowSeries);

        // One rally inside the synchronization window bounds both evaluation
        // variants; the contaminated stream additionally predicts the terminal
        // bars, which cannot be labeled.
        int rallyIndex = -1;
        int searchFloor = Math.max(windowBegin, windowEnd - RelationshipObjectiveSearchExample.SYNC_WINDOW_BARS + 1);
        for (int i = windowEnd - RelationshipObjectiveSearchExample.FORECAST_BARS; i >= searchFloor; i--) {
            if (reference.getValue(i)) {
                rallyIndex = i;
                break;
            }
        }
        assertTrue("series has no rally inside the synchronization window", rallyIndex >= 0);

        Boolean[] clean = new Boolean[windowSeries.getBarCount()];
        Boolean[] contaminated = new Boolean[windowSeries.getBarCount()];
        for (int i = 0; i < clean.length; i++) {
            int index = windowBegin + i;
            boolean rally = index == rallyIndex;
            boolean terminal = index > windowEnd - RelationshipObjectiveSearchExample.FORECAST_BARS;
            clean[i] = rally;
            contaminated[i] = rally || terminal;
        }

        ObjectiveEvaluation cleanScore = RelationshipObjectiveSearchExample.scoreSynchronization(
                new FixedBooleanIndicator(windowSeries, clean),
                new ResearchWindow(windowSeries, windowBegin, windowEnd, ResearchWindow.WindowPhase.TRAINING, "clean"));
        ObjectiveEvaluation contaminatedScore = RelationshipObjectiveSearchExample.scoreSynchronization(
                new FixedBooleanIndicator(windowSeries, contaminated), new ResearchWindow(windowSeries, windowBegin,
                        windowEnd, ResearchWindow.WindowPhase.TRAINING, "contaminated"));

        assertEquals(ObjectiveEvaluation.Status.VALID, cleanScore.status());
        assertEquals(cleanScore.score().doubleValue(), contaminatedScore.score().doubleValue(), 1e-12);
        assertEquals(ObjectiveEvaluation.Status.VALID, contaminatedScore.status());
    }

    @Test
    public void oneSidedEventStreamPublishesOnlyFiniteMetrics() {
        BarSeries series = buildMomentumSeries(200);
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        BarSeries windowSeries = series.getSubSeries(begin, end + 1);
        int windowBegin = windowSeries.getBeginIndex();
        int windowEnd = windowSeries.getEndIndex();
        Indicator<Boolean> reference = RelationshipObjectiveSearchExample.rallyAheadEvents(windowSeries);

        // At least one labeled rally exists, so a silent predicted stream
        // scores F1 = 0 with precision undefined (0/0) and recall 0.
        int rallyIndex = -1;
        for (int i = windowBegin; i <= windowEnd - RelationshipObjectiveSearchExample.FORECAST_BARS; i++) {
            if (reference.getValue(i)) {
                rallyIndex = i;
                break;
            }
        }
        assertTrue("series has no rally in the evaluable window", rallyIndex >= 0);

        Boolean[] silent = new Boolean[windowSeries.getBarCount()];
        ObjectiveEvaluation evaluation = RelationshipObjectiveSearchExample
                .scoreSynchronization(new FixedBooleanIndicator(windowSeries, silent), new ResearchWindow(windowSeries,
                        windowBegin, windowEnd, ResearchWindow.WindowPhase.TRAINING, "silent"));

        assertEquals(ObjectiveEvaluation.Status.VALID, evaluation.status());
        assertEquals(0.0, evaluation.score().doubleValue(), 1e-12);
        assertFalse("zero-F1 candidate still carries finite diagnostics", evaluation.metrics().isEmpty());
        for (Num metric : evaluation.metrics().values()) {
            assertTrue(Num.isFinite(metric));
        }
        // Precision is undefined and must not be published; recall is 0 and
        // finite, so the candidate stays rankable without NaN diagnostics.
        assertFalse(evaluation.metrics().containsKey("Precision"));
        assertTrue(evaluation.metrics().containsKey("Recall"));
    }

    @Test
    public void scoreSynchronizationFailsForShortWindows() {
        BarSeries series = buildMomentumSeries(200).getSubSeries(0, 3);
        Indicator<Boolean> predicted = RelationshipObjectiveSearchExample.momentumCrossUpEvents(series, 1);
        ObjectiveEvaluation evaluation = RelationshipObjectiveSearchExample.scoreSynchronization(predicted,
                new ResearchWindow(series, 0, 2, ResearchWindow.WindowPhase.TRAINING, "short"));
        assertEquals(ObjectiveEvaluation.Status.FAILED, evaluation.status());
    }

    private static void assertF1(Num score) {
        assertTrue(Num.isFinite(score));
        assertTrue(score.doubleValue() >= 0.0);
        assertTrue(score.doubleValue() <= 1.0);
    }

    private static String leaderboardKey(ParameterResearchReport report) {
        StringBuilder key = new StringBuilder();
        for (RankedCandidate candidate : report.trainingLeaderboard()) {
            key.append(candidate.candidateId()).append('@').append(candidate.trainingScore().doubleValue()).append(';');
        }
        return key.toString();
    }

    private static BarSeries buildMomentumSeries(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.12) + (Math.sin(i / 3.0) * 8.0) + (Math.cos(i / 7.0) * 3.0);
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }

    private static Bar terminalBar(String endTime) {
        return new TimeBarBuilder(DoubleNumFactory.getInstance()).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse(endTime))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
    }
}
