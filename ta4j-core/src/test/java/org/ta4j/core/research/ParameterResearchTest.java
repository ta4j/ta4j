/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.CandidateValidator;
import org.ta4j.core.research.ParameterResearch.Direction;
import org.ta4j.core.research.ParameterResearch.EvaluatedCandidate;
import org.ta4j.core.research.ParameterResearch.GeneticSettings;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterNormalizer;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.ParameterValue;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.research.ParameterResearch.SwarmSettings;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

class ParameterResearchTest {

    private static BarSeries series(double... closes) {
        return new MockBarSeriesBuilder().withData(closes).build();
    }

    private static ParameterResearch.Builder<Integer> sumGridBuilder(BarSeries series, int budget) {
        return ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .integer("b", 3, 4)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(budget));
    }

    private static ParameterResearch.Builder<Integer> holdoutConfigBuilder(BarSeries series) {
        return ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(series.numFactory().numOf(candidate)))
                .search(SearchPlan.grid(2));
    }

    @Test
    void builderRequiresDomainsCandidateObjectiveAndPlan() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> noDomains = ParameterResearch.<Integer>builder(series)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(series.numFactory().numOf(candidate)))
                .search(SearchPlan.grid(4));
        assertThrows(IllegalStateException.class, noDomains::run);

        ParameterResearch.Builder<Integer> noCandidate = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(series.numFactory().numOf(candidate)))
                .search(SearchPlan.grid(4));
        assertThrows(IllegalStateException.class, noCandidate::run);

        ParameterResearch.Builder<Integer> noObjective = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> 1)
                .search(SearchPlan.grid(4));
        assertThrows(IllegalStateException.class, noObjective::run);

        ParameterResearch.Builder<Integer> noPlan = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(series.numFactory().numOf(candidate)));
        assertThrows(IllegalStateException.class, noPlan::run);
    }

    @Test
    void builderRejectsEmptySeries() {
        BarSeries empty = series();
        assertThrows(IllegalStateException.class, () -> sumGridBuilder(empty, 4).run());
    }

    @Test
    void builderRejectsDuplicateDomainNames() {
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series(1d, 2d))
                .integer("a", 1, 2);
        assertThrows(IllegalArgumentException.class, () -> builder.integer("a", 3, 4));
    }

    @Test
    void domainsRejectInvalidDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.integer("", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.integer("a", 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.integer("a", 3, 2));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.decimal("a", 1d, 2d, 0d));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.decimal("a", Double.NaN, 2d, 1d));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.categorical("a"));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.categorical("a", "x", " "));
        assertThrows(IllegalArgumentException.class, () -> ParameterDomain.categorical("a", "x", "x", "y"));
    }

    @Test
    void searchPlanRejectsNonPositiveBudgets() {
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.grid(0));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.genetic(-1, 42L));
        assertThrows(IllegalArgumentException.class, () -> SearchPlan.particleSwarm(0, 42L));
    }

    @Test
    void gridIteratesDeclarationOrderAndExhausts() {
        BarSeries series = series(1d, 2d, 3d);
        List<int[]> evaluated = new ArrayList<>();
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .bool("flag")
                .candidate(
                        (window, parameters) -> parameters.intValue("a") + (parameters.booleanValue("flag") ? 10 : 0))
                .maximize((candidate, window) -> {
                    evaluated.add(
                            new int[] { window.series().numFactory().numOf(candidate).intValue(), window.barCount() });
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(4))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(4);
        assertThat(report.counts().budgetRemaining()).isEqualTo(0);
        assertThat(evaluated).hasSize(4);
        assertThat(evaluated.stream().map(pair -> pair[0])).containsExactly(1, 11, 2, 12);
    }

    @Test
    void gridBudgetTruncationReportsBudgetExhaustion() {
        ParameterResearchReport report = sumGridBuilder(series(1d, 2d, 3d), 3).run();
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.EVALUATION_BUDGET_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(3);
        assertThat(report.counts().budgetRemaining()).isEqualTo(0);
        assertThat(report.trainingLeaderboard()).hasSize(3);
    }

    @Test
    void failedObjectiveEvaluationsRankBelowValidAndAreReported() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> candidate == 2 ? ParameterResearch.ObjectiveEvaluation.failed("skip")
                        : ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(3);
        assertThat(report.counts().failed()).isEqualTo(1);
        assertThat(report.counts().successful()).isEqualTo(2);
        assertThat(report.failedEvaluations()).hasSize(1);
        assertThat(report.failedEvaluations().get(0).reason()).isEqualTo("skip");
        assertThat(report.trainingLeaderboard()).hasSize(2);
        assertThat(report.trainingLeaderboard().get(0).parameters().intValue("a")).isEqualTo(3);
    }

    @Test
    void allFailedEvaluationsReportNoValidCandidates() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation.failed("always"))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        assertThat(report.trainingLeaderboard()).isEmpty();
        assertThat(report.counts().successful()).isEqualTo(0);
    }

    @Test
    void validatorRejectionsDoNotConsumeBudget() {
        BarSeries series = series(1d, 2d, 3d);
        CandidateValidator validator = parameters -> {
            if (parameters.intValue("a") == 2) {
                throw new IllegalArgumentException("value 2 is forbidden");
            }
        };
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 4)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .validate(validator)
                .search(SearchPlan.grid(4))
                .run();

        assertThat(report.counts().rejected()).isEqualTo(1);
        assertThat(report.counts().attempted()).isEqualTo(3);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void repairedCandidatesRankBelowUnrepairedWithEqualScores() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterNormalizer normalizer = (data, name, value) -> {
            if ("1".equals(value)) {
                return new ParameterValue(name, "5", true, "clamped");
            }
            return new ParameterValue(name, value, false, "");
        };
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize(normalizer)
                .search(SearchPlan.grid(5))
                .run();

        assertThat(report.counts().repaired()).isEqualTo(1);
        RankedCandidate first = report.trainingLeaderboard().get(0);
        RankedCandidate second = report.trainingLeaderboard().get(1);
        assertThat(first.parameters().intValue("a")).isEqualTo(5);
        assertThat(first.parameters().repairCount()).isEqualTo(0);
        assertThat(second.parameters().intValue("a")).isEqualTo(5);
        assertThat(second.parameters().repairCount()).isEqualTo(1);
        assertThat(second.parameters().repairs()).containsEntry("a", "clamped");
    }

    @Test
    void normalizerRenamingValueRejectsProposal() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterNormalizer normalizer = (data, name, value) -> new ParameterValue("other", value, false, "");
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize(normalizer)
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.counts().rejected()).isEqualTo(3);
        assertThat(report.counts().attempted()).isEqualTo(0);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
    }

    @Test
    void equalScoresBreakTiesByEvaluationOrder() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(1)))
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.trainingLeaderboard().get(0).parameters().intValue("a")).isEqualTo(1);
        assertThat(report.trainingLeaderboard().get(1).parameters().intValue("a")).isEqualTo(2);
        assertThat(report.trainingLeaderboard().get(2).parameters().intValue("a")).isEqualTo(3);
    }

    @Test
    void geneticSearchIsSeededAndDeterministic() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport first = runGenetic(series, 42L);
        ParameterResearchReport second = runGenetic(series, 42L);
        List<String> firstIds = first.trainingLeaderboard().stream().map(RankedCandidate::candidateId).toList();
        List<String> secondIds = second.trainingLeaderboard().stream().map(RankedCandidate::candidateId).toList();
        assertThat(secondIds).isEqualTo(firstIds);
        assertThat(
                second.trainingLeaderboard().stream().map(RankedCandidate::trainingScore).map(Num::toString).toList())
                .isEqualTo(first.trainingLeaderboard()
                        .stream()
                        .map(RankedCandidate::trainingScore)
                        .map(Num::toString)
                        .toList());
    }

    @Test
    void geneticSearchRespectsExactBudget() {
        ParameterResearchReport report = runGenetic(series(1d, 2d, 3d, 4d), 7L);
        assertThat(report.counts().attempted()).isEqualTo(10);
        assertThat(report.counts().budgetRemaining()).isEqualTo(0);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.EVALUATION_BUDGET_EXHAUSTED);
    }

    @Test
    void geneticSearchHonorsIterationLimit() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 10)
                .integer("b", 1, 10)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(200, 3L))
                .maxIterations(2)
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.ITERATION_LIMIT);
        assertThat(report.counts().iterationsCompleted()).isEqualTo(2);
        assertThat(report.counts().attempted()).isGreaterThanOrEqualTo(50).isLessThanOrEqualTo(100);
    }

    @Test
    void geneticSearchHonorsNoImprovementLimit() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 100)
                .integer("b", 1, 100)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(1000, 5L, new ParameterResearch.GeneticSettings(10, 1, 2, 0.9, 0.1)))
                .noImprovementIterations(1)
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_IMPROVEMENT);
        assertThat(report.counts().iterationsCompleted()).isGreaterThanOrEqualTo(2);
        assertThat(report.counts().attempted()).isGreaterThanOrEqualTo(10)
                .isLessThanOrEqualTo(10L * report.counts().iterationsCompleted());
    }

    @Test
    void particleSwarmRejectsNonNumericDomainsBeforeEvaluation() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .bool("flag")
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.particleSwarm(10, 1L));
        assertThrows(IllegalArgumentException.class, builder::run);
    }

    @Test
    void particleSwarmRespectsExactBudget() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .decimal("a", 0d, 1d, 0.25d)
                .decimal("b", 0d, 1d, 0.25d)
                .candidate((window, parameters) -> (int) Math.round(parameters.decimalValue("a") * 10))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.particleSwarm(12, 9L))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(12);
        assertThat(report.counts().budgetRemaining()).isEqualTo(0);
    }

    @Test
    void particleSwarmCollisionsProduceDuplicateCounts() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .integer("b", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.particleSwarm(100, 11L))
                .run();

        assertThat(report.counts().duplicate()).isGreaterThan(0);
        assertThat(report.counts().cached()).isEqualTo(report.counts().duplicate());
        assertThat(report.counts().attempted()).isLessThanOrEqualTo(100);
    }

    @Test
    void targetScoreTerminatesGridSearch() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(5))
                .targetScore(series.numFactory().numOf(2))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.TARGET_SCORE_REACHED);
        assertThat(report.counts().attempted()).isEqualTo(2);
    }

    @Test
    void targetScoreTerminatesGeneticSearch() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(100, 11L))
                .targetScore(series.numFactory().numOf(3))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.TARGET_SCORE_REACHED);
        assertThat(report.counts().attempted()).isLessThanOrEqualTo(5);
    }

    @Test
    void targetScoreTerminatesParticleSwarmSearch() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.particleSwarm(100, 11L))
                .targetScore(series.numFactory().numOf(3))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.TARGET_SCORE_REACHED);
        assertThat(report.counts().attempted()).isLessThanOrEqualTo(50);
    }

    @Test
    void holdoutSplitsWindowsAndRebuildsIndependently() {
        BarSeries series = series(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .integer("b", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> window.phase() == ResearchWindow.WindowPhase.TRAINING
                        ? ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(1))
                        : ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(10))
                .holdoutBarCount(5)
                .topK(3)
                .run();

        assertThat(report.trainingWindow().barCount()).isEqualTo(15);
        assertThat(report.holdoutWindow()).isPresent();
        assertThat(report.holdoutWindow().get().barCount()).isEqualTo(5);
        assertThat(report.holdoutWindow().get().phase()).isEqualTo(ResearchWindow.WindowPhase.HOLDOUT);

        List<RankedCandidate> training = report.trainingLeaderboard();
        assertThat(training).hasSize(3);
        assertThat(training.stream().map(RankedCandidate::holdoutRank)).containsExactlyInAnyOrder(1, 2, 3);

        RankedCandidate holdoutWinner = report.holdoutLeaderboard().get(0);
        assertThat(holdoutWinner.holdoutRank()).isEqualTo(1);
        assertThat(holdoutWinner.trainingScore().intValue()).isEqualTo(1);

        RankedCandidate rebuilt = training.stream()
                .filter(row -> row.candidateId().equals(holdoutWinner.candidateId()))
                .findFirst()
                .orElseThrow();
        assertThat(rebuilt.holdoutScore().intValue()).isEqualTo(2);
        assertThat(rebuilt.scoreDelta().intValue()).isEqualTo(1);
    }

    @Test
    void holdoutConfigurationValidation() {
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        assertThrows(IllegalArgumentException.class, () -> holdoutConfigBuilder(series).holdoutFraction(0d));
        assertThrows(IllegalArgumentException.class, () -> holdoutConfigBuilder(series).holdoutFraction(1d));
        assertThrows(IllegalArgumentException.class, () -> holdoutConfigBuilder(series).holdoutFraction(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> holdoutConfigBuilder(series).holdoutFraction(0.5d).holdoutBarCount(2));
        assertThrows(IllegalArgumentException.class, () -> holdoutConfigBuilder(series).holdoutBarCount(0));
        assertThrows(IllegalArgumentException.class, () -> holdoutConfigBuilder(series).holdoutBarCount(5).run());
    }

    @Test
    void objectiveMetricsPropagateToLeaderboard() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    Num score = window.series().numFactory().numOf(candidate);
                    Map<String, Num> metrics = new LinkedHashMap<>();
                    metrics.put("double", score.multipliedBy(window.series().numFactory().numOf(2)));
                    return ParameterResearch.ObjectiveEvaluation.of(score, metrics);
                })
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.trainingLeaderboard().get(0).trainingMetrics()).containsKey("double");
        assertThat(report.trainingLeaderboard().get(0).trainingMetrics().get("double").intValue()).isEqualTo(4);
    }

    @Test
    void topKWarningWhenFewerValidCandidatesThanRequested() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = sumGridBuilder(series, 2).topK(5).run();
        assertThat(report.trainingLeaderboard()).hasSize(2);
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("topK"));
    }

    @Test
    void trainingWindowIsolationLeakageControl() {
        double[] data = new double[20];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        BarSeries series = series(data);
        List<Double> observedCloses = new ArrayList<>();
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    assertThat(window.series().getBarCount()).isEqualTo(window.barCount());
                    if (window.phase() == ParameterResearch.ResearchWindow.WindowPhase.TRAINING) {
                        observedCloses.add(window.series().getBar(window.barCount() - 1).getClosePrice().doubleValue());
                    }
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(4))
                .holdoutBarCount(5)
                .topK(2)
                .run();

        assertThat(report.trainingWindow().barCount()).isEqualTo(15);
        assertThat(observedCloses).isNotEmpty();
        assertThat(observedCloses).allMatch(value -> value <= 14d);
        assertThat(observedCloses).contains(14d);
    }

    @Test
    void datasetRevisionFailsTheRun() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 4)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    series.addBar(new BaseBar(Duration.ofDays(1), Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(1)),
                            series.numFactory().numOf(1), series.numFactory().numOf(1), series.numFactory().numOf(1),
                            series.numFactory().numOf(1), series.numFactory().numOf(1), series.numFactory().numOf(1),
                            0L));
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(3));
        assertThrows(IllegalStateException.class, builder::run);
    }

    @Test
    void parameterSetCanonicalAccessorsAndStableId() {
        ParameterSet set = new ParameterSet(
                List.of(new ParameterValue("x", "5", false, ""), new ParameterValue("flag", "true", false, "")));
        assertThat(set.intValue("x")).isEqualTo(5);
        assertThat(set.booleanValue("flag")).isTrue();
        assertThat(set.value("x")).isEqualTo("5");
        assertThat(set.stableId()).isEqualTo("x=5|flag=true");
        assertThrows(IllegalArgumentException.class, () -> set.intValue("missing"));
        assertThrows(IllegalArgumentException.class, () -> set.booleanValue("x"));
    }

    @Test
    void decimalDomainRejectsCardinalityOverflow() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series)
                .decimal("a", 0d, 1d, 1e-20)
                .candidate((window, parameters) -> (int) Math.round(parameters.decimalValue("a") * 10))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(2));
        assertThrows(IllegalArgumentException.class, builder::run);
    }

    @Test
    void candidateDeclaredAfterObjectiveIsRejected() {
        BarSeries series = series(1d, 2d, 3d);
        assertThrows(IllegalStateException.class,
                () -> ParameterResearch.<Integer>builder(series)
                        .integer("a", 1, 3)
                        .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                                .of(window.series().numFactory().numOf(candidate)))
                        .candidate((window, parameters) -> parameters.intValue("a")));
    }

    @Test
    void geneticSearchCarriesElitesIntoLaterGenerations() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 10)
                .integer("b", 1, 10)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(12, 3L, new ParameterResearch.GeneticSettings(10, 1, 2, 0.9, 0.1)))
                .run();

        assertThat(report.counts().duplicate()).isGreaterThan(0);
        assertThat(report.counts().attempted()).isEqualTo(12);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.EVALUATION_BUDGET_EXHAUSTED);
    }

    @Test
    void geneticSearchFillsInitialPopulationFromRemainingSpace() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(4, 0L, new ParameterResearch.GeneticSettings(2, 1, 2, 0.9, 0.0)))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void datasetRevisionChangeOnFinalEvaluationIsRejected() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        AtomicInteger evaluations = new AtomicInteger();
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    if (evaluations.incrementAndGet() == 3) {
                        series.addPrice(5d);
                    }
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(3));
        assertThrows(IllegalStateException.class, builder::run);
    }

    @Test
    void trainingWindowMutationByObjectiveIsRejected() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        AtomicInteger evaluations = new AtomicInteger();
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    if (evaluations.incrementAndGet() == 2) {
                        window.series().addPrice(5d);
                    }
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.counts().successful()).isEqualTo(2);
        assertThat(report.counts().failed()).isEqualTo(1);
        assertThat(series.getBar(3).getClosePrice().doubleValue()).isEqualTo(4d);
    }

    @Test
    void objectiveIdCoversTerminationConfiguration() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> withTarget = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(3))
                .targetScore(series.numFactory().numOf(2));
        ParameterResearch.Builder<Integer> withoutTarget = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(3));
        assertThat(withTarget.run().objectiveId()).isNotEqualTo(withoutTarget.run().objectiveId());
    }

    @Test
    void objectiveIdSeparatesAmbiguousCategoricalLiterals() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport first = ParameterResearch.<String>builder(series)
                .categorical("label", "a,b", "c")
                .candidate((window, parameters) -> parameters.value("label"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(1)))
                .search(SearchPlan.grid(2))
                .run();
        ParameterResearchReport second = ParameterResearch.<String>builder(series)
                .categorical("label", "a", "b,c")
                .candidate((window, parameters) -> parameters.value("label"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(1)))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(first.objectiveId()).isNotEqualTo(second.objectiveId());
    }

    @Test
    void decimalDomainConsolidatesCollapsedPositions() {
        // 1e16 has ULP 2: positions 1e16, 1e16 + 1, 1e16 + 2 collapse to two
        // distinct doubles, so the domain must expose exactly those two values.
        BarSeries series = series(1d, 2d, 3d);
        List<String> evaluated = new ArrayList<>();
        ParameterResearchReport report = ParameterResearch.<String>builder(series)
                .decimal("a", 1e16, 1e16 + 2d, 1d)
                .candidate((window, parameters) -> parameters.value("a"))
                .maximize((candidate, window) -> {
                    evaluated.add(candidate);
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(1));
                })
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.counts().proposed()).isEqualTo(2);
        assertThat(report.counts().duplicate()).isZero();
        assertThat(evaluated).containsExactly("10000000000000000", "10000000000000002");
    }

    @Test
    void decimalDomainRejectsUnverifiableCollapse() {
        // More than 100_000 declared positions collapse at a step below half-ULP
        // precision; eager distinct-value verification is refused instead of
        // materializing a huge list.
        double huge = Math.scalb(1d, 53);
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series)
                .decimal("a", huge, huge + 100_000d, 1d)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(1));
        assertThrows(IllegalArgumentException.class, builder::run);
    }

    @Test
    void objectiveIdSeparatesAmbiguousDomainNames() {
        // A single boolean domain named "x|bool:y" must not fingerprint like two
        // boolean domains "x" and "y".
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport aliased = ParameterResearch.<Integer>builder(series)
                .bool("x|bool:y")
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(1))
                .run();
        ParameterResearchReport separated = ParameterResearch.<Integer>builder(series)
                .bool("x")
                .bool("y")
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(1))
                .run();

        assertThat(aliased.objectiveId()).isNotEqualTo(separated.objectiveId());
    }

    @Test
    void barMutationByObjectiveFailsEvaluationAndPreservesSource() {
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    window.series().getBar(0).addPrice(window.series().numFactory().numOf(99));
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                })
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.counts().successful()).isZero();
        assertThat(report.counts().failed()).isEqualTo(2);
        assertThat(report.failedEvaluations())
                .allSatisfy(failure -> assertThat(failure.reason()).contains("UnsupportedOperationException"));
        assertThat(series.getBar(0).getClosePrice().doubleValue()).isEqualTo(1d);
    }

    @Test
    void targetScoreCoercesCrossFactoryNums() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(3))
                .targetScore(DecimalNum.valueOf(2))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.TARGET_SCORE_REACHED);
    }

    @Test
    void rankingSortAcceptsMixedNumFactories() {
        // DecimalNum.compareTo and DoubleNum.compareTo each cast the argument to
        // their own implementation, so ranking scores produced by different
        // factories used to throw ClassCastException. Ranking must compare
        // cross-factory scores without coercing them — coercion at evaluation
        // time would destroy decimal precision.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(candidate == 1 ? DecimalNum.valueOf(10) : DoubleNum.valueOf(5)))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.counts().failed()).isZero();
        assertThat(report.trainingLeaderboard()).hasSize(2);
        assertThat(report.trainingLeaderboard().getFirst().parameters().intValue("a")).isEqualTo(1);
        assertThat(report.trainingLeaderboard().getFirst().trainingScore().doubleValue()).isEqualTo(10d);
    }

    @Test
    void failedEvaluationsRetainMetrics() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 1)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation.failed("boom",
                        Map.of("detail", DecimalNum.valueOf(7))))
                .search(SearchPlan.grid(1))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        assertThat(report.failedEvaluations()).hasSize(1);
        assertThat(report.failedEvaluations().getFirst().metrics()).containsEntry("detail", DecimalNum.valueOf(7));
    }

    @Test
    void decimalDomainCountsExactGridPositions() {
        // (0.9999999995 - 0) / 0.5 is 1.999999999, which the old double
        // arithmetic pushed to 2.0 with its 1e-9 fudge, inventing a third
        // declared position. Exact decimal arithmetic keeps the two real ones.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<String>builder(series)
                .decimal("x", 0d, 0.9999999995, 0.5)
                .candidate((window, parameters) -> parameters.value("x"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(1)))
                .search(SearchPlan.grid(3))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().proposed()).isEqualTo(2);
        assertThat(report.counts().attempted()).isEqualTo(2);
    }

    @Test
    void targetScoreComparisonPreservesDecimalPrecision() {
        // Coercing the target through doubleValue() collapses
        // 1.0000000000000000001 to 1.0 and stops after the first, slightly
        // lower score. Exact same-factory comparison must require the
        // genuinely higher score.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(candidate == 1 ? DecimalNum.valueOf("1.0000000000000000000")
                                : DecimalNum.valueOf("1.0000000000000000002")))
                .search(SearchPlan.grid(2))
                .targetScore(DecimalNum.valueOf("1.0000000000000000001"))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.TARGET_SCORE_REACHED);
        assertThat(report.counts().attempted()).isEqualTo(2);
    }

    @Test
    void normalizerSeesReadOnlyTrainingWindow() {
        // The normalizer receives the read-only window series even without
        // holdout validation, so a mutating normalizer is rejected instead of
        // corrupting the source series.
        BarSeries series = series(1d, 2d, 3d, 4d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .normalize((data, name, value) -> {
                    data.getBar(data.getEndIndex()).addPrice(data.numFactory().numOf(99));
                    return new ParameterValue(name, value, false, "");
                })
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.counts().rejected()).isEqualTo(2);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        assertThat(series.getBar(3).getClosePrice().doubleValue()).isEqualTo(4d);
    }

    @Test
    void geneticStagnationIgnoresTieBreakerImprovements() {
        // The no-improvement streak must track primary scores only: repair-count
        // tie-breakers improving generation over generation must not reset it.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 10)),
                DomainSpec.of(ParameterDomain.integer("b", 1, 10)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> {
            int byScore = b.score().compareTo(a.score());
            return byScore != 0 ? byScore : Integer.compare(a.parameters().repairCount(), b.parameters().repairCount());
        };
        GeneticSearchEngine engine = new GeneticSearchEngine(specs, new GeneticSettings(4, 1, 2, 0.9, 0.1),
                new Random(0), ranking, Direction.MAXIMIZE, -1, 2);

        List<List<Integer>> repairScript = List.of(List.of(4, 3, 2, 1), List.of(1, 0, 0, 0), List.of(0, 0, 0, 0));
        for (List<Integer> repairs : repairScript) {
            List<ParameterSet> batch = engine.propose(10);
            assertThat(batch).isNotEmpty();
            for (int i = 0; i < batch.size(); i++) {
                ParameterSet set = batch.get(i);
                int count = repairs.get(Math.min(i, repairs.size() - 1));
                engine.observe(EvaluatedCandidate.valid(set.stableId(), withRepairs(set, count), i,
                        DecimalNum.valueOf(5), Map.of()));
            }
        }

        assertThat(engine.propose(10)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.NO_IMPROVEMENT);
    }

    @Test
    void finalizedObservationCountsFinalGeneration() {
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 4)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        GeneticSearchEngine engine = new GeneticSearchEngine(specs, new GeneticSettings(2, 1, 2, 0.0, 0.0),
                new Random(0), ranking, Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> batch = engine.propose(4);
        assertThat(batch).isNotEmpty();
        for (int i = 0; i < batch.size(); i++) {
            ParameterSet set = batch.get(i);
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(1), Map.of()));
        }

        assertThat(engine.iterationsCompleted()).isZero();
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
        engine.finalizeObserved();
        assertThat(engine.iterationsCompleted()).isEqualTo(1);
    }

    @Test
    void particleSwarmContinuesThroughTransientCollisions() {
        // With inertia disabled and unit attraction weights the movement is
        // fully scripted. The first post-observation projection collides with
        // the two already-seen points; the engine must keep moving until a
        // particle reaches the unseen middle point instead of declaring
        // convergence on the first cached batch.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 1, 3)));
        Comparator<EvaluatedCandidate> ranking = (a, b) -> b.score().compareTo(a.score());
        ParticleSwarmEngine engine = new ParticleSwarmEngine(specs, new SwarmSettings(2, 0.0, 1.0, 1.0, 1.0),
                new ScriptedRandom(0d, 1d, 0.2, 0.2, 0.5, 0.5, 0.2, 0.2, 0.5, 0.5, 0.2, 0.2, 0.5, 0.5), ranking,
                Direction.MAXIMIZE, -1, -1);

        List<ParameterSet> first = engine.propose(2);
        assertThat(first).hasSize(2);
        for (int i = 0; i < first.size(); i++) {
            ParameterSet set = first.get(i);
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(i + 1), Map.of()));
        }

        List<ParameterSet> second = engine.propose(2);
        assertThat(engine.terminationReason()).isNull();
        assertThat(second).hasSize(2);
        assertThat(second.stream().map(ParameterSet::stableId)).contains("a=2");

        for (int i = 0; i < second.size(); i++) {
            ParameterSet set = second.get(i);
            int score = set.stableId().equals("a=2") ? 3 : 2;
            engine.observe(EvaluatedCandidate.valid(set.stableId(), set, i, DecimalNum.valueOf(score), Map.of()));
        }

        assertThat(engine.propose(2)).isEmpty();
        assertThat(engine.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    private static ParameterSet withRepairs(ParameterSet set, int repairs) {
        List<ParameterValue> values = new ArrayList<>();
        for (int i = 0; i < set.values().size(); i++) {
            ParameterValue value = set.values().get(i);
            values.add(i < repairs ? new ParameterValue(value.name(), value.value(), true, "repaired") : value);
        }
        return new ParameterSet(values);
    }

    /**
     * Deterministic {@link Random} feeding one scripted {@code nextDouble()} draw
     * per call; the particle-swarm engines use no other random primitive.
     */
    private static final class ScriptedRandom extends Random {

        private final double[] script;
        private int cursor;

        private ScriptedRandom(double... script) {
            this.script = script;
        }

        @Override
        public double nextDouble() {
            if (cursor >= script.length) {
                throw new IllegalStateException("script exhausted at draw " + cursor);
            }
            return script[cursor++];
        }
    }

    private static ParameterResearchReport runGenetic(BarSeries series, long seed) {
        return ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 10)
                .integer("b", 1, 10)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(10, seed, new ParameterResearch.GeneticSettings(4, 1, 2, 0.9, 0.1)))
                .run();
    }
}
