/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.CandidateValidator;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterNormalizer;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.ParameterSet;
import org.ta4j.core.research.ParameterResearch.ParameterValue;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.ResearchWindow;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
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
