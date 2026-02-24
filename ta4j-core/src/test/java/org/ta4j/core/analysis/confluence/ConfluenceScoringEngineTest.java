/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.confluence;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ConfluenceScoringEngineTest {

    @Test
    public void appliesFamilyCapsAndCorrelationPenalties() {
        List<ConfluenceReport.PillarScore> pillars = List.of(
                pillar(ConfluenceReport.Pillar.STRUCTURE, "structure", 80.0d, 0.60d),
                pillar(ConfluenceReport.Pillar.TREND, "trend", 60.0d, 0.40d));

        ConfluenceScoringEngine scoringEngine = new ConfluenceScoringEngine(
                Map.of("structure", new ConfluenceScoringEngine.FamilyPolicy(0.30d, 0.20d), "trend",
                        new ConfluenceScoringEngine.FamilyPolicy(1.00d, 0.00d)));

        ConfluenceScoringEngine.ConfluenceScores scores = scoringEngine.score(pillars);

        assertEquals(72.0d, scores.rawScore(), 1.0e-9d);
        assertEquals(67.5d, scores.decorrelatedScore(), 1.0e-9d);
        assertEquals(4.5d, scores.correlationPenalty(), 1.0e-9d);
        assertEquals(0.375d, scores.effectiveFamilyWeights().get("structure"), 1.0e-9d);
        assertEquals(0.625d, scores.effectiveFamilyWeights().get("trend"), 1.0e-9d);
    }

    @Test
    public void fallsBackToEqualWeightsWhenAllWeightsAreZero() {
        List<ConfluenceReport.PillarScore> pillars = List.of(
                pillar(ConfluenceReport.Pillar.STRUCTURE, "structure", 80.0d, 0.0d),
                pillar(ConfluenceReport.Pillar.TREND, "trend", 40.0d, 0.0d),
                pillar(ConfluenceReport.Pillar.MOMENTUM, "momentum", 20.0d, 0.0d));

        ConfluenceScoringEngine scoringEngine = new ConfluenceScoringEngine();
        ConfluenceScoringEngine.ConfluenceScores scores = scoringEngine.score(pillars);

        assertEquals(46.666666666666664d, scores.rawScore(), 1.0e-12d);
        assertEquals(scores.rawScore(), scores.decorrelatedScore(), 1.0e-12d);
        assertEquals(0.0d, scores.correlationPenalty(), 1.0e-12d);
    }

    @Test
    public void rejectsEmptyInput() {
        ConfluenceScoringEngine scoringEngine = new ConfluenceScoringEngine();
        assertThrows(IllegalArgumentException.class, () -> scoringEngine.score(List.of()));
    }

    private static ConfluenceReport.PillarScore pillar(ConfluenceReport.Pillar pillar, String family, double score,
            double weight) {
        return new ConfluenceReport.PillarScore(pillar, family, score, weight, ConfluenceReport.Direction.NEUTRAL,
                List.of(new ConfluenceReport.FeatureContribution("feature", score, 1.0d,
                        ConfluenceReport.Direction.NEUTRAL, "feature rationale")),
                List.of("explanation"));
    }
}
