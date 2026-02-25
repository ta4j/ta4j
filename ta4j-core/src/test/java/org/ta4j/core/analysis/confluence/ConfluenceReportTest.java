/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.confluence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfluenceReportTest {

    @Test
    public void topLevelHelpersReturnSortedResults() {
        ConfluenceReport report = minimalReport(List.of(
                new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.SUPPORT, "support-A", 5000.0d, 55.0d,
                        -2.1d, 0.50d, 0.50d, 0.50d, 0.50d, 0.50d, "support A"),
                new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.SUPPORT, "support-B", 5100.0d, 82.0d,
                        -0.8d, 0.70d, 0.80d, 0.60d, 0.70d, 0.65d, "support B"),
                new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.RESISTANCE, "resistance-A", 5300.0d,
                        77.0d, 3.3d, 0.70d, 0.70d, 0.60d, 0.70d, 0.60d, "resistance A")));

        List<ConfluenceReport.LevelConfidence> supports = report.topSupports(2);
        assertEquals(2, supports.size());
        assertEquals("support-B", supports.get(0).name());
        assertEquals("support-A", supports.get(1).name());

        List<ConfluenceReport.LevelConfidence> resistances = report.topResistances(5);
        assertEquals(1, resistances.size());
        assertEquals("resistance-A", resistances.get(0).name());
    }

    @Test
    public void constructorCreatesDefensiveCopies() {
        List<ConfluenceReport.PillarScore> pillars = new ArrayList<>();
        pillars.add(defaultPillar());
        List<ConfluenceReport.LevelConfidence> levels = new ArrayList<>();
        levels.add(new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.SUPPORT, "support", 5000.0d, 50.0d,
                -1.0d, 0.5d, 0.5d, 0.5d, 0.5d, 0.5d, "support"));
        List<ConfluenceReport.HorizonProbability> probabilities = new ArrayList<>();
        probabilities.add(defaultProbability());
        List<String> outlook = new ArrayList<>();
        outlook.add("neutral");
        Map<String, String> extensions = new HashMap<>();
        extensions.put("k", "v");

        ConfluenceReport report = new ConfluenceReport(defaultSnapshot(), pillars, levels, probabilities,
                defaultConfidence(), defaultValidation(), outlook, extensions);

        pillars.clear();
        levels.clear();
        probabilities.clear();
        outlook.clear();
        extensions.clear();

        assertEquals(1, report.pillarScores().size());
        assertEquals(1, report.levelConfidences().size());
        assertEquals(1, report.horizonProbabilities().size());
        assertEquals(1, report.outlookNarrative().size());
        assertEquals(1, report.extensions().size());

        assertThrows(UnsupportedOperationException.class, () -> report.pillarScores().add(defaultPillar()));
        assertThrows(UnsupportedOperationException.class, () -> report.extensions().put("new", "entry"));
    }

    @Test
    public void horizonProbabilityRequiresSumToOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ConfluenceReport.HorizonProbability(ConfluenceReport.Horizon.ONE_MONTH, 0.60d, 0.30d, 0.05d,
                        true, "isotonic"));
        assertTrue(exception.getMessage().contains("sum to 1.0"));
    }

    @Test
    public void topLevelsRejectsNullTypeAndHandlesNonPositiveLimit() {
        ConfluenceReport report = minimalReport(
                List.of(new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.SUPPORT, "support-A", 5000.0d,
                        55.0d, -2.1d, 0.50d, 0.50d, 0.50d, 0.50d, 0.50d, "support A")));

        assertThrows(NullPointerException.class, () -> report.topLevels(null, 1));
        assertEquals(List.of(), report.topSupports(0));
        assertEquals(List.of(), report.topResistances(-2));
    }

    @Test
    public void pillarScoreNormalizesFamilyAndCreatesNestedDefensiveCopies() {
        List<ConfluenceReport.FeatureContribution> features = new ArrayList<>();
        features.add(new ConfluenceReport.FeatureContribution("feature", 51.0d, 0.4d,
                ConfluenceReport.Direction.NEUTRAL, "rationale"));
        List<String> explanations = new ArrayList<>();
        explanations.add("explanation");

        ConfluenceReport.PillarScore fallbackFamily = new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.TREND,
                "   ", 56.0d, 0.5d, ConfluenceReport.Direction.BULLISH, features, explanations);
        ConfluenceReport.PillarScore trimmedFamily = new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.MOMENTUM,
                " momentum ", 54.0d, 0.4d, ConfluenceReport.Direction.NEUTRAL, features, explanations);

        features.clear();
        explanations.clear();

        assertEquals("TREND", fallbackFamily.family());
        assertEquals("momentum", trimmedFamily.family());
        assertEquals(1, fallbackFamily.featureContributions().size());
        assertEquals(1, fallbackFamily.explanations().size());
        assertThrows(UnsupportedOperationException.class,
                () -> fallbackFamily.featureContributions().add(fallbackFamily.featureContributions().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> fallbackFamily.explanations().add("new"));
    }

    @Test
    public void nestedRecordsValidateCriticalConstraints() {
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.FeatureContribution("", 60.0d, 0.5d,
                ConfluenceReport.Direction.NEUTRAL, "rationale"));
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.FeatureContribution("feature", 60.0d,
                1.5d, ConfluenceReport.Direction.NEUTRAL, "rationale"));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.TREND, "trend", 50.0d, -0.1d,
                        ConfluenceReport.Direction.NEUTRAL, List.of(), List.of("x")));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfluenceReport.LevelConfidence(ConfluenceReport.LevelType.SUPPORT, "support", 5000.0d,
                        40.0d, 1.0d, 1.1d, 0.3d, 0.3d, 0.3d, 0.3d, "bad structural"));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfluenceReport.HorizonProbability(ConfluenceReport.Horizon.ONE_MONTH, 0.40d, 0.30d, 0.30d,
                        false, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfluenceReport.ConfidenceBreakdown(50.0d, 40.0d, 30.0d, 20.0d, 120.0d, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.ValidationMetadata("isotonic",
                "2014-2025", "2026-02-24", -0.1d, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.ValidationMetadata("isotonic",
                "2014-2025", "2026-02-24", null, null, null, "   ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.ValidationMetadata("isotonic",
                "2014-2025", "not-a-date", null, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfluenceReport.Snapshot("^GSPC", "PT1D",
                Instant.parse("2026-02-24T19:39:46Z"), 0, 5000.0d, 60.0d, 58.0d, 2.0d));
    }

    @Test
    public void horizonProbabilityAcceptsEpsilonSafeRounding() {
        ConfluenceReport.HorizonProbability probability = new ConfluenceReport.HorizonProbability(
                ConfluenceReport.Horizon.THREE_MONTH, 0.3333333d, 0.3333333d, 0.3333334d, true, "isotonic");
        assertEquals(ConfluenceReport.Horizon.THREE_MONTH, probability.horizon());
    }

    private static ConfluenceReport minimalReport(List<ConfluenceReport.LevelConfidence> levels) {
        return new ConfluenceReport(defaultSnapshot(), List.of(defaultPillar()), levels, List.of(defaultProbability()),
                defaultConfidence(), defaultValidation(), List.of("baseline"), Map.of("profile", "default"));
    }

    private static ConfluenceReport.PillarScore defaultPillar() {
        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.TREND, "trend", 60.0d, 1.0d,
                ConfluenceReport.Direction.NEUTRAL, List.of(new ConfluenceReport.FeatureContribution("sma-stack", 60.0d,
                        0.5d, ConfluenceReport.Direction.NEUTRAL, "neutral stack")),
                List.of("trend neutral"));
    }

    private static ConfluenceReport.HorizonProbability defaultProbability() {
        return new ConfluenceReport.HorizonProbability(ConfluenceReport.Horizon.ONE_MONTH, 0.40d, 0.30d, 0.30d, true,
                "isotonic");
    }

    private static ConfluenceReport.ConfidenceBreakdown defaultConfidence() {
        return new ConfluenceReport.ConfidenceBreakdown(60.0d, 55.0d, 50.0d, 90.0d, 62.0d, List.of("balanced"));
    }

    private static ConfluenceReport.ValidationMetadata defaultValidation() {
        return new ConfluenceReport.ValidationMetadata("isotonic", "2014-2025", "2026-02-24", 0.19d, 0.03d, 0.58d,
                "temp/charts/reliability.png", List.of());
    }

    private static ConfluenceReport.Snapshot defaultSnapshot() {
        return new ConfluenceReport.Snapshot("^GSPC", "PT1D", Instant.parse("2026-02-24T19:39:46Z"), 500, 6886.55d,
                64.0d, 61.5d, 2.5d);
    }
}
