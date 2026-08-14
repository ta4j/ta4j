/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
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
                SearchPlan.grid(29), 40);

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
                SearchPlan.grid(29), 40);

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
}
