/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottLogicProfile;

class ElliottWaveMacroCycleTruthTargetScoringTest {

    @Test
    void truthTargetCoverageScorePenalizesMissingAndUnexpectedCycles() {
        ElliottWaveMacroCycleDemo.TruthTargetCoverage complete = new ElliottWaveMacroCycleDemo.TruthTargetCoverage(3, 3,
                0, 0, List.of(), List.of());
        ElliottWaveMacroCycleDemo.TruthTargetCoverage missingCycle = new ElliottWaveMacroCycleDemo.TruthTargetCoverage(
                3, 2, 1, 0, List.of("cycle-3"), List.of());
        ElliottWaveMacroCycleDemo.TruthTargetCoverage unexpectedCycle = new ElliottWaveMacroCycleDemo.TruthTargetCoverage(
                3, 3, 0, 1, List.of(), List.of("cycle-extra"));

        assertEquals(1.0, complete.score());
        assertTrue(missingCycle.score() < complete.score());
        assertTrue(unexpectedCycle.score() < complete.score());
        assertTrue(missingCycle.score() < unexpectedCycle.score());
    }

    @Test
    void profileComparatorPrefersCompleteTruthTargetCoverageOverHigherAggregateScore() throws Exception {
        Comparator<ElliottWaveMacroCycleDemo.MacroProfileEvaluation> comparator = invokeProfileComparator();
        ElliottWaveMacroCycleDemo.MacroProfileEvaluation completeCoverage = evaluation("complete-coverage", 0, 0.62, 3,
                6, true, new ElliottWaveMacroCycleDemo.TruthTargetCoverage(3, 3, 0, 0, List.of(), List.of()));
        ElliottWaveMacroCycleDemo.MacroProfileEvaluation unexpectedCoverage = evaluation("unexpected-coverage", 1, 0.98,
                3, 8, false,
                new ElliottWaveMacroCycleDemo.TruthTargetCoverage(3, 3, 0, 1, List.of(), List.of("cycle-extra")));
        ElliottWaveMacroCycleDemo.MacroProfileEvaluation missingCoverage = evaluation("missing-coverage", 2, 0.99, 4, 8,
                false, new ElliottWaveMacroCycleDemo.TruthTargetCoverage(3, 2, 1, 0, List.of("cycle-3"), List.of()));

        List<String> orderedProfileIds = List.of(missingCoverage, unexpectedCoverage, completeCoverage)
                .stream()
                .sorted(comparator)
                .map(evaluation -> evaluation.profile().id())
                .toList();

        assertEquals(List.of("complete-coverage", "unexpected-coverage", "missing-coverage"), orderedProfileIds);
    }

    private static ElliottWaveMacroCycleDemo.MacroProfileEvaluation evaluation(final String profileId,
            final int orthodoxyRank, final double aggregateScore, final int acceptedCycles, final int acceptedSegments,
            final boolean historicalFitPassed,
            final ElliottWaveMacroCycleDemo.TruthTargetCoverage truthTargetCoverage) {
        ElliottWaveMacroCycleDemo.MacroLogicProfile profile = new ElliottWaveMacroCycleDemo.MacroLogicProfile(profileId,
                "HX", "Test profile", orthodoxyRank, ElliottLogicProfile.ORTHODOX_CLASSICAL, ElliottDegree.MINUTE, null,
                null);
        return new ElliottWaveMacroCycleDemo.MacroProfileEvaluation(profile, aggregateScore, acceptedCycles,
                acceptedSegments, historicalFitPassed, truthTargetCoverage, List.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private static Comparator<ElliottWaveMacroCycleDemo.MacroProfileEvaluation> invokeProfileComparator()
            throws Exception {
        Method method = ElliottWaveMacroCycleDemo.class.getDeclaredMethod("profileEvaluationComparator");
        method.setAccessible(true);
        return (Comparator<ElliottWaveMacroCycleDemo.MacroProfileEvaluation>) method.invoke(null);
    }
}
