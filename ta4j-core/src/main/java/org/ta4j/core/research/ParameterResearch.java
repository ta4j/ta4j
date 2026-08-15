/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Unified parameter search over a typed parameter space driven by a generic
 * objective function.
 *
 * <p>
 * The workflow owns, in order: ordered parameter declaration; candidate
 * construction and validation; objective and direction (maximize or minimize);
 * search plan and exact evaluation budget; training window and optional holdout
 * policy; top-K retention; and a final report. Search engines are deterministic
 * for identical code, data, configuration, and seed: grid search iterates the
 * Cartesian product in declaration order, while the genetic and particle-swarm
 * engines derive all randomness from the run-local seed.
 * </p>
 *
 * <p>
 * <b>Leakage boundaries.</b> Every evaluation receives a {@link ResearchWindow}
 * whose {@link ResearchWindow#series()} is a sub-series restricted to exactly
 * the window's bars, so candidate factories and objectives that build and score
 * exclusively from the supplied window cannot read outside it. The workflow
 * cannot enforce this for callbacks that capture the original series and ignore
 * the window argument. Training-window results select the top K; those
 * candidates are then rebuilt from scratch on the holdout window. Normalization
 * and cross-parameter validation happen before any objective invocation and do
 * not consume evaluation budget.
 * </p>
 *
 * <b>Budget semantics.</b> {@code maxEvaluations} is the exact budget of unique
 * objective evaluations across training and holdout: cache hits and duplicate
 * proposals never consume it, and the final generation or swarm batch is
 * truncated rather than allowed to exceed it. When a holdout window is
 * configured, {@code topK} evaluations are reserved for the holdout rebuild, so
 * the training search may attempt at most {@code maxEvaluations - topK} unique
 * evaluations and a budget that leaves no training evaluation fails fast with
 * an {@link IllegalArgumentException}. The report separates training attempts
 * from holdout attempts in {@link RunCounts#attempted()} and
 * {@link RunCounts#holdoutAttempted()}. A budget-limited grid run reports
 * {@link TerminationReason#EVALUATION_BUDGET_EXHAUSTED} and never claims
 * exhaustive optimality. Objective or candidate-factory failures that occur
 * after an attempted evaluation do consume budget and are ranked below every
 * valid evaluation.
 * </p>
 *
 * <p>
 * <b>Dataset revision contract.</b> The series name, begin index, end index,
 * and bar count are snapshotted when the run starts and verified before every
 * proposal batch and the holdout rebuild. A mismatch fails the run with an
 * {@link IllegalStateException} instead of silently evaluating candidates on
 * mutated data.
 * </p>
 *
 * @since 0.24.2
 */
public final class ParameterResearch {

    private static final int SHORT_HASH_LENGTH = 12;

    /**
     * Maximum number of evaluations a single run retains. Every evaluated candidate
     * is kept for the report (plus deduplication and cache bookkeeping), so
     * unbounded budgets would exhaust memory long before a huge space is searched;
     * budgets above this limit must be split into multiple runs.
     */
    static final int MAX_RETAINED_EVALUATIONS = 1_000_000;

    /**
     * Maximum number of proposals a single run processes before terminating.
     * Rejected proposals never consume the evaluation budget, so a rejection-heavy
     * normalizer over a large space would otherwise make genetic or particle-swarm
     * engines propose (and retain proposal ids) without bound. Non-final so tests
     * can exercise the bound cheaply.
     */
    static int MAX_PROPOSALS_PER_RUN = 1_000_000;

    private ParameterResearch() {
    }

    /**
     * Starts a parameter research workflow for the supplied series.
     *
     * @param series dataset to search over
     * @param <T>    candidate type, inferred from {@link Builder#candidate}
     * @return workflow builder
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code series} does not report its bar
     *                                  history revision via
     *                                  {@link BarSeries#getBarHistoryRevision()}
     * @since 0.24.2
     */
    public static <T> Builder<T> builder(BarSeries series) {
        BarSeries validated = Objects.requireNonNull(series, "series");
        if (validated.getBarHistoryRevision() < 0L) {
            throw new IllegalArgumentException("series must report a non-negative bar history revision via "
                    + "getBarHistoryRevision() so dataset mutations are detectable");
        }
        return new Builder<>(validated);
    }

    /**
     * Optimization direction of the objective.
     *
     * @since 0.24.2
     */
    public enum Direction {
        /** Higher scores are better. */
        MAXIMIZE,
        /** Lower scores are better. */
        MINIMIZE
    }

    /**
     * Fluent workflow builder.
     *
     * @param <T> candidate type, bound by {@link #candidate}
     * @since 0.24.2
     */
    public static final class Builder<T> {

        private final BarSeries series;
        private final List<ParameterDomain> domains = new ArrayList<>();
        private CandidateFactory<T> candidateFactory;
        private ObjectiveFunction<T> objective;
        private Direction direction;
        private SearchPlan searchPlan;
        private double holdoutFraction = Double.NaN;
        private int holdoutBarCount = -1;
        private int topK = 10;
        private CandidateValidator validator = CandidateValidator.acceptAll();
        private ParameterNormalizer normalizer;
        private Num targetScore;
        private Integer maxIterations;
        private Integer noImprovementIterations;

        private Builder(BarSeries series) {
            this.series = series;
        }

        /**
         * Declares an ordered integer domain.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @return this builder
         * @throws IllegalArgumentException if the domain is invalid or the name is
         *                                  already declared
         * @since 0.24.2
         */
        public Builder<T> integer(String name, int from, int to) {
            return integer(name, from, to, 1);
        }

        /**
         * Declares an ordered integer domain with a positive step.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @return this builder
         * @throws IllegalArgumentException if the domain is invalid or the name is
         *                                  already declared
         * @since 0.24.2
         */
        public Builder<T> integer(String name, int from, int to, int step) {
            return domain(ParameterDomain.integer(name, from, to, step));
        }

        /**
         * Declares an ordered decimal domain.
         *
         * <p>
         * Declared positions are generated from {@code from + index * step} and mapped
         * to their canonical {@code double} representation. When {@code step} is below
         * the ULP of the largest magnitude in the range, consecutive positions can
         * collapse to the same {@code double}; the collapsed positions are consolidated
         * into the distinct canonical values, and the effective cardinality is reported
         * as that distinct count. Consolidation begins below one full ULP because steps
         * between half and one ULP can still merge pairs of positions.
         * </p>
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @return this builder
         * @throws IllegalArgumentException if the domain is invalid, the name is
         *                                  already declared, or more than
         *                                  {@code 100_000} positions collapse to fewer
         *                                  distinct values (verification would be
         *                                  prohibitively expensive)
         * @since 0.24.2
         */
        public Builder<T> decimal(String name, double from, double to, double step) {
            return domain(ParameterDomain.decimal(name, from, to, step));
        }

        /**
         * Declares a Boolean domain whose canonical values are {@code "false"} and
         * {@code "true"}, in that order.
         *
         * @param name parameter name
         * @return this builder
         * @throws IllegalArgumentException if the name is already declared
         * @since 0.24.2
         */
        public Builder<T> bool(String name) {
            return domain(ParameterDomain.bool(name));
        }

        /**
         * Declares a categorical domain over ordered literal values.
         *
         * @param name   parameter name
         * @param values ordered categorical values
         * @return this builder
         * @throws IllegalArgumentException if the domain is invalid or the name is
         *                                  already declared
         * @since 0.24.2
         */
        public Builder<T> categorical(String name, String... values) {
            return domain(ParameterDomain.categorical(name, values));
        }

        /**
         * Declares a parameter domain.
         *
         * @param domain parameter domain
         * @return this builder
         * @throws IllegalArgumentException if the name is already declared
         * @since 0.24.2
         */
        public Builder<T> domain(ParameterDomain domain) {
            Objects.requireNonNull(domain, "domain");
            for (ParameterDomain declared : domains) {
                if (declared.name().equals(domain.name())) {
                    throw new IllegalArgumentException("Duplicate parameter domain name: " + domain.name());
                }
            }
            domains.add(domain);
            return this;
        }

        /**
         * Binds the candidate factory and re-binds the builder's candidate type to the
         * factory result type.
         *
         * @param factory builds a candidate from a window and a normalized parameter
         *                set
         * @param <U>     candidate type
         * @return this builder with {@code U} as its candidate type
         * @throws NullPointerException  if {@code factory} is null
         * @throws IllegalStateException if the objective is already configured, because
         *                               the candidate type must be declared first, or
         *                               if a candidate factory was already bound,
         *                               because re-binding would leave previously
         *                               returned builder references with a stale
         *                               candidate type
         * @since 0.24.2
         */
        @SuppressWarnings("unchecked")
        public <U> Builder<U> candidate(CandidateFactory<U> factory) {
            if (objective != null) {
                throw new IllegalStateException("candidate(...) must be declared before maximize(...)/minimize(...) "
                        + "so the candidate type cannot diverge from the objective");
            }
            if (candidateFactory != null) {
                throw new IllegalStateException("candidate(...) was already bound: re-binding the candidate type on "
                        + "the same builder would leave previously returned builder references with a stale type");
            }
            this.candidateFactory = (CandidateFactory<T>) (CandidateFactory<?>) Objects.requireNonNull(factory,
                    "factory");
            return (Builder<U>) this;
        }

        /**
         * Sets the objective with maximization direction.
         *
         * @param objective objective function
         * @return this builder
         * @throws NullPointerException if {@code objective} is null
         * @since 0.24.2
         */
        public Builder<T> maximize(ObjectiveFunction<T> objective) {
            this.objective = Objects.requireNonNull(objective, "objective");
            this.direction = Direction.MAXIMIZE;
            return this;
        }

        /**
         * Sets the objective with minimization direction.
         *
         * @param objective objective function
         * @return this builder
         * @throws NullPointerException if {@code objective} is null
         * @since 0.24.2
         */
        public Builder<T> minimize(ObjectiveFunction<T> objective) {
            this.objective = Objects.requireNonNull(objective, "objective");
            this.direction = Direction.MINIMIZE;
            return this;
        }

        /**
         * Sets the search plan (engine kind, exact budget, seed, settings).
         *
         * @param plan search plan
         * @return this builder
         * @throws NullPointerException if {@code plan} is null
         * @since 0.24.2
         */
        public Builder<T> search(SearchPlan plan) {
            this.searchPlan = Objects.requireNonNull(plan, "plan");
            return this;
        }

        /**
         * Holds out the given fraction of the final bars for validation.
         *
         * @param fraction holdout fraction in {@code (0, 1)}
         * @return this builder
         * @throws IllegalArgumentException if the fraction is out of range or a holdout
         *                                  bar count was already set
         * @since 0.24.2
         */
        public Builder<T> holdoutFraction(double fraction) {
            if (!(fraction > 0d && fraction < 1d) || Double.isNaN(fraction)) {
                throw new IllegalArgumentException("holdoutFraction must be in (0, 1), but was " + fraction);
            }
            if (holdoutBarCount >= 0) {
                throw new IllegalArgumentException("set either holdoutFraction or holdoutBarCount, not both");
            }
            this.holdoutFraction = fraction;
            return this;
        }

        /**
         * Holds out the given number of final bars for validation.
         *
         * @param barCount number of final bars held out
         * @return this builder
         * @throws IllegalArgumentException if the count is not positive or a holdout
         *                                  fraction was already set
         * @since 0.24.2
         */
        public Builder<T> holdoutBarCount(int barCount) {
            if (barCount <= 0) {
                throw new IllegalArgumentException("holdoutBarCount must be > 0, but was " + barCount);
            }
            if (!Double.isNaN(holdoutFraction)) {
                throw new IllegalArgumentException("set either holdoutFraction or holdoutBarCount, not both");
            }
            this.holdoutBarCount = barCount;
            return this;
        }

        /**
         * Sets how many top training candidates are retained and rebuilt on the holdout
         * window.
         *
         * @param topK number of retained candidates
         * @return this builder
         * @throws IllegalArgumentException if {@code topK <= 0}
         * @since 0.24.2
         */
        public Builder<T> topK(int topK) {
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be > 0, but was " + topK);
            }
            this.topK = topK;
            return this;
        }

        /**
         * Sets the cross-parameter validator applied to every normalized proposal
         * before evaluation.
         *
         * @param validator cross-parameter validator
         * @return this builder
         * @throws NullPointerException if {@code validator} is null
         * @since 0.24.2
         */
        public Builder<T> validate(CandidateValidator validator) {
            this.validator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        /**
         * Sets the optional value normalizer applied to every proposed value before
         * validation.
         *
         * @param normalizer value normalizer
         * @return this builder
         * @throws NullPointerException if {@code normalizer} is null
         * @since 0.24.2
         */
        public Builder<T> normalize(ParameterNormalizer normalizer) {
            this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
            return this;
        }

        /**
         * Sets an optional target score that terminates the run as soon as a valid
         * evaluation reaches it.
         *
         * <p>
         * Comparison is factory-independent and precision-preserving: the target is
         * compared against each evaluated score with exact decimal semantics, so a tiny
         * or high-precision target is never rounded through the score's numeric
         * factory.
         * </p>
         *
         * @param targetScore objective value considered good enough
         * @return this builder
         * @throws NullPointerException     if {@code targetScore} is null
         * @throws IllegalArgumentException if {@code targetScore} is not finite
         * @since 0.24.2
         */
        public Builder<T> targetScore(Num targetScore) {
            this.targetScore = Objects.requireNonNull(targetScore, "targetScore");
            if (!Num.isFinite(targetScore)) {
                throw new IllegalArgumentException("targetScore must be finite, but was " + targetScore);
            }
            return this;
        }

        /**
         * Caps the number of iterations (generations or swarm updates) for iterative
         * plans.
         *
         * @param iterations maximum iterations
         * @return this builder
         * @throws IllegalArgumentException if {@code iterations <= 0}
         * @since 0.24.2
         */
        public Builder<T> maxIterations(int iterations) {
            if (iterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be > 0, but was " + iterations);
            }
            this.maxIterations = iterations;
            return this;
        }

        /**
         * Terminates iterative plans when the best score has not improved for the given
         * number of iterations.
         *
         * @param iterations stagnation tolerance
         * @return this builder
         * @throws IllegalArgumentException if {@code iterations <= 0}
         * @since 0.24.2
         */
        public Builder<T> noImprovementIterations(int iterations) {
            if (iterations <= 0) {
                throw new IllegalArgumentException("noImprovementIterations must be > 0, but was " + iterations);
            }
            this.noImprovementIterations = iterations;
            return this;
        }

        /**
         * Runs the search and returns the final report.
         *
         * @return final research report
         * @throws IllegalStateException    if required configuration is missing, the
         *                                  series is empty, or the dataset changed
         *                                  during the run
         * @throws IllegalArgumentException if the holdout window leaves no training
         *                                  bar, the topK holdout reservation leaves no
         *                                  training evaluation budget, or the selected
         *                                  engine rejects a declared domain
         * @since 0.24.2
         */
        public ParameterResearchReport run() {
            if (domains.isEmpty()) {
                throw new IllegalStateException("at least one parameter domain is required");
            }
            if (candidateFactory == null) {
                throw new IllegalStateException("candidate(...) is required before run()");
            }
            if (objective == null) {
                throw new IllegalStateException("maximize(...) or minimize(...) is required before run()");
            }
            if (searchPlan == null) {
                throw new IllegalStateException("search(...) is required before run()");
            }
            SeriesSnapshot snapshot = new SeriesSnapshot(series);
            if (snapshot.barCount() <= 0) {
                throw new IllegalStateException("series has no bars");
            }
            int holdoutBars = resolveHoldoutBars(snapshot.barCount());
            String datasetId = resolveDatasetId(series);
            ResearchWindow trainingWindow = buildWindow(datasetId, ResearchWindow.WindowPhase.TRAINING,
                    snapshot.beginIndex(), snapshot.endIndex() - holdoutBars);
            ResearchWindow holdoutWindow = holdoutBars > 0
                    ? buildWindow(datasetId, ResearchWindow.WindowPhase.HOLDOUT, snapshot.endIndex() - holdoutBars + 1,
                            snapshot.endIndex())
                    : null;
            BarSeries normalizerData = trainingWindow.series();
            SeriesSnapshot trainingSnapshot = new SeriesSnapshot(trainingWindow.series());

            String objectiveId = computeObjectiveId(holdoutBars);
            Comparator<EvaluatedCandidate> ranking = rankingComparator(direction);
            List<DomainSpec> specs = new ArrayList<>(domains.size());
            for (ParameterDomain domain : domains) {
                specs.add(DomainSpec.of(domain));
            }
            SearchEngine engine = createEngine(specs, ranking, direction);

            int budget = searchPlan.maxEvaluations();
            // Holdout rebuilds score up to topK retained candidates, so those
            // evaluations are reserved out of the budget; the training search
            // never spends them, keeping total objective calls budget-exact.
            int trainingBudget = holdoutBars > 0 ? budget - topK : budget;
            if (trainingBudget <= 0) {
                throw new IllegalArgumentException("search budget of " + budget + " evaluations must exceed the topK "
                        + topK + " holdout reservation; raise maxEvaluations or lower topK");
            }
            EvaluationCache cache = new EvaluationCache();
            List<EvaluatedCandidate> evaluations = new ArrayList<>();
            List<FailedEvaluation> failures = new ArrayList<>();
            RunCounters counters = new RunCounters();
            long orchestrationStart = System.nanoTime();
            long evaluationNanos = 0L;
            TerminationReason reason = null;
            boolean targetReached = false;
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    engine.finalizeObserved();
                    reason = TerminationReason.CANCELED;
                    break;
                }
                verifyUnchanged(snapshot, series);
                long allowance = MAX_PROPOSALS_PER_RUN - counters.proposed;
                if (allowance <= 0) {
                    engine.terminate(TerminationReason.PROPOSAL_LIMIT_EXCEEDED);
                    engine.finalizeObserved();
                    reason = TerminationReason.PROPOSAL_LIMIT_EXCEEDED;
                    break;
                }
                int remaining = trainingBudget - (int) counters.attempted;
                List<ParameterSet> batch = engine.propose(Math.min(remaining, (int) allowance));
                if (batch.size() > allowance) {
                    // Engines honor the request count, but keep the cap
                    // airtight against batch-size granularity: never process
                    // or retain proposals beyond the run-wide allowance.
                    batch = batch.subList(0, (int) allowance);
                }
                if (batch.isEmpty()) {
                    reason = engine.terminationReason();
                    if (reason == null) {
                        reason = counters.attempted >= trainingBudget ? TerminationReason.EVALUATION_BUDGET_EXHAUSTED
                                : TerminationReason.SEARCH_SPACE_EXHAUSTED;
                    }
                    break;
                }
                for (ParameterSet proposed : batch) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    counters.proposed++;
                    ParameterSet normalized = normalizeProposal(proposed, normalizerData);
                    verifyUnchanged(trainingSnapshot, trainingWindow.series());
                    if (normalized == null) {
                        counters.rejected++;
                        continue;
                    }
                    if (!normalized.repairs().isEmpty()) {
                        counters.repaired++;
                    }
                    try {
                        validator.validate(normalized);
                    } catch (RuntimeException ex) {
                        counters.rejected++;
                        verifyUnchanged(trainingSnapshot, trainingWindow.series());
                        continue;
                    }
                    verifyUnchanged(trainingSnapshot, trainingWindow.series());
                    String candidateId = normalized.stableId();
                    EvaluatedCandidate cached = cache.get(new CacheKey(candidateId, null));
                    if (cached != null) {
                        counters.duplicate++;
                        counters.cached++;
                        engine.observe(proposed.stableId(), cached);
                        continue;
                    }
                    counters.attempted++;
                    long evaluationStart = System.nanoTime();
                    EvaluatedCandidate evaluated;
                    try {
                        T candidate = candidateFactory.build(trainingWindow, normalized);
                        ObjectiveEvaluation outcome = objective.evaluate(candidate, trainingWindow);
                        evaluated = classify(candidateId, normalized, (int) counters.attempted, outcome);
                    } catch (RuntimeException ex) {
                        evaluated = EvaluatedCandidate.failed(candidateId, normalized, (int) counters.attempted,
                                "evaluation threw " + ex.getClass().getSimpleName() + message(ex), Map.of());
                    }
                    evaluationNanos += System.nanoTime() - evaluationStart;
                    verifyUnchanged(trainingSnapshot, trainingWindow.series());

                    cache.put(new CacheKey(candidateId, null), evaluated);
                    engine.observe(proposed.stableId(), evaluated);
                    evaluations.add(evaluated);
                    if (evaluated.valid()) {
                        counters.successful++;
                    } else {
                        counters.failed++;
                        failures.add(evaluated.toFailedEvaluation());
                    }
                    if (evaluated.valid() && targetScore != null && reachedTarget(evaluated.score())) {
                        targetReached = true;
                        engine.finalizeObserved();
                        break;
                    }

                }
                if (Thread.currentThread().isInterrupted()) {
                    engine.finalizeObserved();
                    reason = TerminationReason.CANCELED;
                    break;
                }
                if (targetReached) {
                    reason = TerminationReason.TARGET_SCORE_REACHED;
                    break;
                }
                if (engine.terminationReason() != null) {
                    engine.finalizeObserved();
                    reason = engine.terminationReason();
                    break;
                }

                if (engine.exhausted()) {
                    engine.finalizeObserved();
                    reason = TerminationReason.SEARCH_SPACE_EXHAUSTED;
                    break;
                }

                if (counters.proposed >= MAX_PROPOSALS_PER_RUN) {
                    engine.terminate(TerminationReason.PROPOSAL_LIMIT_EXCEEDED);
                    engine.finalizeObserved();
                    reason = TerminationReason.PROPOSAL_LIMIT_EXCEEDED;
                    break;
                }
            }
            verifyUnchanged(snapshot, series);
            if (counters.successful == 0 && reason != TerminationReason.CANCELED) {
                reason = TerminationReason.NO_VALID_CANDIDATES;
            }
            List<EvaluatedCandidate> ranked = evaluations.stream()
                    .filter(EvaluatedCandidate::valid)
                    .sorted(ranking)
                    .toList();
            int leaderboardSize = Math.min(topK, ranked.size());
            List<RankedCandidate> holdoutLeaderboard = List.of();
            Map<String, HoldoutEvaluation> holdoutById = Map.of();
            if (holdoutWindow != null && !ranked.isEmpty()) {
                verifyUnchanged(snapshot, series);
                HoldoutResult holdout = rebuildOnHoldout(ranked, leaderboardSize, holdoutWindow, trainingWindow,
                        ranking, cache, failures);
                holdoutLeaderboard = holdout.leaderboard();
                holdoutById = holdout.byId();
                evaluationNanos += holdout.evaluationNanos();
                counters.holdoutAttempted += holdout.attempted();
                if (Thread.currentThread().isInterrupted()) {
                    reason = TerminationReason.CANCELED;
                }
            }
            verifyUnchanged(snapshot, series);
            List<RankedCandidate> trainingLeaderboard = new ArrayList<>(leaderboardSize);
            for (int i = 0; i < leaderboardSize; i++) {
                EvaluatedCandidate evaluated = ranked.get(i);
                HoldoutEvaluation holdout = holdoutById.get(evaluated.candidateId());
                Integer holdoutRank = holdout == null ? null : holdout.holdoutRank();
                Num holdoutScore = holdout == null ? null : holdout.evaluation().score();
                Num scoreDelta = holdout == null ? null : subtractScores(holdoutScore, evaluated.score());
                trainingLeaderboard.add(new RankedCandidate(evaluated.candidateId(), evaluated.parameters(), i + 1,
                        holdoutRank, evaluated.score(), holdoutScore, scoreDelta, evaluated.metrics(),
                        holdout == null ? Map.of() : holdout.evaluation().metrics()));
            }
            List<String> warnings = new ArrayList<>();
            if (topK > ranked.size()) {
                warnings.add("topK " + topK + " exceeds the " + ranked.size()
                        + " valid evaluations; leaderboard contains " + ranked.size() + " candidates");
            }
            RunCounts counts = new RunCounts(counters.proposed, counters.rejected, counters.repaired,
                    counters.duplicate, counters.cached, counters.attempted, counters.holdoutAttempted,
                    counters.successful, counters.failed,
                    budget - (int) counters.attempted - (int) counters.holdoutAttempted, engine.iterationsCompleted());
            long orchestrationNanos = System.nanoTime() - orchestrationStart - evaluationNanos;
            return new ParameterResearchReport(datasetId, searchPlan, objectiveId, trainingWindow,
                    Optional.ofNullable(holdoutWindow), topK, trainingLeaderboard, holdoutLeaderboard, reason, counts,
                    failures, evaluationNanos, orchestrationNanos, warnings);

        }

        private int resolveHoldoutBars(int totalBars) {
            int holdoutBars = 0;
            if (!Double.isNaN(holdoutFraction)) {
                holdoutBars = Math.max(1, (int) Math.round(totalBars * holdoutFraction));
            } else if (holdoutBarCount >= 0) {
                holdoutBars = holdoutBarCount;
            }
            if (holdoutBars > 0 && holdoutBars >= totalBars) {
                throw new IllegalArgumentException("holdout window of " + holdoutBars
                        + " bars must leave at least one training bar of " + totalBars);
            }
            return holdoutBars;
        }

        private ResearchWindow buildWindow(String datasetId, ResearchWindow.WindowPhase phase, int start, int end) {
            String windowId = datasetId + "|" + phase.name().toLowerCase(Locale.ROOT) + "|" + start + "|" + end;
            // Build the owned window directly from the inclusive source range:
            // getSubSeries needs an exclusive end index, which overflows when
            // the window legally ends at Integer.MAX_VALUE. Like getSubSeries,
            // the rebuilt series is re-based to index zero; the original
            // absolute range stays on the ResearchWindow for reporting. Bars
            // are frozen copies with their mutators disabled so a mutating
            // objective cannot corrupt the original data unseen by the
            // revision checks. The loop breaks once the terminal index is
            // copied so the increment can never wrap past Integer.MAX_VALUE.
            long windowLength = (long) end - start + 1;
            List<Bar> bars = new ArrayList<>((int) Math.min(windowLength, series.getBarCount()));
            for (int i = start; i <= end; i++) {
                bars.add(new UnmodifiableBar(series.getBar(i)));
                if (i == end) {
                    break;
                }
            }
            BarSeries window = new BaseBarSeriesBuilder().withName(series.getName())
                    .withNumFactory(series.numFactory())
                    .withMaxBarCount(series.getMaximumBarCount())
                    .withBeginIndex(0)
                    .withBars(bars)
                    .build();
            return new ResearchWindow(window, start, end, phase, windowId);
        }

        private ParameterSet normalizeProposal(ParameterSet proposed, BarSeries normalizerData) {
            if (normalizer == null) {
                return proposed;
            }
            List<ParameterValue> values = new ArrayList<>(proposed.values().size());
            for (ParameterValue value : proposed.values()) {
                ParameterValue normalized;
                try {
                    normalized = normalizer.normalize(normalizerData, value.name(), value.value());
                } catch (RuntimeException ex) {
                    return null;
                }
                // A normalizer must not change parameter names: renaming would
                // alias or duplicate declared parameters, so it rejects the
                // proposal like any other normalization failure.
                if (normalized == null || !value.name().equals(normalized.name())) {
                    return null;
                }
                values.add(normalized);
            }
            try {
                return new ParameterSet(values);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private EvaluatedCandidate classify(String candidateId, ParameterSet parameters, int ordinal,
                ObjectiveEvaluation outcome) {
            if (outcome.status() == ObjectiveEvaluation.Status.FAILED) {
                String reason = outcome.failureReason().isBlank() ? "objective declared failure"
                        : outcome.failureReason();
                return EvaluatedCandidate.failed(candidateId, parameters, ordinal, reason, outcome.metrics());
            }
            Num score = outcome.score();
            if (!Num.isFinite(score)) {
                return EvaluatedCandidate.failed(candidateId, parameters, ordinal, "objective score is not finite",
                        Map.of());
            }
            return EvaluatedCandidate.valid(candidateId, parameters, ordinal, score, outcome.metrics());
        }

        private SearchEngine createEngine(List<DomainSpec> specs, Comparator<EvaluatedCandidate> ranking,
                Direction direction) {
            if (specs.isEmpty()) {
                throw new IllegalArgumentException("at least one parameter domain is required");
            }
            int maxIter = maxIterations == null ? -1 : maxIterations;
            int noImprovement = noImprovementIterations == null ? -1 : noImprovementIterations;
            return switch (searchPlan.kind()) {
            case GRID -> new GridSearchEngine(specs);
            case GENETIC -> new GeneticSearchEngine(specs, searchPlan.geneticSettings(), new Random(searchPlan.seed()),
                    ranking, direction, maxIter, noImprovement);
            case PARTICLE_SWARM -> new ParticleSwarmEngine(specs, searchPlan.swarmSettings(),
                    new Random(searchPlan.seed()), ranking, direction, maxIter, noImprovement);
            };
        }

        private boolean reachedTarget(Num score) {
            if (!Num.isFinite(score)) {
                return false;
            }
            // compareScores stays precision-preserving across factories, so a
            // tiny decimal target is never narrowed to zero (or a large one to
            // infinity) by a narrower score factory.
            int comparison = compareScores(score, targetScore);
            return direction == Direction.MAXIMIZE ? comparison >= 0 : comparison <= 0;
        }

        private HoldoutResult rebuildOnHoldout(List<EvaluatedCandidate> ranked, int leaderboardSize,
                ResearchWindow holdoutWindow, ResearchWindow trainingWindow, Comparator<EvaluatedCandidate> ranking,
                EvaluationCache cache, List<FailedEvaluation> failures) {
            List<HoldoutEvaluation> holdoutEvaluations = new ArrayList<>(leaderboardSize);
            SeriesSnapshot holdoutSnapshot = new SeriesSnapshot(holdoutWindow.series());
            // A stateful candidate factory may have captured the training
            // window's series during the training invocation and can mutate it
            // during a holdout callback, so the training window is re-checked
            // around every holdout invocation like the holdout window itself.
            SeriesSnapshot trainingSnapshot = new SeriesSnapshot(trainingWindow.series());

            Map<String, HoldoutEvaluation> byId = new LinkedHashMap<>();
            long evaluationNanos = 0L;
            long attempted = 0L;
            for (int i = 0; i < leaderboardSize; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                EvaluatedCandidate training = ranked.get(i);
                CacheKey key = new CacheKey(training.candidateId(), holdoutWindow.windowId());
                EvaluatedCandidate cached = cache.get(key);
                EvaluatedCandidate holdout = cached;
                if (cached == null) {
                    long evaluationStart = System.nanoTime();
                    try {
                        T candidate = candidateFactory.build(holdoutWindow, training.parameters());
                        ObjectiveEvaluation outcome = objective.evaluate(candidate, holdoutWindow);
                        holdout = classify(training.candidateId(), training.parameters(), i + 1, outcome);
                    } catch (RuntimeException ex) {
                        holdout = EvaluatedCandidate.failed(training.candidateId(), training.parameters(), i + 1,
                                "holdout evaluation threw " + ex.getClass().getSimpleName() + message(ex), Map.of());

                    }
                    verifyUnchanged(holdoutSnapshot, holdoutWindow.series());
                    verifyUnchanged(trainingSnapshot, trainingWindow.series());

                    evaluationNanos += System.nanoTime() - evaluationStart;
                    cache.put(key, holdout);
                    attempted++;
                }
                if (!holdout.valid() && cached == null) {
                    failures.add(new FailedEvaluation(holdout.candidateId(), holdout.parameters(),
                            "holdout: " + holdout.failureReason(), holdout.metrics()));
                }
                if (holdout.valid()) {
                    holdoutEvaluations.add(new HoldoutEvaluation(training, holdout, i + 1, 0));
                }
            }
            holdoutEvaluations.sort((a, b) -> ranking.compare(a.evaluation(), b.evaluation()));
            List<RankedCandidate> leaderboard = new ArrayList<>(holdoutEvaluations.size());
            int rank = 1;
            for (HoldoutEvaluation evaluation : holdoutEvaluations) {
                RankedCandidate row = new RankedCandidate(evaluation.evaluation().candidateId(),
                        evaluation.evaluation().parameters(), evaluation.trainingRank(), rank,
                        evaluation.training().score(), evaluation.evaluation().score(),
                        subtractScores(evaluation.evaluation().score(), evaluation.training().score()),
                        evaluation.training().metrics(), evaluation.evaluation().metrics());
                leaderboard.add(row);
                byId.put(row.candidateId(), new HoldoutEvaluation(evaluation.training(), evaluation.evaluation(),
                        evaluation.trainingRank(), rank));
                rank++;
            }
            return new HoldoutResult(leaderboard, byId, evaluationNanos, attempted);
        }

        /**
         * Computes a deterministic objective fingerprint from the value-based
         * configuration: declared domains, plan kind, budget, seed, top-K, holdout
         * size, target score, iteration limits, and engine settings.
         *
         * <p>
         * Candidate factories, objectives, validators, and normalizers are lambdas
         * without identity and are intentionally excluded.
         * </p>
         */
        private String computeObjectiveId(int holdoutBars) {
            StringJoiner joiner = new StringJoiner("|");
            joiner.add(direction.name());
            for (ParameterDomain domain : domains) {
                joiner.add(domainSpec(domain));
            }
            joiner.add(searchPlan.kind().name())
                    .add(String.valueOf(searchPlan.maxEvaluations()))
                    .add(String.valueOf(searchPlan.seed()))
                    .add(String.valueOf(topK))
                    .add(String.valueOf(holdoutBars))
                    .add(String.valueOf(targetScore))
                    .add(String.valueOf(maxIterations))
                    .add(String.valueOf(noImprovementIterations));
            if (searchPlan.kind() == SearchPlan.Kind.GENETIC) {
                joiner.add(String.valueOf(searchPlan.geneticSettings()));
            } else if (searchPlan.kind() == SearchPlan.Kind.PARTICLE_SWARM) {
                joiner.add(String.valueOf(searchPlan.swarmSettings()));
            }
            return shortHash(joiner.toString());
        }
    }

    /**
     * Builds a candidate for one window from a normalized parameter set.
     *
     * @param <T> candidate type
     * @since 0.24.2
     */
    @FunctionalInterface
    public interface CandidateFactory<T> {

        /**
         * Builds a candidate restricted to the supplied window.
         *
         * @param window     evaluation window
         * @param parameters normalized parameter set
         * @return candidate to evaluate
         * @since 0.24.2
         */
        T build(ResearchWindow window, ParameterSet parameters);
    }

    /**
     * Scores one candidate on one window.
     *
     * @param <T> candidate type
     * @since 0.24.2
     */
    @FunctionalInterface
    public interface ObjectiveFunction<T> {

        /**
         * Evaluates a candidate on the supplied window.
         *
         * <p>
         * A {@link RuntimeException} thrown from this method is captured as a failed
         * evaluation and consumes budget. The same applies to {@link #evaluate}
         * returning a {@link ObjectiveEvaluation} whose score is NaN or infinite, or
         * whose status is {@link ObjectiveEvaluation.Status#FAILED}.
         * </p>
         *
         * @param candidate candidate to evaluate
         * @param window    evaluation window
         * @return evaluation outcome
         * @since 0.24.2
         */
        ObjectiveEvaluation evaluate(T candidate, ResearchWindow window);
    }

    /**
     * One objective evaluation outcome.
     *
     * @param score         objective score; required when the status is
     *                      {@link Status#VALID}
     * @param metrics       optional auxiliary metrics keyed by name
     * @param status        validity status
     * @param failureReason factual failure reason when the status is
     *                      {@link Status#FAILED}
     * @since 0.24.2
     */
    public record ObjectiveEvaluation(Num score, Map<String, Num> metrics, Status status, String failureReason) {

        /**
         * Validity status of an objective evaluation.
         *
         * @since 0.24.2
         */
        public enum Status {
            /** Score is usable for ranking. */
            VALID,
            /** Evaluation is invalid and ranked below every valid evaluation. */
            FAILED
        }

        /**
         * Creates a validated evaluation outcome.
         *
         * @throws NullPointerException if {@code status} is null, or the status is
         *                              {@link Status#VALID} and {@code score} is null
         * @since 0.24.2
         */
        public ObjectiveEvaluation {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            failureReason = failureReason == null ? "" : failureReason;
            Objects.requireNonNull(status, "status");
            if (status == Status.VALID) {
                Objects.requireNonNull(score, "score");
            }
        }

        /**
         * Creates a valid evaluation with a score only.
         *
         * @param score objective score
         * @return valid evaluation
         * @since 0.24.2
         */
        public static ObjectiveEvaluation of(Num score) {
            return of(score, Map.of());
        }

        /**
         * Creates a valid evaluation with auxiliary metrics.
         *
         * @param score   objective score
         * @param metrics auxiliary metrics
         * @return valid evaluation
         * @since 0.24.2
         */
        public static ObjectiveEvaluation of(Num score, Map<String, Num> metrics) {
            return new ObjectiveEvaluation(score, metrics, Status.VALID, "");
        }

        /**
         * Creates a failed evaluation with a factual reason.
         *
         * @param reason failure reason
         * @return failed evaluation
         * @since 0.24.2
         */
        public static ObjectiveEvaluation failed(String reason) {
            return failed(reason, Map.of());
        }

        /**
         * Creates a failed evaluation with a factual reason and metrics.
         *
         * @param reason  failure reason
         * @param metrics auxiliary metrics
         * @return failed evaluation
         * @since 0.24.2
         */
        public static ObjectiveEvaluation failed(String reason, Map<String, Num> metrics) {
            return new ObjectiveEvaluation(null, metrics, Status.FAILED, Objects.requireNonNull(reason, "reason"));
        }
    }

    /**
     * Cross-parameter validation callback.
     *
     * @since 0.24.2
     */
    @FunctionalInterface
    public interface CandidateValidator {

        /**
         * Accepts or rejects one normalized parameter set by throwing a
         * {@link RuntimeException} with a factual reason.
         *
         * @param parameters normalized parameter set
         * @since 0.24.2
         */
        void validate(ParameterSet parameters);

        /**
         * Returns a validator that accepts every parameter set.
         *
         * @return no-op validator
         * @since 0.24.2
         */
        static CandidateValidator acceptAll() {
            return parameters -> {
                // no-op
            };
        }
    }

    /**
     * Normalizes one proposed parameter value.
     *
     * @since 0.24.2
     */
    @FunctionalInterface
    public interface ParameterNormalizer {

        /**
         * Normalizes one proposed value.
         *
         * <p>
         * A {@link RuntimeException} thrown from this method rejects the whole proposal
         * without consuming evaluation budget, as does returning {@code null} or a
         * {@link ParameterValue} whose {@link ParameterValue#name()} differs from
         * {@code name}. Returning a {@link ParameterValue} whose
         * {@link ParameterValue#normalized()} is {@code true} records a repair. Cache
         * identity and the reported {@link RankedCandidate#candidateId()} are derived
         * from the normalized values, while {@link RankedCandidate#parameters()}
         * carries them too; the raw proposal identity is only used internally to attach
         * the outcome back to the engine that proposed it. Distinct raw proposals that
         * repair to the same canonical values therefore collapse into a single
         * candidate: the first evaluation wins and later proposals are served from the
         * cache. Repaired candidates still rank below unrepaired candidates with equal
         * scores, and the repair itself is reported through
         * {@link ParameterValue#normalized()} and {@link ParameterSet#repairs()}.
         * </p>
         *
         * @param series dataset being searched, limited to the training window when
         *               holdout validation is configured
         * @param name   parameter name
         * @param value  proposed canonical value
         * @return normalized value
         * @since 0.24.2
         */
        ParameterValue normalize(BarSeries series, String name, String value);
    }

    /**
     * Typed parameter domain for candidate-space generation.
     *
     * @since 0.24.2
     */
    public sealed interface ParameterDomain permits ParameterDomain.IntegerDomain, ParameterDomain.DecimalDomain,
            ParameterDomain.BooleanDomain, ParameterDomain.CategoricalDomain {

        /**
         * @return parameter name
         * @since 0.24.2
         */
        String name();

        /**
         * Creates an inclusive integer domain with unit step.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @return integer domain
         * @throws IllegalArgumentException if the range is invalid
         * @since 0.24.2
         */
        static IntegerDomain integer(String name, int from, int to) {
            return integer(name, from, to, 1);
        }

        /**
         * Creates an inclusive integer domain.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @return integer domain
         * @throws IllegalArgumentException if the range is invalid
         * @since 0.24.2
         */
        static IntegerDomain integer(String name, int from, int to, int step) {
            return new IntegerDomain(name, from, to, step);
        }

        /**
         * Creates an inclusive decimal domain.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @return decimal domain
         * @throws IllegalArgumentException if the range is invalid
         * @since 0.24.2
         */
        static DecimalDomain decimal(String name, double from, double to, double step) {
            return new DecimalDomain(name, from, to, step);
        }

        /**
         * Creates a Boolean domain.
         *
         * @param name parameter name
         * @return Boolean domain
         * @since 0.24.2
         */
        static BooleanDomain bool(String name) {
            return new BooleanDomain(name);
        }

        /**
         * Creates a categorical domain.
         *
         * @param name   parameter name
         * @param values ordered categorical values
         * @return categorical domain
         * @throws IllegalArgumentException if the values are invalid
         * @since 0.24.2
         */
        static CategoricalDomain categorical(String name, String... values) {
            return new CategoricalDomain(name, List.of(values));
        }

        /**
         * Creates a categorical domain.
         *
         * @param name   parameter name
         * @param values ordered categorical values
         * @return categorical domain
         * @throws IllegalArgumentException if the values are invalid
         * @since 0.24.2
         */
        static CategoricalDomain categorical(String name, List<String> values) {
            return new CategoricalDomain(name, values);
        }

        /**
         * Ordered inclusive integer domain.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @since 0.24.2
         */
        record IntegerDomain(String name, int from, int to, int step) implements ParameterDomain {

            /**
             * Creates a validated integer domain.
             *
             * @throws IllegalArgumentException if the name is blank, the step is not
             *                                  positive, or {@code from > to}
             * @since 0.24.2
             */
            public IntegerDomain {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("name cannot be blank");
                }
                if (step <= 0) {
                    throw new IllegalArgumentException("step must be positive");
                }
                if (from > to) {
                    throw new IllegalArgumentException("from cannot be greater than to");
                }
            }
        }

        /**
         * Ordered inclusive decimal domain.
         *
         * @param name parameter name
         * @param from inclusive lower bound
         * @param to   inclusive upper bound
         * @param step positive increment
         * @since 0.24.2
         */
        record DecimalDomain(String name, double from, double to, double step) implements ParameterDomain {

            /**
             * Creates a validated decimal domain.
             *
             * @throws IllegalArgumentException if the name is blank, any bound is not
             *                                  finite, the step is not positive, or
             *                                  {@code from > to}
             * @since 0.24.2
             */
            public DecimalDomain {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("name cannot be blank");
                }
                if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(step)) {
                    throw new IllegalArgumentException("decimal domain bounds must be finite");
                }
                if (step <= 0d) {
                    throw new IllegalArgumentException("step must be positive");
                }
                if (from > to) {
                    throw new IllegalArgumentException("from cannot be greater than to");
                }
            }
        }

        /**
         * Boolean domain with canonical values {@code "false"} and {@code "true"}.
         *
         * @param name parameter name
         * @since 0.24.2
         */
        record BooleanDomain(String name) implements ParameterDomain {

            /**
             * Creates a validated Boolean domain.
             *
             * @throws IllegalArgumentException if the name is blank
             * @since 0.24.2
             */
            public BooleanDomain {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("name cannot be blank");
                }
            }
        }

        /**
         * Categorical domain over ordered literal values.
         *
         * @param name   parameter name
         * @param values ordered categorical values
         * @since 0.24.2
         */
        record CategoricalDomain(String name, List<String> values) implements ParameterDomain {

            /**
             * Creates a validated categorical domain.
             *
             * @throws IllegalArgumentException if the name is blank or the values are empty
             *                                  or contain blank entries or duplicate
             *                                  literals
             * @since 0.24.2
             */
            public CategoricalDomain {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("name cannot be blank");
                }
                if (values == null || values.isEmpty()) {
                    throw new IllegalArgumentException("values cannot be empty");
                }
                for (String value : values) {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException("values cannot contain blank entries");
                    }
                }
                if (new LinkedHashSet<>(values).size() != values.size()) {
                    throw new IllegalArgumentException("values cannot contain duplicates");
                }
                values = List.copyOf(values);
            }
        }
    }

    /**
     * One canonical parameter value.
     *
     * @param name       parameter name
     * @param value      canonical value
     * @param normalized whether a normalizer repaired the proposed value
     * @param note       repair note
     * @since 0.24.2
     */
    public record ParameterValue(String name, String value, boolean normalized, String note) {

        /**
         * Creates a validated parameter value.
         *
         * @throws IllegalArgumentException if the name or value is blank
         * @since 0.24.2
         */
        public ParameterValue {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value cannot be blank");
            }
            note = note == null ? "" : note;
        }
    }

    /**
     * Escapes the stable-id separators so canonical tokens are unambiguous.
     */
    static String escapeToken(String token) {
        StringBuilder builder = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            switch (c) {
            case '\\' -> builder.append("\\\\");
            case '|' -> builder.append("\\|");
            case '=' -> builder.append("\\=");
            default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * Canonical {@code name=value} token shared by proposal registration
     * ({@link ParameterSet#stableId()}) and engine lookups
     * ({@link SearchEngine#canonicalId(int[])}); both formats must stay
     * byte-identical.
     *
     * @param name  escaped parameter name
     * @param value escaped canonical value
     * @return escaped name-value token
     */
    static String canonicalToken(String name, String value) {
        return escapeToken(name) + "=" + escapeToken(value);
    }

    /**
     * Ordered normalized parameter set.
     *
     * @param values canonical values in declaration order
     * @since 0.24.2
     */
    public record ParameterSet(List<ParameterValue> values) {

        /**
         * Creates a validated parameter set.
         *
         * @throws IllegalArgumentException if the values are empty or contain duplicate
         *                                  names
         * @since 0.24.2
         */
        public ParameterSet {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.isEmpty()) {
                throw new IllegalArgumentException("values cannot be empty");
            }
            Set<String> names = new LinkedHashSet<>();
            for (ParameterValue value : values) {
                Objects.requireNonNull(value, "values cannot contain null entries");
                if (!names.add(value.name())) {
                    throw new IllegalArgumentException("Duplicate parameter name: " + value.name());
                }
            }
        }

        /**
         * Returns the canonical value of a parameter.
         *
         * @param name parameter name
         * @return canonical value
         * @throws IllegalArgumentException if the parameter is unknown
         * @since 0.24.2
         */
        public String value(String name) {
            for (ParameterValue value : values) {
                if (value.name().equals(name)) {
                    return value.value();
                }
            }
            throw new IllegalArgumentException("Unknown parameter: " + name);
        }

        /**
         * Returns a parameter value parsed as an integer.
         *
         * @param name parameter name
         * @return integer value
         * @throws IllegalArgumentException if the parameter is unknown or not an
         *                                  integer
         * @since 0.24.2
         */
        public int intValue(String name) {
            String canonical = value(name);
            try {
                return Integer.parseInt(canonical);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Parameter '" + name + "' is not an integer: " + canonical, ex);
            }
        }

        /**
         * Returns a parameter value parsed as a decimal.
         *
         * @param name parameter name
         * @return decimal value
         * @throws IllegalArgumentException if the parameter is unknown or not a decimal
         * @since 0.24.2
         */
        public double decimalValue(String name) {
            String canonical = value(name);
            try {
                return Double.parseDouble(canonical);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Parameter '" + name + "' is not a decimal: " + canonical, ex);
            }
        }

        /**
         * Returns a parameter value parsed as a Boolean.
         *
         * @param name parameter name
         * @return Boolean value
         * @throws IllegalArgumentException if the parameter is unknown or neither
         *                                  {@code "true"} nor {@code "false"}
         * @since 0.24.2
         */
        public boolean booleanValue(String name) {
            String canonical = value(name);
            if ("true".equals(canonical)) {
                return true;
            }
            if ("false".equals(canonical)) {
                return false;
            }
            throw new IllegalArgumentException("Parameter '" + name + "' is not a boolean: " + canonical);
        }

        /**
         * Returns a parameter value as its categorical literal.
         *
         * @param name parameter name
         * @return categorical value
         * @throws IllegalArgumentException if the parameter is unknown
         * @since 0.24.2
         */
        public String categoricalValue(String name) {
            return value(name);
        }

        /**
         * Returns the number of repaired (normalized) values in this set.
         *
         * @return repair count
         * @since 0.24.2
         */
        public int repairCount() {
            int count = 0;
            for (ParameterValue value : values) {
                if (value.normalized()) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Returns the repaired values keyed by name.
         *
         * @return unmodifiable repair map, empty when nothing was repaired
         * @since 0.24.2
         */
        public Map<String, String> repairs() {
            Map<String, String> repairs = new LinkedHashMap<>();
            for (ParameterValue value : values) {
                if (value.normalized()) {
                    repairs.put(value.name(), value.note());
                }
            }
            return Collections.unmodifiableMap(repairs);
        }

        /**
         * Returns a stable candidate identifier based on canonical values.
         *
         * <p>
         * Name/value tokens are escaped so ids are unambiguous and collision-free even
         * when values contain the separators ({@code |}, {@code =}).
         * </p>
         *
         * @return stable id
         * @since 0.24.2
         */
        public String stableId() {
            StringJoiner joiner = new StringJoiner("|");
            for (ParameterValue value : values) {
                joiner.add(ParameterResearch.canonicalToken(value.name(), value.value()));
            }
            return joiner.toString();
        }

    }

    /**
     * Evaluation window over one dataset.
     *
     * <p>
     * The window series is a fresh {@link BaseBarSeries} backed by read-only bar
     * wrappers: objectives receive the same bars across evaluations, and any
     * attempt to mutate a bar fails the evaluation instead of corrupting the source
     * series.
     * </p>
     *
     * @param series     series restricted to exactly this window's bars, backed by
     *                   read-only bar wrappers
     * @param startIndex inclusive start index on the original series
     * @param endIndex   inclusive end index on the original series
     * @param phase      window phase
     * @param windowId   stable window identifier
     * @since 0.24.2
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "the window series is a fresh "
            + "per-window series created solely for objective access; the workflow never mutates it")
    public record ResearchWindow(BarSeries series, int startIndex, int endIndex, WindowPhase phase, String windowId) {

        /**
         * Window phase.
         *
         * @since 0.24.2
         */
        public enum WindowPhase {
            /** Candidate selection window. */
            TRAINING,
            /** Independent validation window. */
            HOLDOUT,
            /** Reserved for walk-forward folds; not produced by the current workflow. */
            FOLD
        }

        /**
         * Creates a validated research window.
         *
         * @throws NullPointerException     if {@code series}, {@code phase}, or
         *                                  {@code windowId} is null
         * @throws IllegalArgumentException if the indexes are inverted, the windowId is
         *                                  blank, or the sub-series bar count does not
         *                                  match the index range
         * @since 0.24.2
         */
        public ResearchWindow {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(phase, "phase");
            if (windowId == null || windowId.isBlank()) {
                throw new IllegalArgumentException("windowId cannot be blank");
            }
            if (startIndex > endIndex) {
                throw new IllegalArgumentException("startIndex cannot be greater than endIndex");
            }
            long range = (long) endIndex - (long) startIndex + 1L;
            if (series.getBarCount() != range) {
                throw new IllegalArgumentException("series bar count must match the window index range, but was "
                        + series.getBarCount() + " for [" + startIndex + ", " + endIndex + "]");
            }
        }

        /**
         * @return number of bars in the window
         * @since 0.24.2
         */
        public int barCount() {
            return (int) ((long) endIndex - startIndex + 1L);
        }
    }

    /**
     * Search plan: engine kind, exact evaluation budget, seed, and engine settings.
     *
     * @param maxEvaluations  exact budget of unique objective evaluations across
     *                        training and holdout; must not exceed
     *                        {@link #MAX_RETAINED_EVALUATIONS} because every
     *                        evaluation is retained for the report. A configured
     *                        holdout window reserves {@code topK} of the budget for
     *                        the holdout rebuild at run time
     * @param seed            run-local seed for stochastic engines; ignored by grid
     *                        search
     * @param geneticSettings genetic algorithm settings; required for
     *                        {@link Kind#GENETIC}
     * @param swarmSettings   particle-swarm settings; required for
     *                        {@link Kind#PARTICLE_SWARM}
     * @since 0.24.2
     */
    public record SearchPlan(Kind kind, int maxEvaluations, long seed, GeneticSettings geneticSettings,
            SwarmSettings swarmSettings) {

        /**
         * Engine kind.
         *
         * @since 0.24.2
         */
        public enum Kind {
            /** Deterministic Cartesian-product search. */
            GRID,
            /** Genetic algorithm search. */
            GENETIC,
            /** Particle-swarm search over bounded numeric dimensions. */
            PARTICLE_SWARM
        }

        /**
         * Creates a validated search plan.
         *
         * @throws NullPointerException     if {@code kind} is null, or a required
         *                                  settings record is missing
         * @throws IllegalArgumentException if {@code maxEvaluations <= 0}, or if it
         *                                  exceeds {@link #MAX_RETAINED_EVALUATIONS}
         * @since 0.24.2
         */
        public SearchPlan {
            Objects.requireNonNull(kind, "kind");
            if (kind == Kind.GENETIC) {
                Objects.requireNonNull(geneticSettings, "geneticSettings are required for a genetic search plan");
            } else if (kind == Kind.PARTICLE_SWARM) {
                Objects.requireNonNull(swarmSettings, "swarmSettings are required for a particle-swarm search plan");
            }
            if (maxEvaluations <= 0) {
                throw new IllegalArgumentException("maxEvaluations must be > 0");
            }
            if (maxEvaluations > MAX_RETAINED_EVALUATIONS) {
                throw new IllegalArgumentException("search plan may evaluate up to " + maxEvaluations
                        + " candidates, which exceeds the retained-evaluation limit of " + MAX_RETAINED_EVALUATIONS
                        + "; every evaluated candidate is retained for the report, so split the search into"
                        + " multiple runs instead of raising the budget");
            }
        }

        /**
         * Creates a grid plan.
         *
         * @param maxEvaluations exact evaluation budget
         * @return grid plan
         * @since 0.24.2
         */
        public static SearchPlan grid(int maxEvaluations) {
            return new SearchPlan(Kind.GRID, maxEvaluations, 0L, null, null);
        }

        /**
         * Creates a genetic plan with default settings.
         *
         * @param maxEvaluations exact evaluation budget
         * @param seed           run-local seed
         * @return genetic plan
         * @since 0.24.2
         */
        public static SearchPlan genetic(int maxEvaluations, long seed) {
            return new SearchPlan(Kind.GENETIC, maxEvaluations, seed, GeneticSettings.defaults(), null);
        }

        /**
         * Creates a genetic plan with explicit settings.
         *
         * @param maxEvaluations exact evaluation budget
         * @param seed           run-local seed
         * @param settings       genetic algorithm settings
         * @return genetic plan
         * @since 0.24.2
         */
        public static SearchPlan genetic(int maxEvaluations, long seed, GeneticSettings settings) {
            return new SearchPlan(Kind.GENETIC, maxEvaluations, seed, settings, null);
        }

        /**
         * Creates a particle-swarm plan with default settings.
         *
         * @param maxEvaluations exact evaluation budget
         * @param seed           run-local seed
         * @return particle-swarm plan
         * @since 0.24.2
         */
        public static SearchPlan particleSwarm(int maxEvaluations, long seed) {
            return new SearchPlan(Kind.PARTICLE_SWARM, maxEvaluations, seed, null, SwarmSettings.defaults());
        }

        /**
         * Creates a particle-swarm plan with explicit settings.
         *
         * @param maxEvaluations exact evaluation budget
         * @param seed           run-local seed
         * @param settings       particle-swarm settings
         * @return particle-swarm plan
         * @since 0.24.2
         */
        public static SearchPlan particleSwarm(int maxEvaluations, long seed, SwarmSettings settings) {
            return new SearchPlan(Kind.PARTICLE_SWARM, maxEvaluations, seed, null, settings);
        }
    }

    /**
     * Genetic algorithm settings.
     *
     * @param populationSize number of individuals per generation
     * @param elitismCount   number of best individuals copied unchanged
     * @param tournamentSize tournament selection size for parent choice
     * @param crossoverRate  probability that each dimension is recombined; crossed
     *                       dimensions take their allele from either parent with
     *                       equal probability, while uncrossed dimensions inherit
     *                       the first parent's allele
     * @param mutationRate   per-dimension mutation probability
     * @since 0.24.2
     */
    public record GeneticSettings(int populationSize, int elitismCount, int tournamentSize, double crossoverRate,
            double mutationRate) {

        /**
         * Creates validated genetic settings.
         *
         * @throws IllegalArgumentException if any constraint is violated
         * @since 0.24.2
         */
        public GeneticSettings {
            if (populationSize < 2) {
                throw new IllegalArgumentException("populationSize must be >= 2");
            }
            if (populationSize > SearchEngine.MAX_COHORT_SIZE) {
                throw new IllegalArgumentException("populationSize must be <= " + SearchEngine.MAX_COHORT_SIZE);
            }
            if (elitismCount < 0 || elitismCount >= populationSize) {
                throw new IllegalArgumentException("elitismCount must be in [0, populationSize)");
            }
            if (tournamentSize < 2 || tournamentSize > populationSize) {
                throw new IllegalArgumentException("tournamentSize must be in [2, populationSize]");
            }
            if (!Double.isFinite(crossoverRate) || crossoverRate < 0d || crossoverRate > 1d) {
                throw new IllegalArgumentException("crossoverRate must be in [0, 1]");
            }
            if (!Double.isFinite(mutationRate) || mutationRate < 0d || mutationRate > 1d) {
                throw new IllegalArgumentException("mutationRate must be in [0, 1]");
            }
        }

        /**
         * Returns conservative defaults: population 50, elitism 2, tournament 5,
         * crossover 0.9, mutation 0.1.
         *
         * @return default settings
         * @since 0.24.2
         */
        public static GeneticSettings defaults() {
            return new GeneticSettings(50, 2, 5, 0.9, 0.1);
        }
    }

    /**
     * Particle-swarm settings.
     *
     * @param swarmSize           number of particles
     * @param inertiaWeight       velocity retention weight
     * @param cognitiveWeight     personal-best attraction weight
     * @param socialWeight        global-best attraction weight
     * @param velocityClampFactor velocity bound as a fraction of the dimension
     *                            range
     * @since 0.24.2
     */
    public record SwarmSettings(int swarmSize, double inertiaWeight, double cognitiveWeight, double socialWeight,
            double velocityClampFactor) {

        /**
         * Creates validated swarm settings.
         *
         * @throws IllegalArgumentException if any constraint is violated
         * @since 0.24.2
         */
        public SwarmSettings {
            if (swarmSize < 2) {
                throw new IllegalArgumentException("swarmSize must be >= 2");
            }
            if (swarmSize > SearchEngine.MAX_COHORT_SIZE) {
                throw new IllegalArgumentException("swarmSize must be <= " + SearchEngine.MAX_COHORT_SIZE);
            }
            if (!Double.isFinite(inertiaWeight) || inertiaWeight < 0d || inertiaWeight > 1d) {
                throw new IllegalArgumentException("inertiaWeight must be in [0, 1]");
            }
            if (!Double.isFinite(cognitiveWeight) || cognitiveWeight < 0d) {
                throw new IllegalArgumentException("cognitiveWeight must be >= 0");
            }
            if (!Double.isFinite(socialWeight) || socialWeight < 0d) {
                throw new IllegalArgumentException("socialWeight must be >= 0");
            }
            if (!Double.isFinite(velocityClampFactor) || velocityClampFactor <= 0d) {
                throw new IllegalArgumentException("velocityClampFactor must be > 0");
            }
        }

        /**
         * Returns the classic Clerc-Kennedy defaults: swarm 50, inertia 0.7298,
         * cognitive and social weights 1.49618, velocity clamp factor 0.2.
         *
         * @return default settings
         * @since 0.24.2
         */
        public static SwarmSettings defaults() {
            return new SwarmSettings(50, 0.7298, 1.49618, 1.49618, 0.2);
        }
    }

    /**
     * Reason a search terminated.
     *
     * @since 0.24.2
     */
    public enum TerminationReason {
        /** Grid search iterated the entire declared space. */
        SEARCH_SPACE_EXHAUSTED,
        /** The exact evaluation budget was consumed before the space was exhausted. */
        EVALUATION_BUDGET_EXHAUSTED,
        /** The configured iteration limit was reached. */
        ITERATION_LIMIT,
        /**
         * The run processed {@code MAX_PROPOSALS_PER_RUN} proposals — mostly rejected,
         * since rejected proposals never consume the evaluation budget — before the
         * space or budget could end the search.
         */
        PROPOSAL_LIMIT_EXCEEDED,
        /** A valid evaluation reached the configured target score. */
        TARGET_SCORE_REACHED,
        /**
         * The best score stagnated for the configured number of iterations, or the
         * engine could no longer propose a candidate that was not already proposed.
         */
        NO_IMPROVEMENT,
        /**
         * The running thread was interrupted between proposal batches or candidate
         * evaluations.
         */
        CANCELED,
        /** The search completed without a single valid evaluation. */
        NO_VALID_CANDIDATES
    }

    /**
     * Run-level counts.
     *
     * @param proposed            proposals processed
     * @param rejected            proposals rejected by the normalizer or validator
     * @param repaired            proposals with at least one repaired value
     * @param duplicate           duplicate proposals seen again after evaluation
     * @param cached              evaluation-side cache hits
     * @param attempted           unique training-window objective evaluations
     * @param holdoutAttempted    holdout-window objective evaluations; 0 when no
     *                            holdout window is configured
     * @param successful          evaluations with a valid score
     * @param failed              training evaluations with an invalid score;
     *                            holdout failures are excluded and reported via
     *                            {@link ParameterResearchReport#failedEvaluations()}
     * @param budgetRemaining     unused evaluation budget across training and
     *                            holdout
     * @param iterationsCompleted completed engine iterations (0 for grid search)
     * @since 0.24.2
     */
    public record RunCounts(long proposed, long rejected, long repaired, long duplicate, long cached, long attempted,
            long holdoutAttempted, long successful, long failed, int budgetRemaining, int iterationsCompleted) {
    }

    /**
     * One ranked candidate on the training and holdout leaderboards.
     *
     * @param candidateId     stable candidate id
     * @param parameters      normalized parameter set
     * @param trainingRank    1-based training leaderboard rank
     * @param holdoutRank     1-based holdout leaderboard rank, or {@code null} when
     *                        the candidate is not on the holdout leaderboard
     * @param trainingScore   training objective score
     * @param holdoutScore    holdout objective score, or {@code null} when absent
     * @param scoreDelta      {@code holdoutScore - trainingScore}, or {@code null}
     *                        when absent
     * @param trainingMetrics training auxiliary metrics
     * @param holdoutMetrics  holdout auxiliary metrics, empty when absent
     * @since 0.24.2
     */
    public record RankedCandidate(String candidateId, ParameterSet parameters, int trainingRank, Integer holdoutRank,
            Num trainingScore, Num holdoutScore, Num scoreDelta, Map<String, Num> trainingMetrics,
            Map<String, Num> holdoutMetrics) {

        /**
         * Creates a validated ranked candidate with defensive metric copies.
         *
         * @since 0.24.2
         */
        public RankedCandidate {
            Objects.requireNonNull(candidateId, "candidateId");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(trainingScore, "trainingScore");
            trainingMetrics = trainingMetrics == null ? Map.of() : Map.copyOf(trainingMetrics);
            holdoutMetrics = holdoutMetrics == null ? Map.of() : Map.copyOf(holdoutMetrics);
        }
    }

    /**
     * One invalid evaluation retained in the report diagnostics.
     *
     * @param candidateId stable candidate id
     * @param parameters  normalized parameter set
     * @param reason      factual failure reason
     * @param metrics     metrics reported by the objective, or empty when none were
     *                    reported before the failure
     * @since 0.24.2
     */
    public record FailedEvaluation(String candidateId, ParameterSet parameters, String reason,
            Map<String, Num> metrics) {

        /**
         * Creates a validated failed-evaluation row with defensive copies.
         *
         * @since 0.24.2
         */
        public FailedEvaluation {
            if (candidateId == null || candidateId.isBlank()) {
                throw new IllegalArgumentException("candidateId cannot be blank");
            }
            Objects.requireNonNull(parameters, "parameters");
            reason = reason == null ? "" : reason;
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        }
    }

    /**
     * Final parameter research report.
     *
     * @param datasetId                 dataset identifier
     * @param searchPlan                executed search plan
     * @param objectiveId               deterministic fingerprint of the objective
     *                                  configuration derived from value-based
     *                                  settings; lambda-based factories are
     *                                  excluded
     * @param trainingWindow            training window
     * @param holdoutWindow             holdout window, or empty when no holdout was
     *                                  configured
     * @param topK                      requested leaderboard size
     * @param trainingLeaderboard       ranked training candidates, at most
     *                                  {@code topK}
     * @param holdoutLeaderboard        independently ranked holdout candidates
     * @param terminationReason         why the search terminated
     * @param counts                    run-level counts
     * @param failedEvaluations         invalid evaluations retained for
     *                                  diagnostics, including holdout-phase
     *                                  failures
     * @param elapsedEvaluationNanos    nanoseconds spent inside candidate factories
     *                                  and objectives
     * @param elapsedOrchestrationNanos nanoseconds spent on generation, validation,
     *                                  ranking, and holdout orchestration
     * @param warnings                  run warnings
     * @since 0.24.2
     */
    public record ParameterResearchReport(String datasetId, SearchPlan searchPlan, String objectiveId,
            ResearchWindow trainingWindow, Optional<ResearchWindow> holdoutWindow, int topK,
            List<RankedCandidate> trainingLeaderboard, List<RankedCandidate> holdoutLeaderboard,
            TerminationReason terminationReason, RunCounts counts, List<FailedEvaluation> failedEvaluations,
            long elapsedEvaluationNanos, long elapsedOrchestrationNanos, List<String> warnings) {

        /**
         * Creates a validated report with defensive copies.
         *
         * @since 0.24.2
         */
        public ParameterResearchReport {
            if (datasetId == null || datasetId.isBlank()) {
                throw new IllegalArgumentException("datasetId cannot be blank");
            }
            Objects.requireNonNull(searchPlan, "searchPlan");
            if (objectiveId == null || objectiveId.isBlank()) {
                throw new IllegalArgumentException("objectiveId cannot be blank");
            }
            Objects.requireNonNull(trainingWindow, "trainingWindow");
            holdoutWindow = holdoutWindow == null ? Optional.empty() : holdoutWindow;
            Objects.requireNonNull(terminationReason, "terminationReason");
            Objects.requireNonNull(counts, "counts");
            trainingLeaderboard = List.copyOf(trainingLeaderboard);
            holdoutLeaderboard = List.copyOf(holdoutLeaderboard);
            failedEvaluations = List.copyOf(failedEvaluations);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * One evaluated candidate internal to the pipeline.
     */
    record EvaluatedCandidate(String candidateId, ParameterSet parameters, int evaluationOrdinal, Num score,
            Map<String, Num> metrics, boolean valid, String failureReason) {

        static EvaluatedCandidate valid(String candidateId, ParameterSet parameters, int ordinal, Num score,
                Map<String, Num> metrics) {
            return new EvaluatedCandidate(candidateId, parameters, ordinal, score, metrics, true, "");
        }

        static EvaluatedCandidate failed(String candidateId, ParameterSet parameters, int ordinal, String reason,
                Map<String, Num> metrics) {
            return new EvaluatedCandidate(candidateId, parameters, ordinal, null, metrics, false, reason);
        }

        FailedEvaluation toFailedEvaluation() {
            return new FailedEvaluation(candidateId, parameters, failureReason, metrics);
        }
    }

    /**
     * One holdout evaluation with its source training evaluation, training rank,
     * and holdout rank.
     */
    private record HoldoutEvaluation(EvaluatedCandidate training, EvaluatedCandidate evaluation, int trainingRank,
            int holdoutRank) {
    }

    /**
     * Holdout rebuild outcome.
     */
    private record HoldoutResult(List<RankedCandidate> leaderboard, Map<String, HoldoutEvaluation> byId,
            long evaluationNanos, long attempted) {
    }

    /**
     * Cache entry key. Structured instead of concatenated so candidate IDs and
     * window IDs cannot alias across the delimiter: training entries use a null
     * window ID, holdout entries use the window ID.
     */
    private record CacheKey(String candidateId, String windowId) {
    }

    /**
     * Run-local evaluation cache.
     */
    private static final class EvaluationCache {

        private final Map<CacheKey, EvaluatedCandidate> entries = new LinkedHashMap<>();

        EvaluatedCandidate get(CacheKey key) {
            return entries.get(key);
        }

        void put(CacheKey key, EvaluatedCandidate evaluated) {
            entries.put(key, evaluated);
        }
    }

    /**
     * Frozen bar copy whose mutators are disabled. Research windows own one per
     * source bar, copying the scalar values up front: objectives that mutate
     * {@code window.series()} bars fail with an
     * {@link UnsupportedOperationException} (captured as a failed evaluation), and
     * later mutations of the caller's original series cannot leak into an
     * already-built window because the shared bar objects are never referenced. The
     * retained {@link Num} instances are immutable by contract, so sharing them
     * does not alias the source bar.
     */
    private static final class UnmodifiableBar implements Bar {

        private static final long serialVersionUID = 1L;

        private final Duration timePeriod;
        private final Instant beginTime;
        private final Instant endTime;
        private final Num openPrice;
        private final Num highPrice;
        private final Num lowPrice;
        private final Num closePrice;
        private final Num volume;
        private final Num amount;
        private final long trades;

        private UnmodifiableBar(Bar source) {
            Objects.requireNonNull(source, "source");
            this.timePeriod = source.getTimePeriod();
            this.beginTime = source.getBeginTime();
            this.endTime = source.getEndTime();
            this.openPrice = source.getOpenPrice();
            this.highPrice = source.getHighPrice();
            this.lowPrice = source.getLowPrice();
            this.closePrice = source.getClosePrice();
            this.volume = source.getVolume();
            this.amount = source.getAmount();
            this.trades = source.getTrades();
        }

        @Override
        public Duration getTimePeriod() {
            return timePeriod;
        }

        @Override
        public Instant getBeginTime() {
            return beginTime;
        }

        @Override
        public Instant getEndTime() {
            return endTime;
        }

        @Override
        public Num getOpenPrice() {
            return openPrice;
        }

        @Override
        public Num getHighPrice() {
            return highPrice;
        }

        @Override
        public Num getLowPrice() {
            return lowPrice;
        }

        @Override
        public Num getClosePrice() {
            return closePrice;
        }

        @Override
        public Num getVolume() {
            return volume;
        }

        @Override
        public Num getAmount() {
            return amount;
        }

        @Override
        public long getTrades() {
            return trades;
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("research window bars are read-only");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("research window bars are read-only");
        }
    }

    /**
     * Dataset revision snapshot verified during the run.
     */
    private record SeriesSnapshot(String name, int beginIndex, int endIndex, int barCount, long barHistoryRevision,
            int maxBarCount) {

        private SeriesSnapshot(BarSeries series) {
            this(series.getName(), series.getBeginIndex(), series.getEndIndex(), series.getBarCount(),
                    series.getBarHistoryRevision(), series.getMaximumBarCount());
        }
    }

    /**
     * Mutable run counters.
     */
    private static final class RunCounters {
        long proposed;
        long rejected;
        long repaired;
        long duplicate;
        long cached;
        long attempted;
        long holdoutAttempted;
        long successful;
        long failed;
    }

    /**
     * Direction-aware primary-score comparison that tolerates scores produced by
     * different {@link NumFactory} implementations.
     *
     * <p>
     * {@code Num.compareTo} implementations may reject foreign instances (for
     * example {@link org.ta4j.core.num.DecimalNum#compareTo} casts to
     * {@code DecimalNum}); instead of coercing scores at evaluation time — which
     * would destroy decimal precision — cross-factory scores are compared through
     * exact {@link BigDecimal} values from both {@code bigDecimalValue()}
     * conversions, which stay exact even when a custom {@code Num}'s delegate does
     * not format as a decimal token. The comparison is symmetric and preserves
     * decimal precision in both operands. Every zero-valued score compares equal to
     * every other zero-valued score regardless of factory or sign, and NaN scores
     * are ordered after everything else, so an objective that declares failure for
     * non-finite scores keeps its ranking behavior.
     * </p>
     *
     * @param left  first score
     * @param right second score
     * @return a negative integer, zero, or a positive integer as {@code left} ranks
     *         better than, equal to, or worse than {@code right}
     */
    static int compareScores(Num left, Num right) {
        boolean leftNaN = !Num.isFinite(left);
        boolean rightNaN = !Num.isFinite(right);
        if (leftNaN || rightNaN) {
            return Boolean.compare(leftNaN, rightNaN);
        }
        if (left.isZero() && right.isZero()) {
            return 0;
        }
        if (left.getClass() == right.getClass()) {
            return left.compareTo(right);
        }
        return left.bigDecimalValue().compareTo(right.bigDecimalValue());
    }

    /**
     * Subtracts {@code subtrahend} from {@code minuend}, tolerating scores produced
     * by different {@link NumFactory} implementations: a cross-factory subtraction
     * goes through exact {@link BigDecimal} values instead of coercing one operand
     * through the other's factory, which could overflow to infinity or round away
     * the delta. A same-factory subtraction whose native result is non-finite (e.g.
     * {@code Double.MAX_VALUE - (-Double.MAX_VALUE)}) also falls back to the exact
     * decimal path, so finite operand scores never produce an infinite delta.
     *
     * @param minuend    score to subtract from
     * @param subtrahend score to subtract
     * @return {@code minuend - subtrahend}; in the minuend's factory when both
     *         operands share it and the result stays finite, otherwise as an exact
     *         decimal
     */
    static Num subtractScores(Num minuend, Num subtrahend) {
        if (minuend.getClass() == subtrahend.getClass()) {
            Num result = minuend.minus(subtrahend);
            if (Num.isFinite(result)) {
                return result;
            }
        }
        return DecimalNum.valueOf(minuend.bigDecimalValue().subtract(subtrahend.bigDecimalValue()));
    }

    /**
     * Reports whether {@code candidate} is a strict improvement over
     * {@code current} under the given goal direction, by primary score only.
     *
     * @param direction goal direction
     * @param candidate candidate score
     * @param current   current best score
     * @return {@code true} when the candidate score is strictly better
     */
    static boolean scoreIsBetter(Direction direction, Num candidate, Num current) {
        return direction == Direction.MAXIMIZE ? compareScores(candidate, current) > 0
                : compareScores(candidate, current) < 0;
    }

    /**
     * Shared deterministic ranking: better primary score under the goal direction,
     * then fewer repair notes, then lower evaluation ordinal, then canonical
     * candidate ID lexicographically. Used by the training leaderboard, the holdout
     * ranking, and engine-internal ordering.
     */
    private static Comparator<EvaluatedCandidate> rankingComparator(Direction direction) {
        return (a, b) -> {
            int comparison = direction == Direction.MAXIMIZE ? compareScores(b.score(), a.score())
                    : compareScores(a.score(), b.score());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(a.parameters().repairCount(), b.parameters().repairCount());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(a.evaluationOrdinal(), b.evaluationOrdinal());
            if (comparison != 0) {
                return comparison;
            }
            return a.candidateId().compareTo(b.candidateId());
        };
    }

    private static String resolveDatasetId(BarSeries series) {
        String name = series.getName();
        if (name == null || name.isBlank()) {
            return "series";
        }
        return name;
    }

    private static String domainSpec(ParameterDomain domain) {
        // Length-prefix the name so names containing type labels or separators
        // cannot alias across fingerprints, e.g. a boolean domain named
        // "x|bool:y" must not collide with separate domains "x" and "bool:y".
        String name = domain.name().length() + ":" + domain.name();
        if (domain instanceof ParameterDomain.IntegerDomain d) {
            return "integer:" + name + ":" + d.from() + ":" + d.to() + ":" + d.step();
        }
        if (domain instanceof ParameterDomain.DecimalDomain d) {
            return "decimal:" + name + ":" + canonicalDecimal(d.from()) + ":" + canonicalDecimal(d.to()) + ":"
                    + canonicalDecimal(d.step());
        }
        if (domain instanceof ParameterDomain.BooleanDomain d) {
            return "bool:" + name;
        }
        if (domain instanceof ParameterDomain.CategoricalDomain d) {
            return "categorical:" + name + ":" + categoricalValues(d.values());
        }
        throw new IllegalArgumentException("Unsupported parameter domain: " + domain.getClass().getName());
    }

    static String canonicalDecimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String categoricalValues(List<String> values) {
        // Length-prefix every literal so values containing separators cannot
        // collide: ["a,b", "c"] and ["a", "b,c"] must not share a fingerprint.
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append(value.length()).append(':').append(value).append(';');
        }
        return builder.toString();
    }

    private static String message(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null ? "" : ": " + message;
    }

    private static void verifyUnchanged(SeriesSnapshot snapshot, BarSeries series) {
        if (!Objects.equals(snapshot.name(), series.getName()) || snapshot.beginIndex() != series.getBeginIndex()
                || snapshot.endIndex() != series.getEndIndex() || snapshot.barCount() != series.getBarCount()
                || snapshot.barHistoryRevision() != series.getBarHistoryRevision()
                || snapshot.maxBarCount() != series.getMaximumBarCount()) {
            throw new IllegalStateException("dataset changed during research: expected name='" + snapshot.name()
                    + "' beginIndex=" + snapshot.beginIndex() + " endIndex=" + snapshot.endIndex() + " barCount="
                    + snapshot.barCount() + " barHistoryRevision=" + snapshot.barHistoryRevision() + " maxBarCount="
                    + snapshot.maxBarCount() + ", but observed name='" + series.getName() + "' beginIndex="
                    + series.getBeginIndex() + " endIndex=" + series.getEndIndex() + " barCount=" + series.getBarCount()
                    + " barHistoryRevision=" + series.getBarHistoryRevision() + " maxBarCount="
                    + series.getMaximumBarCount());
        }
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.substring(0, SHORT_HASH_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
