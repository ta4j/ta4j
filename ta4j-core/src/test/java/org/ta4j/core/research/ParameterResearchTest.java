/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.MathContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.NaN;
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
import org.ta4j.core.research.ParameterResearch.RunCounts;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.research.ParameterResearch.SwarmSettings;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

class ParameterResearchTest {

    private static BarSeries series(double... closes) {
        return new MockBarSeriesBuilder().withData(closes).build();
    }

    private static ParameterResearch.CandidateStage<Integer> sumGridBuilder(BarSeries series, int budget) {
        return ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .integer("b", 3, 4)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(budget));
    }

    private static ParameterResearch.CandidateStage<Integer> holdoutConfigBuilder(BarSeries series) {
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
        ParameterResearch.CandidateStage<Integer> noDomains = ParameterResearch.<Integer>builder(series)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(series.numFactory().numOf(candidate)))
                .search(SearchPlan.grid(4));
        assertThrows(IllegalStateException.class, noDomains::run);
        ParameterResearch.CandidateStage<Integer> noObjective = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> 1)
                .search(SearchPlan.grid(4));
        assertThrows(IllegalStateException.class, noObjective::run);

        ParameterResearch.CandidateStage<Integer> noPlan = ParameterResearch.<Integer>builder(series)
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
    void searchPlanRejectsSettingsItsEngineNeverReads() {
        GeneticSettings genetic = GeneticSettings.defaults();
        SwarmSettings swarm = SwarmSettings.defaults();
        assertThrows(IllegalArgumentException.class, () -> new SearchPlan(SearchPlan.Kind.GRID, 4, 7L, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchPlan(SearchPlan.Kind.GRID, 4, 0L, genetic, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchPlan(SearchPlan.Kind.GRID, 4, 0L, null, swarm));
        assertThrows(IllegalArgumentException.class,
                () -> new SearchPlan(SearchPlan.Kind.GENETIC, 4, 1L, genetic, swarm));
        assertThrows(IllegalArgumentException.class,
                () -> new SearchPlan(SearchPlan.Kind.PARTICLE_SWARM, 4, 1L, genetic, swarm));
    }

    @Test
    void runCountsRejectNegativeAccounting() {
        assertThrows(IllegalArgumentException.class, () -> new RunCounts(-1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunCounts(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1, -1));
    }

    @Test
    void rankedCandidateRejectsImpossibleStates() {
        ParameterSet set = new ParameterSet(List.of(new ParameterValue("x", "5", false, "")));
        Num score = DecimalNum.valueOf(1);
        assertThat(
                new RankedCandidate(set.stableId(), set, 1, null, score, null, null, Map.of(), Map.of()).trainingRank())
                .isEqualTo(1);
        assertThrows(IllegalArgumentException.class,
                () -> new RankedCandidate("other", set, 1, null, score, null, null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RankedCandidate(set.stableId(), set, 0, null, score, null, null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RankedCandidate(set.stableId(), set, 1, 0, score, score,
                score.minus(score), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RankedCandidate(set.stableId(), set, 1, 1, score, null, null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RankedCandidate(set.stableId(), set, 1, 1, score, score, null, Map.of(), Map.of()));
    }

    private static RankedCandidate rankedRow(int trainingRank) {
        ParameterSet set = new ParameterSet(List.of(new ParameterValue("x", "5", false, "")));
        return new RankedCandidate(set.stableId(), set, trainingRank, null, DecimalNum.valueOf(1), null, null, Map.of(),
                Map.of());
    }

    @Test
    void reportRejectsFactuallyInconsistentCarriers() {
        BarSeries series = series(1d, 2d, 3d);
        ResearchWindow window = new ResearchWindow(series, 0, 2, ResearchWindow.WindowPhase.TRAINING, "train");
        assertThrows(IllegalArgumentException.class, () -> report(window, Optional.empty(), 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> report(window, Optional.empty(), 1, List.of(rankedRow(1), rankedRow(2))));
        assertThrows(IllegalArgumentException.class,
                () -> report(window, Optional.empty(), 2, List.of(rankedRow(2), rankedRow(1))));
        RankedCandidate holdoutRow = new RankedCandidate(
                new ParameterSet(List.of(new ParameterValue("x", "5", false, ""))).stableId(),
                new ParameterSet(List.of(new ParameterValue("x", "5", false, ""))), 1, 1, DecimalNum.valueOf(1),
                DecimalNum.valueOf(2), DecimalNum.valueOf(1), Map.of(), Map.of());
        ResearchWindow holdout = new ResearchWindow(series(1d), 0, 0, ResearchWindow.WindowPhase.HOLDOUT, "holdout");
        assertThrows(IllegalArgumentException.class,
                () -> report(window, Optional.empty(), 1, List.of(rankedRow(1)), List.of(holdoutRow)));
        assertThrows(IllegalArgumentException.class,
                () -> report(window, Optional.of(holdout), 1, List.of(rankedRow(1)), List.of(rankedRow(1))));
        assertThrows(IllegalArgumentException.class,
                () -> report(window, Optional.of(holdout), 1, List.of(rankedRow(1)), List.of(holdoutRow), -1L));
    }

    private static ParameterResearchReport report(ResearchWindow window, Optional<ResearchWindow> holdout, int topK,
            List<RankedCandidate> trainingLeaderboard) {
        return report(window, holdout, topK, trainingLeaderboard, List.of());
    }

    private static ParameterResearchReport report(ResearchWindow window, Optional<ResearchWindow> holdout, int topK,
            List<RankedCandidate> trainingLeaderboard, List<RankedCandidate> holdoutLeaderboard) {
        return report(window, holdout, topK, trainingLeaderboard, holdoutLeaderboard, 1L);
    }

    private static ParameterResearchReport report(ResearchWindow window, Optional<ResearchWindow> holdout, int topK,
            List<RankedCandidate> trainingLeaderboard, List<RankedCandidate> holdoutLeaderboard,
            long elapsedEvaluationNanos) {
        return new ParameterResearchReport("dataset", SearchPlan.grid(topK == 0 ? 1 : topK), "objective", window,
                holdout, topK, trainingLeaderboard, holdoutLeaderboard, TerminationReason.SEARCH_SPACE_EXHAUSTED,
                new RunCounts(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0), List.of(), elapsedEvaluationNanos, 0L,
                List.of());
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
    void nonFiniteObjectiveScoreKeepsReportedMetrics() {
        // A valid-status outcome whose score is not finite is converted to a
        // failed evaluation; the metrics the objective reported must stay
        // attached for diagnostics instead of being dropped on conversion.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation.of(NaN.NaN,
                        Map.of("acc", series.numFactory().numOf(0.5))))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.failedEvaluations()).hasSize(2);
        assertThat(report.failedEvaluations().get(0).metrics()).containsEntry("acc", series.numFactory().numOf(0.5));
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
        // With canonical (post-normalization) identities, a proposal that
        // repairs onto another candidate's value collides with it in the
        // cache instead of doubling it; among DISTINCT candidates with equal
        // scores, the repaired one still ranks below unrepaired ones.
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
                        .of(window.series().numFactory().one()))
                .normalize(normalizer)
                .search(SearchPlan.grid(5))
                .run();

        assertThat(report.counts().repaired()).isEqualTo(1);
        assertThat(report.trainingLeaderboard()).hasSize(4);
        RankedCandidate top = report.trainingLeaderboard().get(0);
        RankedCandidate bottom = report.trainingLeaderboard().get(3);
        assertThat(top.parameters().repairCount()).isEqualTo(0);
        assertThat(bottom.parameters().intValue("a")).isEqualTo(5);
        assertThat(bottom.parameters().repairCount()).isEqualTo(1);
        assertThat(bottom.parameters().repairs()).containsEntry("a", "clamped");
    }

    @Test
    void nonCanonicalNormalizerOutputCollapsesOntoDeclaredCandidate() {
        // A normalizer may emit a declared value in a non-canonical string form
        // ("05" instead of "5"). Run identity must be the declared canonical
        // value, so the repair deduplicates against the later proposal of the
        // same logical candidate instead of evaluating it twice under two keys.
        BarSeries series = series(1d, 2d, 3d);
        ParameterNormalizer normalizer = (data, name, value) -> "1".equals(value)
                ? new ParameterValue(name, "05", true, "padded")
                : new ParameterValue(name, value, false, "");
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize(normalizer)
                .search(SearchPlan.grid(5))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(4);
        assertThat(report.counts().repaired()).isEqualTo(1);
        assertThat(report.counts().duplicate()).isEqualTo(1);
        assertThat(report.trainingLeaderboard()).hasSize(4);
        RankedCandidate top = report.trainingLeaderboard().get(0);
        assertThat(top.parameters().value("a")).isEqualTo("5");
        assertThat(top.parameters().repairCount()).isEqualTo(1);
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
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
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
    void targetScoreRejectsNonFiniteValues() {
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 5)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)));

        assertThrows(IllegalArgumentException.class, () -> builder.targetScore(NaN.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> builder.targetScore(DoubleNum.valueOf(Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.targetScore(DoubleNum.valueOf(Double.NEGATIVE_INFINITY)));
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
        assertThat(report.counts().attempted()).isEqualTo(6);
        assertThat(report.counts().holdoutAttempted()).isEqualTo(3);
        assertThat(report.counts().budgetRemaining()).isEqualTo(1);
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
    void holdoutReservationFailsFastWhenBudgetCannotCoverTopK() {
        // Default topK(10) exceeds the grid budget: the reservation leaves no
        // training evaluation, so run() must fail before searching.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        assertThrows(IllegalArgumentException.class,
                () -> holdoutConfigBuilder(series).holdoutBarCount(2).search(SearchPlan.grid(3)).run());
        // Exact equality also leaves no training evaluation.
        assertThrows(IllegalArgumentException.class,
                () -> holdoutConfigBuilder(series).holdoutBarCount(2).search(SearchPlan.grid(3)).topK(3).run());
    }

    @Test
    void holdoutRebuildConsumesReservedBudgetAndReportsIt() {
        // budget 3 = 2 training attempts (a in 1..2) + 1 reserved holdout
        // evaluation: total objective calls equal the budget exactly, and the
        // report separates training attempts from holdout attempts.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        ParameterResearchReport report = holdoutConfigBuilder(series).holdoutBarCount(2)
                .search(SearchPlan.grid(3))
                .topK(1)
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.counts().holdoutAttempted()).isEqualTo(1);
        assertThat(report.counts().budgetRemaining()).isEqualTo(0);
        assertThat(report.holdoutLeaderboard()).hasSize(1);
    }

    @Test
    void holdoutRebuildTimeCountsTowardElapsedEvaluationNanos() {
        // The objective sleeps only on the 2-bar holdout window; training on
        // the 3-bar window is trivial. If the rebuild path stops accumulating
        // candidate/objective time, elapsedEvaluationNanos stays at the
        // training-only level and this assertion fails.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        ParameterResearchReport report = holdoutConfigBuilder(series).maximize((candidate, window) -> {
            if (window.series().getBarCount() == 2) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return ParameterResearch.ObjectiveEvaluation.of(series.numFactory().numOf(candidate));
        }).holdoutBarCount(2).search(SearchPlan.grid(3)).topK(1).run();

        assertThat(report.counts().holdoutAttempted()).isEqualTo(1);
        assertThat(report.elapsedEvaluationNanos()).isGreaterThanOrEqualTo(10_000_000L);
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
    void callbackMutationOfObjectiveDoesNotAlterRunningSearch() {
        // A callback that retains the builder and mutates it must not change
        // the objective of the run already in flight: the first evaluation
        // (candidate 0) swaps the builder to a different objective and
        // direction, but the running search keeps the snapshot taken at
        // run() start, so the leaderboard still reflects the original
        // maximize objective with candidate 4 on top.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 0, 4)
                .candidate((window, parameters) -> parameters.intValue("a"));
        builder.maximize((candidate, window) -> {
            if (candidate == 0) {
                builder.minimize((c, w) -> ParameterResearch.ObjectiveEvaluation.of(series.numFactory().numOf(c)));
            }
            return ParameterResearch.ObjectiveEvaluation.of(series.numFactory().numOf(candidate));
        }).search(SearchPlan.grid(5));

        ParameterResearchReport report = builder.run();

        assertThat(report.trainingLeaderboard().get(0).parameters().intValue("a")).isEqualTo(4);
        assertThat(report.counts().attempted()).isEqualTo(5);
    }

    @Test
    void candidateRebindingIsRejected() {
        // Retaining the first CandidateStage while binding a second factory
        // would let both stages mutate the same objective fields and can
        // bridge a ClassCastException inside run(). The builder must reject
        // the second binding so the factory (and every stage's candidate
        // type) stays fixed for the workflow.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.Builder<Integer> builder = ParameterResearch.<Integer>builder(series).integer("a", 1, 2);
        builder.candidate((window, parameters) -> parameters.intValue("a"));

        assertThrows(IllegalStateException.class,
                () -> builder.candidate((window, parameters) -> parameters.intValue("a") + 1));
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
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
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
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .decimal("a", 0d, 1d, 1e-20)
                .candidate((window, parameters) -> (int) Math.round(parameters.decimalValue("a") * 10))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(2));
        assertThrows(IllegalArgumentException.class, builder::run);
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
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
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
        ParameterResearch.CandidateStage<Integer> withTarget = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.grid(3))
                .targetScore(series.numFactory().numOf(2));
        ParameterResearch.CandidateStage<Integer> withoutTarget = ParameterResearch.<Integer>builder(series)
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
    void decimalDomainConsolidatesAboveHalfUlpCollisions() {
        // Step 1.1 exceeds half the ULP at 1e16 (1.0) but is still below the
        // full ULP (2.0): the four declared positions canonicalize as 1e16,
        // 1e16 + 2, 1e16 + 2, and 1e16 + 4, so the domain must collapse the
        // duplicated middle position instead of declaring four points.
        BarSeries series = series(1d, 2d, 3d);
        List<String> evaluated = new ArrayList<>();
        ParameterResearchReport report = ParameterResearch.<String>builder(series)
                .decimal("a", 1e16, 1e16 + 4d, 1.1d)
                .candidate((window, parameters) -> parameters.value("a"))
                .maximize((candidate, window) -> {
                    evaluated.add(candidate);
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(1));
                })
                .search(SearchPlan.grid(4))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(3);
        assertThat(report.counts().proposed()).isEqualTo(3);
        assertThat(report.counts().duplicate()).isZero();
        assertThat(evaluated).containsExactly("10000000000000000", "10000000000000002", "10000000000000004");
    }

    @Test
    void decimalDomainProjectsSwarmPositionsOntoConsolidatedGrid() {
        // 1e16 has ULP 2: a declared step of 0.1 consolidates 1,001 positions
        // into 51 distinct doubles. Swarm projection must land on the nearest
        // distinct double instead of an index of the uncompressed step grid,
        // where every position above the lower bound would clamp to the last
        // value.
        DomainSpec spec = DomainSpec.of(ParameterDomain.decimal("a", 1e16, 1e16 + 100d, 0.1d));
        assertThat(spec.cardinality()).isEqualTo(51);
        assertThat(spec.valueAt(spec.projectIndex(1e16 + 5d))).isEqualTo("10000000000000004");
        assertThat(spec.valueAt(spec.projectIndex(1e16 + 50d))).isEqualTo("10000000000000050");
        assertThat(spec.valueAt(spec.projectIndex(1e16 - 1d))).isEqualTo("10000000000000000");
        assertThat(spec.valueAt(spec.projectIndex(1e16 + 101d))).isEqualTo("10000000000000100");
    }

    @Test
    void decimalDomainRejectsUnverifiableCollapse() {
        // More than 100_000 declared positions collapse at a step below half-ULP
        // precision; eager distinct-value verification is refused instead of
        // materializing a huge list.
        double huge = Math.scalb(1d, 53);
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
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
    void windowBarsAreFrozenCopiesAndSourceMutationsAreRejected() {
        // Mutating the caller's original series mid-run must not leak into the
        // already-built research window: its bars are copies, not views.
        BarSeries series = series(1d, 2d, 3d, 4d);
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger observedWindowClose = new AtomicInteger();
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    if (evaluations.incrementAndGet() == 1) {
                        series.getBar(1).addPrice(DecimalNum.valueOf(999));
                    }
                    observedWindowClose.set(window.series().getBar(1).getClosePrice().intValue());
                    return ParameterResearch.ObjectiveEvaluation.of(window.series().getBar(1).getClosePrice());
                })
                .search(SearchPlan.grid(2));

        assertThrows(IllegalStateException.class, builder::run);
        assertThat(observedWindowClose).hasValue(2);
        assertThat(series.getBar(1).getClosePrice().doubleValue()).isEqualTo(999d);
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
    void compareScoresIsSymmetricAcrossFactories() {
        // 0.1 as a double is slightly above the decimal 0.1, so the decimal
        // literal below is strictly greater. Coercing the right operand into
        // the left factory used to report equality in one direction only;
        // the comparison must agree from both directions.
        Num decimal = DecimalNum.valueOf("0.100000000000000001");
        Num floating = DoubleNum.valueOf(0.1);

        assertThat(ParameterResearch.compareScores(decimal, floating)).isPositive();
        assertThat(ParameterResearch.compareScores(floating, decimal)).isNegative();
        assertThat(ParameterResearch.compareScores(decimal, floating))
                .isEqualTo(-ParameterResearch.compareScores(floating, decimal));
    }

    @Test
    void compareScoresNormalizesSignedZeroAcrossFactories() {
        // DoubleNum distinguishes -0.0 from 0.0, so the old cross-class path
        // could rank a signed zero against an unsigned one, breaking
        // transitivity with the decimal zero. Every zero must rank equal.
        Num negativeZero = DoubleNum.valueOf(-0.0);
        Num positiveZero = DoubleNum.valueOf(0.0);
        Num decimalZero = DecimalNum.valueOf(0);

        assertThat(ParameterResearch.compareScores(negativeZero, positiveZero)).isZero();
        assertThat(ParameterResearch.compareScores(positiveZero, negativeZero)).isZero();
        assertThat(ParameterResearch.compareScores(negativeZero, decimalZero)).isZero();
        assertThat(ParameterResearch.compareScores(decimalZero, positiveZero)).isZero();
        assertThat(ParameterResearch.compareScores(decimalZero, DoubleNum.valueOf(1))).isNegative();
        assertThat(ParameterResearch.compareScores(DoubleNum.valueOf(1), negativeZero)).isPositive();
    }

    @Test
    void subtractScoresPreservesDecimalPrecisionAcrossFactories() {
        // Coercing the decimal subtrahend through the DoubleNum minuend's
        // factory used to round 0.100000000000000001 down to 0.1 and report
        // a zero delta; a decimal score outside double range used to coerce
        // to infinity. Cross-factory subtraction must stay exact.
        Num delta = ParameterResearch.subtractScores(DoubleNum.valueOf(0.1),
                DecimalNum.valueOf("0.100000000000000001"));
        assertThat(delta.bigDecimalValue()).isEqualByComparingTo(new BigDecimal("-0.000000000000000001"));

        Num huge = ParameterResearch.subtractScores(DoubleNum.valueOf(1), DecimalNum.valueOf("1e1000"));
        assertThat(huge.bigDecimalValue()).isEqualByComparingTo(BigDecimal.ONE.subtract(new BigDecimal("1e1000")));
    }

    @Test
    void subtractScoresKeepsSameFactoryOverflowFinite() {
        // Double.MAX_VALUE - (-Double.MAX_VALUE) overflows to positive
        // infinity in native DoubleNum arithmetic even though both operands
        // are finite; the delta must fall back to the exact decimal path
        // instead of emitting a non-finite report value.
        Num delta = ParameterResearch.subtractScores(DoubleNum.valueOf(Double.MAX_VALUE),
                DoubleNum.valueOf(-Double.MAX_VALUE));
        assertThat(Num.isFinite(delta)).isTrue();
        assertThat(delta.bigDecimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2)));
    }

    @Test
    void sameClassScoresFromForeignFactoriesSubtractDecimally() {
        // Two same-class Num instances can still reject each other's native
        // arithmetic: NonDecimalDelegateNum.minus delegates to DecimalNum,
        // which casts the subtrahend, so the old class-equality fast path
        // threw ClassCastException. Score deltas must never take a native
        // path and must stay an exact decimal across any factories.
        Num minuend = new NonDecimalDelegateNum(DecimalNum.valueOf("2.5"));
        Num subtrahend = new NonDecimalDelegateNum(DecimalNum.valueOf(2));

        Num delta = ParameterResearch.subtractScores(minuend, subtrahend);

        assertThat(delta.bigDecimalValue()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void holdoutDeltaToleratesMixedNumFactories() {
        // A valid objective may return DecimalNum during training and
        // DoubleNum during holdout; report-building score deltas must not
        // throw ClassCastException when subtracting across factories.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 1)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.phase() == ResearchWindow.WindowPhase.TRAINING ? DecimalNum.valueOf(2)
                                : DoubleNum.valueOf(1)))
                .search(SearchPlan.grid(3))
                .holdoutBarCount(2)
                .topK(1)
                .run();

        assertThat(report.counts().failed()).isZero();
        assertThat(report.trainingLeaderboard()).isNotEmpty();
        assertThat(report.trainingLeaderboard().getFirst().scoreDelta().doubleValue()).isEqualTo(-1d);
        assertThat(report.holdoutLeaderboard()).isNotEmpty();
        assertThat(report.holdoutLeaderboard().getFirst().scoreDelta().doubleValue()).isEqualTo(-1d);
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
    void targetScoreRejectsCrossFactoryUnderflow() {
        // Coercing a tiny DecimalNum target through the score's DoubleNum
        // factory underflows 1e-1000 to 0.0, so an all-zero maximizing run
        // used to declare TARGET_SCORE_REACHED after its first evaluation.
        // Factory-independent comparison must keep the search alive.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation.of(DoubleNum.valueOf(0)))
                .search(SearchPlan.grid(3))
                .targetScore(DecimalNum.valueOf("1e-1000"))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.counts().attempted()).isEqualTo(3);
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
    void cohortSettingsRejectOversizedSwarmsAndPopulations() {
        // Settings beyond the engine cohort bound must fail at plan-build time
        // instead of pre-allocating an unbounded list before the first proposal.
        assertThrows(IllegalArgumentException.class,
                () -> new ParameterResearch.SwarmSettings(Integer.MAX_VALUE, 0.5, 0.5, 0.5, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ParameterResearch.GeneticSettings(Integer.MAX_VALUE, 0, 2, 0.9, 0.1));
    }

    @Test
    void researchWindowSupportsTerminalMaximumIndex() {
        // A retained series may legally end at Integer.MAX_VALUE; the window
        // builder must copy the inclusive source range directly instead of
        // overflowing an exclusive end index. The fixture itself must land on
        // the terminal index, and the objective must read the copied bar so
        // that a missing or empty window cannot pass.
        final Bar first = new TimeBarBuilder(DoubleNumFactory.getInstance()).timePeriod(Duration.ofMinutes(1))
                .endTime(Instant.parse("2026-01-01T00:01:00Z"))
                .closePrice(1)
                .build();
        BarSeries series = new BaseBarSeriesBuilder().withBars(List.of(first))
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        assertThat(series.getEndIndex()).isEqualTo(Integer.MAX_VALUE);

        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().getBar(0).getClosePrice()))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
        assertThat(report.trainingWindow().barCount()).isEqualTo(1);
        assertThat(report.trainingWindow().series().getEndIndex()).isEqualTo(0);
        assertThat(report.trainingWindow().series().getBar(0).getClosePrice().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void searchPlansRejectBudgetsBeyondRetentionLimit() {
        // Every evaluated candidate is retained for the report, so a budget
        // that cannot be retained must fail fast at plan construction instead
        // of exhausting memory mid-run.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SearchPlan.grid(ParameterResearch.MAX_RETAINED_EVALUATIONS + 1));
        assertThat(exception.getMessage()).contains("retained-evaluation limit");
    }

    @Test
    void searchPlansRejectMissingGeneticSettings() {
        // Kind-specific settings must fail fast at plan construction instead
        // of materializing research windows and dereferencing null settings
        // inside the engine mid-run.
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new SearchPlan(SearchPlan.Kind.GENETIC, 10, 0L, null, null));
        assertThat(exception.getMessage()).contains("geneticSettings");
    }

    @Test
    void searchPlansRejectMissingSwarmSettings() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new SearchPlan(SearchPlan.Kind.PARTICLE_SWARM, 10, 0L, null, null));
        assertThat(exception.getMessage()).contains("swarmSettings");
    }

    @Test
    void gridRejectsEveryProposalAndStillTerminates() {
        // A rejecting normalizer never shrinks the budget, so the grid keeps
        // proposing until the space is exhausted. The engine must terminate
        // with an exhausted space without retaining every proposed id.
        BarSeries series = series(1d, 2d, 3d);

        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 3)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize((data, name, value) -> null)
                .search(SearchPlan.grid(1))
                .run();
        assertThat(report.counts().attempted()).isZero();
        assertThat(report.counts().proposed()).isEqualTo(3);
        // Zero successful evaluations overrides the engine-level exhaustion
        // reason, matching the other all-rejected scenarios.
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
    }

    @Test
    void rawReproposalsReinvokeCallbacksAndCountAgainstTheProposalCap() {
        // Genetic elitism re-proposes already-seen raw sets: the 8 elite
        // slots of every later generation carry gen-1 raw sets forward.
        // Normalizers and validators are not required to be pure or
        // idempotent, so every proposal event must re-invoke both callbacks,
        // and raw re-proposals must count against the run-wide proposal cap
        // so that elite-dominated callback work stays bounded.
        int originalLimit = ParameterResearch.MAX_PROPOSALS_PER_RUN;
        ParameterResearch.MAX_PROPOSALS_PER_RUN = 40;
        try {
            BarSeries series = series(1d, 2d, 3d, 4d, 5d);
            AtomicInteger normalizerCalls = new AtomicInteger();
            AtomicInteger validatorCalls = new AtomicInteger();
            Set<String> validatedRawIds = Collections.synchronizedSet(new HashSet<>());

            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .integer("a", 1, 100)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                            .of(window.series().numFactory().numOf(candidate)))
                    .normalize((data, name, value) -> {
                        normalizerCalls.incrementAndGet();
                        return new ParameterValue(name, value, true, "");
                    })
                    .validate(parameters -> {
                        validatorCalls.incrementAndGet();
                        validatedRawIds.add(parameters.stableId());
                    })
                    .search(SearchPlan.genetic(1000, 7L, new ParameterResearch.GeneticSettings(10, 8, 2, 0.9, 0.1)))
                    .run();

            // Elite slots really did re-propose gen-1 raw sets, each proposal
            // event re-invoked both callbacks and incremented the repaired
            // count, and the cap terminated the run once the counted proposal
            // events (re-proposals included) reached the limit.
            assertThat(report.counts().proposed()).isGreaterThan(validatedRawIds.size());
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.PROPOSAL_LIMIT_EXCEEDED);
            assertThat(normalizerCalls.get()).isEqualTo(report.counts().proposed());
            assertThat(validatorCalls.get()).isEqualTo(report.counts().proposed());
            assertThat(report.counts().repaired()).isEqualTo(report.counts().proposed());
            assertThat(report.counts().proposed()).isEqualTo(40);
        } finally {
            ParameterResearch.MAX_PROPOSALS_PER_RUN = originalLimit;
        }
    }

    @Test
    void decimalNormalizationBeyondLastDeclaredIndexKeepsRawIdentity() {
        // Domain 0..1 step 0.6 declares only 0 and 0.6; a normalizer mapping
        // "0" onto "1.0" (beyond the last declared index) must keep "1.0" as
        // its own raw identity instead of clamping it onto the last declared
        // position: both grid proposals stay distinct.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .decimal("a", 0d, 1d, 0.6d)
                .candidate((window, parameters) -> 1)
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(1)))
                .normalize((data, name, value) -> "0".equals(value) ? new ParameterValue(name, "1.0", true, "swapped")
                        : new ParameterValue(name, "1", true, "swapped"))
                .search(SearchPlan.grid(2))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.counts().duplicate()).isZero();
    }

    @Test
    void rejectionHeavyRunsTerminateAtTheProposalLimit() {
        // Rejected proposals never consume the evaluation budget, so a
        // rejection-heavy run over a space too large to exhaust would propose
        // (and retain proposal ids) without bound. The run-wide proposal cap
        // must terminate the search independent of completed evaluations.
        int originalLimit = ParameterResearch.MAX_PROPOSALS_PER_RUN;
        ParameterResearch.MAX_PROPOSALS_PER_RUN = 500;
        try {
            BarSeries series = series(1d, 2d, 3d);

            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .decimal("a", 0, 1_000_000_000, 1)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                            .of(window.series().numFactory().numOf(candidate)))
                    .normalize((data, name, value) -> null)
                    .search(SearchPlan.genetic(1, 7L, ParameterResearch.GeneticSettings.defaults()))
                    .run();
            assertThat(report.counts().attempted()).isZero();
            assertThat(report.counts().proposed()).isLessThanOrEqualTo(500L);
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        } finally {
            ParameterResearch.MAX_PROPOSALS_PER_RUN = originalLimit;
        }
    }

    @Test
    void proposalCapBoundsBatchGranularityOvershoot() {
        // A genetic population larger than the remaining proposal allowance
        // must not push the run past MAX_PROPOSALS_PER_RUN: the propose
        // request is bounded by the allowance, so a rejection-only run never
        // processes or retains more proposals than the configured cap.
        int originalLimit = ParameterResearch.MAX_PROPOSALS_PER_RUN;
        ParameterResearch.MAX_PROPOSALS_PER_RUN = 500;
        try {
            BarSeries series = series(1d, 2d, 3d);

            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .decimal("a", 0, 1_000_000_000, 1)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                            .of(window.series().numFactory().numOf(candidate)))
                    .normalize((data, name, value) -> null)
                    .search(SearchPlan.genetic(1_000_000, 7L,
                            new ParameterResearch.GeneticSettings(1000, 2, 5, 0.9, 0.1)))
                    .run();
            assertThat(report.counts().attempted()).isZero();
            assertThat(report.counts().proposed()).isEqualTo(500L);
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        } finally {
            ParameterResearch.MAX_PROPOSALS_PER_RUN = originalLimit;
        }
    }

    @Test
    void normalizedAliasCollisionsCountTowardTheProposalCap() {
        // A normalizer that aliases every declared value onto one canonical
        // value turns each new raw proposal into a cache hit. Cache hits do
        // not consume the evaluation budget, so exempting them from the
        // proposal cap would let the grid walk the whole space — paying full
        // normalization and validation work per aliased raw id — while the
        // cap is supposed to bound that proposal work. Every proposal event,
        // new raw ids and raw re-proposals alike, charges the cap.
        int originalLimit = ParameterResearch.MAX_PROPOSALS_PER_RUN;
        ParameterResearch.MAX_PROPOSALS_PER_RUN = 50;
        try {
            BarSeries series = series(1d, 2d, 3d);
            ParameterNormalizer normalizer = (data, name, value) -> new ParameterValue(name, "1", true, "aliased");
            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .integer("a", 1, 100)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                            .of(window.series().numFactory().numOf(candidate)))
                    .normalize(normalizer)
                    .search(SearchPlan.grid(100))
                    .run();
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.PROPOSAL_LIMIT_EXCEEDED);
            assertThat(report.counts().proposed()).isEqualTo(50);
            assertThat(report.counts().attempted()).isEqualTo(1);
            assertThat(report.counts().duplicate()).isEqualTo(49);
        } finally {
            ParameterResearch.MAX_PROPOSALS_PER_RUN = originalLimit;
        }
    }

    @Test
    void wideDecimalDomainCanonicalizesScientificAndExactStringForms() {
        // A normalizer may emit the same declared value once in scientific
        // notation and once as an exact plain string. On a domain wide enough
        // to overflow double subtraction ([-Double.MAX_VALUE, Double.MAX_VALUE]
        // stepped by Double.MAX_VALUE), the position lookup must still resolve
        // both forms onto one canonical id: index arithmetic that overflows to
        // Long.MAX_VALUE wraps the position window and leaves the raw string
        // verbatim, evaluating the same declared position twice.
        BarSeries series = series(1d, 2d, 3d);
        String exactMax = BigDecimal.valueOf(Double.MAX_VALUE).toPlainString();
        ParameterNormalizer normalizer = (data, name, value) -> value.startsWith("-")
                ? new ParameterValue(name, "1.7976931348623157E308", true, "scientific")
                : new ParameterValue(name, exactMax, true, "exact");
        ParameterResearchReport report = ParameterResearch.<Double>builder(series)
                .decimal("a", -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
                .candidate((window, parameters) -> parameters.decimalValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize(normalizer)
                .search(SearchPlan.grid(3))
                .run();
        assertThat(report.counts().attempted()).isEqualTo(1);
        assertThat(report.counts().duplicate()).isEqualTo(2);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void doubleParseableDecimalFormsShareOneCanonicalIdentity() {
        // A normalizer may emit the same declared value in two spellings that
        // Double.parseDouble accepts but BigDecimal(String) rejects — here a
        // hex-float literal and its plain decimal spelling. Both must
        // canonicalize onto the declared id of 1.0, so the second proposal is
        // a cache hit instead of a second identity for the same numeric value.
        BarSeries series = series(1d, 2d, 3d);
        AtomicInteger proposals = new AtomicInteger();
        ParameterNormalizer normalizer = (data, name, value) -> proposals.getAndIncrement() == 0
                ? new ParameterValue(name, "0x1.0p0", true, "hex-float")
                : new ParameterValue(name, "1.0", true, "plain");
        ParameterResearchReport report = ParameterResearch.<Double>builder(series)
                .decimal("a", 0, 1, 1)
                .candidate((window, parameters) -> parameters.decimalValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize(normalizer)
                .search(SearchPlan.grid(2))
                .run();
        assertThat(report.counts().attempted()).isEqualTo(1);
        assertThat(report.counts().duplicate()).isEqualTo(1);
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.SEARCH_SPACE_EXHAUSTED);
    }

    @Test
    void objectiveIdIgnoresTargetScoreNumFactory() {
        // The objective fingerprint is value-based: a target score of 1 must
        // fingerprint identically whether it was built as DoubleNum (whose
        // toString is "1.0") or DecimalNum ("1"), so runs that differ only in
        // the target score's num factory share one objective id.
        BarSeries series = series(1d, 2d, 3d);
        String doubleNumId = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .targetScore(DoubleNum.valueOf(1))
                .search(SearchPlan.grid(2))
                .run()
                .objectiveId();
        String decimalNumId = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .targetScore(DecimalNum.valueOf(1))
                .search(SearchPlan.grid(2))
                .run()
                .objectiveId();
        assertThat(decimalNumId).isEqualTo(doubleNumId);
    }

    @Test
    void normalizerMutationOfWindowCapacityAbortsTheRun() {
        // Structural changes that SeriesSnapshot tracks must abort the run,
        // including maximum-bar-count changes: a normalizer can mutate the
        // window and reject the proposal, which would otherwise corrupt the
        // window silently for every later evaluation.
        BarSeries series = series(1d, 2d, 3d);
        final boolean[] mutated = { false };

        assertThrows(IllegalStateException.class,
                () -> ParameterResearch.<Integer>builder(series)
                        .integer("a", 1, 2)
                        .candidate((window, parameters) -> parameters.intValue("a"))
                        .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                                .of(window.series().numFactory().numOf(candidate)))
                        .normalize((data, name, value) -> {
                            if (!mutated[0]) {
                                mutated[0] = true;
                                data.setMaximumBarCount(100);
                            }
                            return new ParameterValue(name, value, false, "");
                        })
                        .search(SearchPlan.grid(2))
                        .run());
    }

    @Test
    void validatorMutationOfWindowCapacityAbortsTheRun() {
        // A validator can capture the window series through shared callback
        // state, structurally mutate it, and then reject the proposal. When
        // that lands on the final proposal, no later post-normalizer check
        // runs, so the rejection path must verify the window itself or the
        // run would complete with a silently corrupted window.
        BarSeries series = series(1d, 2d, 3d);
        final BarSeries[] stash = { null };
        final int[] calls = { 0 };

        assertThrows(IllegalStateException.class,
                () -> ParameterResearch.<Integer>builder(series)
                        .integer("a", 1, 2)
                        .candidate((window, parameters) -> parameters.intValue("a"))
                        .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                                .of(window.series().numFactory().numOf(candidate)))
                        .normalize((data, name, value) -> {
                            stash[0] = data;
                            return new ParameterValue(name, value, false, "");
                        })
                        .validate(parameters -> {
                            calls[0]++;
                            if (calls[0] == 2) {
                                stash[0].setMaximumBarCount(100);
                                throw new IllegalArgumentException("rejected after mutating the window");
                            }
                        })
                        .search(SearchPlan.grid(2))
                        .run());
    }

    @Test
    void validatorMutationOnCachedDuplicateAbortsTheRun() {
        // A validator can mutate the window through shared callback state and
        // return normally. When the normalized candidate is served from the
        // cache, no objective-side check runs; on the final proposal the run
        // would otherwise complete with a silently corrupted window.
        BarSeries series = series(1d, 2d, 3d);
        final BarSeries[] stash = { null };
        final int[] calls = { 0 };

        assertThrows(IllegalStateException.class,
                () -> ParameterResearch.<Integer>builder(series)
                        .integer("a", 1, 2)
                        .candidate((window, parameters) -> parameters.intValue("a"))
                        .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                                .of(window.series().numFactory().numOf(candidate)))
                        .normalize((data, name, value) -> {
                            stash[0] = data;
                            return new ParameterValue(name, "1", true, "canonical");
                        })
                        .validate(parameters -> {
                            calls[0]++;
                            if (calls[0] == 2) {
                                stash[0].setMaximumBarCount(100);
                            }
                        })
                        .search(SearchPlan.grid(2))
                        .run());
    }

    @Test
    void allInvalidInitialBatchStillExploresTheSpace() {
        // When every particle in the initial batch fails evaluation, no
        // validated personal or global best exists. The swarm must keep
        // exploring by resampling uniformly drawn grid points until every
        // grid point has been proposed, instead of freezing at the launch
        // positions (or being attracted toward the arbitrary fallback
        // gbestPosition).
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .decimal("a", 0d, 2d, 1d)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    throw new RuntimeException("fail every evaluation");
                })
                .search(SearchPlan.particleSwarm(20, 23L, new SwarmSettings(2, 0.5, 0.5, 1.49618, 0.2)))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(3);
    }

    @Test
    void noBestResamplingEscapesClampedLaunchCells() {
        // The no-best exploration reach must not scale with the velocity
        // clamp: with velocityClampFactor 1e-6 each velocity-scaled step is
        // at most 1e-4, so the swarm could never cross a projection boundary
        // within the 16-move stall limit and would terminate with unseen grid
        // points remaining. Resampling onto uniformly drawn grid points keeps
        // exploration independent of the damping knob, so every declared
        // point gets proposed.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 0, 100)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> {
                    throw new RuntimeException("fail every evaluation");
                })
                .search(SearchPlan.particleSwarm(1000, 23L, new SwarmSettings(2, 0.5, 0.5, 1.49618, 1e-6)))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(101);
    }

    @Test
    void collidingSwarmCountsEveryUpdateAgainstIterationTracker() {
        // With a tiny velocity clamp every swarm update moves the particles
        // by less than one grid step, so after the launch every batch fully
        // collides with already-proposed grid points and the stall loop moves
        // the swarm 16 more times before the fallthrough. Each of those
        // updates is an iteration: 1 launch generation + 1 leading move + 16
        // stall moves must all appear in iterationsCompleted, or a pinned
        // swarm understates the work it performed against the iteration cap.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 0, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.particleSwarm(100, 23L, new SwarmSettings(2, 0.5, 0.5, 1.49618, 1e-6)))
                .maxIterations(100)
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_IMPROVEMENT);
        assertThat(report.counts().iterationsCompleted()).isEqualTo(18);
    }

    @Test
    void holdoutCallbackMutationOfTrainingWindowAbortsTheRun() {
        // A stateful candidate factory that captures the training window's
        // series during the training invocation can mutate it during the
        // holdout invocation. The holdout rebuild must verify the training
        // window around every holdout callback, or the run silently returns a
        // structurally corrupted training window despite the stable-window
        // contract.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d);
        BarSeries[] captured = new BarSeries[1];
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate(new ParameterResearch.CandidateFactory<Integer>() {
                    @Override
                    public Integer build(ResearchWindow window, ParameterSet parameters) {
                        if (window.phase() == ResearchWindow.WindowPhase.TRAINING) {
                            captured[0] = window.series();
                        } else if (captured[0] != null) {
                            captured[0].setMaximumBarCount(100);
                        }
                        return parameters.intValue("a");
                    }
                })
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .holdoutBarCount(2)
                .search(SearchPlan.grid(2))
                .topK(1);

        assertThrows(IllegalStateException.class, builder::run);
    }

    @Test
    void callbackMutationOfTopKDoesNotAlterHoldoutReservation() {
        // A candidate factory that retains the builder can raise topK while the
        // training search is running. The holdout reservation is fixed when the
        // run starts, so the run must still score only the originally reserved
        // candidate and stay within the exact evaluation budget instead of
        // inflating holdout evaluations past maxEvaluations.
        BarSeries series = series(1d, 2d, 3d, 4d, 5d, 6d);
        ParameterResearch.CandidateStage<?>[] captured = new ParameterResearch.CandidateStage<?>[1];
        ParameterResearch.CandidateStage<Integer> builder = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 12)
                .candidate((window, parameters) -> {
                    captured[0].topK(9);
                    return parameters.intValue("a");
                })
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .holdoutBarCount(2)
                .topK(1)
                .search(SearchPlan.grid(10));
        captured[0] = builder;

        ParameterResearchReport report = builder.run();

        assertThat(report.counts().attempted()).isEqualTo(9);
        assertThat(report.counts().holdoutAttempted()).isEqualTo(1);
        assertThat(report.counts().budgetRemaining()).isZero();
        assertThat(report.topK()).isEqualTo(1);
        assertThat(report.holdoutLeaderboard()).hasSize(1);
    }

    @Test
    void crossFactoryScoresWithNonDecimalDelegateToStringsCompareExactly() {
        // A custom Num implementation is free to expose a Number delegate whose
        // toString() is not BigDecimal-compatible; cross-factory ranking must
        // compare through bigDecimalValue() instead of parsing delegate text.
        Num custom = new NonDecimalDelegateNum(DecimalNum.valueOf("2.5"));
        assertThat(ParameterResearch.compareScores(custom, DecimalNum.valueOf(2))).isPositive();
        assertThat(ParameterResearch.compareScores(custom, DecimalNum.valueOf(3))).isNegative();
        assertThat(ParameterResearch.compareScores(custom, DecimalNum.valueOf("2.50"))).isZero();
    }

    @Test
    void sameClassScoresFromForeignFactoriesCompareDecimally() {
        // NonDecimalDelegateNum.compareTo delegates to DecimalNum.compareTo,
        // which casts its argument to DecimalNum — two instances of the same
        // custom class therefore reject each other with a ClassCastException.
        // Ranking must compare through bigDecimalValue() instead, so same-class
        // cross-factory scores never abort a run.
        Num left = new NonDecimalDelegateNum(DecimalNum.valueOf("2.5"));
        Num right = new NonDecimalDelegateNum(DecimalNum.valueOf(2));
        assertThat(ParameterResearch.compareScores(left, right)).isPositive();
        assertThat(ParameterResearch.compareScores(right, left)).isNegative();
        assertThat(ParameterResearch.compareScores(left, new NonDecimalDelegateNum(DecimalNum.valueOf("2.50"))))
                .isZero();
    }

    @Test
    void interruptedRunFinalizesObservedBatch() {
        // Cancellation exits must finalize pending engine observations, or the
        // report undercounts completed iterations even when the whole batch
        // finished and the interrupt arrived before the next loop.
        BarSeries series = series(1d, 2d, 3d);
        try {
            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .decimal("a", 0, 100, 1)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> {
                        Thread.currentThread().interrupt();
                        return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                    })
                    .search(SearchPlan.genetic(1000, 7L, new ParameterResearch.GeneticSettings(4, 1, 2, 0.9, 0.1)))
                    .run();
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.CANCELED);
            assertThat(report.counts().iterationsCompleted()).isEqualTo(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void canceledRunCountsOnlyProcessedProposals() {
        // The interrupt flag must be observed before the next proposal is
        // counted, or a canceled batch overcounts proposals that were never
        // normalized, validated, or evaluated.
        BarSeries series = series(1d, 2d, 3d);
        try {
            ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                    .decimal("a", 0, 100, 1)
                    .candidate((window, parameters) -> parameters.intValue("a"))
                    .maximize((candidate, window) -> {
                        Thread.currentThread().interrupt();
                        return ParameterResearch.ObjectiveEvaluation.of(window.series().numFactory().numOf(candidate));
                    })
                    .search(SearchPlan.grid(100))
                    .run();
            assertThat(report.terminationReason()).isEqualTo(TerminationReason.CANCELED);
            assertThat(report.counts().attempted()).isEqualTo(1);
            assertThat(report.counts().proposed()).isEqualTo(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void researchWindowRejectsRangeThatOverflowsIntBarCount() {
        // An inclusive range spanning all integer indexes wraps int
        // subtraction to zero; validation must use long arithmetic and reject
        // it rather than accepting an empty series for a nonempty range.
        BarSeries empty = new MockBarSeriesBuilder().build();
        assertThrows(IllegalArgumentException.class, () -> new ParameterResearch.ResearchWindow(empty,
                Integer.MIN_VALUE, Integer.MAX_VALUE, ParameterResearch.ResearchWindow.WindowPhase.TRAINING, "w"));
    }

    @Test
    void repairedCandidatesShareCanonicalIdentity() {
        // Two proposals that repair to the same canonical value must dedupe
        // through the run cache: identities come from the normalized set, not
        // the raw proposal, or repaired duplicates would be double-counted.
        BarSeries series = series(1d, 2d, 3d);
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series)
                .integer("a", 1, 2)
                .candidate((window, parameters) -> parameters.intValue("a"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .normalize((data, name, value) -> new ParameterValue(name, "2", true, "clamped"))
                .search(SearchPlan.grid(2))
                .run();
        assertThat(report.counts().proposed()).isEqualTo(2);
        assertThat(report.counts().repaired()).isEqualTo(2);
        assertThat(report.counts().attempted()).isEqualTo(1);
        assertThat(report.counts().duplicate()).isEqualTo(1);
        assertThat(report.counts().successful()).isEqualTo(1);
        assertThat(report.trainingLeaderboard()).hasSize(1);
    }

    @Test
    void geneticCrossoverAtFullRateStillExploresBeyondParents() {
        // crossoverRate=1.0 must recombine via uniform parent-allele
        // selection, not clone the first parent: with mutation disabled a
        // full-rate search still evaluates genomes beyond the initial
        // population instead of stalling after its first generation.
        ParameterResearchReport report = ParameterResearch.<Integer>builder(series(1d, 2d, 3d))
                .integer("a", 1, 10)
                .integer("b", 1, 10)
                .candidate((window, parameters) -> parameters.intValue("a") + parameters.intValue("b"))
                .maximize((candidate, window) -> ParameterResearch.ObjectiveEvaluation
                        .of(window.series().numFactory().numOf(candidate)))
                .search(SearchPlan.genetic(40, 13L, new ParameterResearch.GeneticSettings(4, 0, 2, 1.0, 0.0)))
                .run();

        assertThat(report.counts().attempted()).isGreaterThan(4);
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

    /**
     * Test Num whose arithmetic and exact decimal value come from a wrapped
     * {@link DecimalNum}, but whose {@link #getDelegate()} formats as a non-decimal
     * token to prove ranking never parses delegate text.
     */
    private static final class NonDecimalDelegateNum implements Num {

        private static final long serialVersionUID = 1L;

        private final DecimalNum value;

        private NonDecimalDelegateNum(DecimalNum value) {
            this.value = value;
        }

        @Override
        public Number getDelegate() {
            BigDecimal delegate = value.getDelegate();
            return new Number() {
                private static final long serialVersionUID = 1L;

                @Override
                public int intValue() {
                    return delegate.intValue();
                }

                @Override
                public long longValue() {
                    return delegate.longValue();
                }

                @Override
                public float floatValue() {
                    return delegate.floatValue();
                }

                @Override
                public double doubleValue() {
                    return delegate.doubleValue();
                }

                @Override
                public String toString() {
                    return delegate.toPlainString() + "/delegate";
                }
            };
        }

        @Override
        public BigDecimal bigDecimalValue() {
            return value.bigDecimalValue();
        }

        @Override
        public String getName() {
            return "NonDecimalDelegateNum";
        }

        @Override
        public NumFactory getNumFactory() {
            return value.getNumFactory();
        }

        @Override
        public Num plus(Num augend) {
            return value.plus(augend);
        }

        @Override
        public Num minus(Num subtrahend) {
            return value.minus(subtrahend);
        }

        @Override
        public Num multipliedBy(Num multiplicand) {
            return value.multipliedBy(multiplicand);
        }

        @Override
        public Num dividedBy(Num divisor) {
            return value.dividedBy(divisor);
        }

        @Override
        public Num remainder(Num divisor) {
            return value.remainder(divisor);
        }

        @Override
        public Num floor() {
            return value.floor();
        }

        @Override
        public Num ceil() {
            return value.ceil();
        }

        @Override
        public Num pow(int n) {
            return value.pow(n);
        }

        @Override
        public Num pow(Num n) {
            return value.pow(n);
        }

        @Override
        public Num log() {
            return value.log();
        }

        @Override
        public Num exp() {
            return value.exp();
        }

        @Override
        public Num sqrt() {
            return value.sqrt();
        }

        @Override
        public Num sqrt(MathContext mathContext) {
            return value.sqrt(mathContext);
        }

        @Override
        public Num abs() {
            return value.abs();
        }

        @Override
        public Num negate() {
            return value.negate();
        }

        @Override
        public boolean isZero() {
            return value.isZero();
        }

        @Override
        public boolean isPositive() {
            return value.isPositive();
        }

        @Override
        public boolean isPositiveOrZero() {
            return value.isPositiveOrZero();
        }

        @Override
        public boolean isNegative() {
            return value.isNegative();
        }

        @Override
        public boolean isNegativeOrZero() {
            return value.isNegativeOrZero();
        }

        @Override
        public boolean isEqual(Num other) {
            return value.isEqual(other);
        }

        @Override
        public boolean isGreaterThan(Num other) {
            return value.isGreaterThan(other);
        }

        @Override
        public boolean isGreaterThanOrEqual(Num other) {
            return value.isGreaterThanOrEqual(other);
        }

        @Override
        public boolean isLessThan(Num other) {
            return value.isLessThan(other);
        }

        @Override
        public boolean isLessThanOrEqual(Num other) {
            return value.isLessThanOrEqual(other);
        }

        @Override
        public Num min(Num other) {
            return value.min(other);
        }

        @Override
        public Num max(Num other) {
            return value.max(other);
        }

        @Override
        public int compareTo(Num other) {
            return value.compareTo(other);
        }
    }
}
