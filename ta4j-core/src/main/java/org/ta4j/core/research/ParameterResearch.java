/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.BarSeriesManager.TradingRecordFactory;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankedTradingStatement;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.reports.TradingStatementGenerator;
import org.ta4j.core.walkforward.WalkForwardCandidate;

/**
 * Utilities for parameter research workflows that separate candidate
 * generation, training-window selection, optional pruning, and holdout
 * validation.
 *
 * <p>
 * The workflow is deliberately conservative: candidate spaces can be generated
 * from the training window, pruning defaults to exact trading behavior rather
 * than fuzzy indicator similarity, and reports preserve the candidate and
 * selection metadata needed to reproduce a run.
 * </p>
 *
 * @since 0.22.8
 */
public final class ParameterResearch {

    private static final int SHORT_HASH_LENGTH = 12;
    private static final double NO_DISTANCE = 0d;

    /**
     * Default upper bound on the declared raw candidate-space cardinality.
     *
     * <p>
     * The bound is enforced on the checked product of domain sizes before any
     * normalization or combination allocation, so oversized ranges fail fast
     * instead of materializing huge value lists.
     * </p>
     *
     * @see #generateCandidateSpace(BarSeries, List, CandidateValidator, int)
     * @see ResearchConfig#maxCombinations()
     */
    public static final int DEFAULT_MAX_COMBINATIONS = 100_000;

    private ParameterResearch() {
    }

    /**
     * Generates a deterministic candidate space from one or more parameter domains.
     *
     * @param series  series used by domain normalizers
     * @param domains ordered parameter domains
     * @return candidate generation result
     * @since 0.22.8
     */
    public static CandidateGenerationResult generateCandidateSpace(BarSeries series, List<ParameterDomain> domains) {
        return generateCandidateSpace(series, domains, CandidateValidator.acceptAll(), DEFAULT_MAX_COMBINATIONS);
    }

    /**
     * Generates a deterministic candidate space and captures rejected combinations.
     *
     * @param series    series used by domain normalizers
     * @param domains   ordered parameter domains
     * @param validator optional cross-parameter validator
     * @return candidate generation result
     * @since 0.22.8
     */
    public static CandidateGenerationResult generateCandidateSpace(BarSeries series, List<ParameterDomain> domains,
            CandidateValidator validator) {
        return generateCandidateSpace(series, domains, validator, DEFAULT_MAX_COMBINATIONS);
    }

    /**
     * Generates a deterministic candidate space under an explicit cardinality
     * budget.
     *
     * <p>
     * The declared raw cardinality (the checked product of the domain sizes) is
     * validated against {@code maxCombinations} before any value is normalized or
     * any combination is materialized, so oversized ranges fail fast without
     * allocating intermediate value lists.
     * </p>
     *
     * @param series          series used by domain normalizers
     * @param domains         ordered parameter domains
     * @param validator       optional cross-parameter validator
     * @param maxCombinations maximum declared raw combinations, inclusive
     * @return candidate generation result
     * @throws IllegalArgumentException when the declared cardinality exceeds the
     *                                  budget or no candidates survive
     * @since 0.22.8
     */
    public static CandidateGenerationResult generateCandidateSpace(BarSeries series, List<ParameterDomain> domains,
            CandidateValidator validator, int maxCombinations) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(domains, "domains");
        if (maxCombinations <= 0) {
            throw new IllegalArgumentException("maxCombinations must be > 0");
        }
        CandidateValidator effectiveValidator = validator == null ? CandidateValidator.acceptAll() : validator;
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        if (domains.isEmpty()) {
            throw new IllegalArgumentException("domains cannot be empty");
        }

        List<ParameterDomain> effectiveDomains = new ArrayList<>(domains.size());
        Set<String> domainNames = new LinkedHashSet<>();
        for (ParameterDomain domain : domains) {
            Objects.requireNonNull(domain, "domains cannot contain null values");
            if (!domainNames.add(domain.name())) {
                throw new IllegalArgumentException("Duplicate parameter domain name: " + domain.name());
            }
            effectiveDomains.add(domain);
        }

        long rawCombinationCount = declaredRawCombinationCount(effectiveDomains, maxCombinations);
        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        List<List<ParameterValue>> normalizedValuesByDomain = new ArrayList<>(effectiveDomains.size());
        for (ParameterDomain domain : effectiveDomains) {
            List<ParameterValue> normalizedValues = new ArrayList<>();
            for (String rawValue : domain.rawValues()) {
                try {
                    ParameterValue normalized = Objects.requireNonNull(
                            domain.normalizer().normalize(series, domain.name(), rawValue), "normalizer returned null");
                    if (!domain.name().equals(normalized.name())) {
                        throw new IllegalArgumentException("Normalizer returned parameter name " + normalized.name()
                                + " for domain " + domain.name());
                    }
                    normalizedValues.add(normalized);
                } catch (RuntimeException ex) {
                    invalidCandidates.add(new InvalidCandidate(domain.name() + "=" + rawValue,
                            Map.of(domain.name(), rawValue), CandidateFailureStage.GENERATION, ex.getMessage()));
                }
            }
            if (normalizedValues.isEmpty()) {
                throw new IllegalArgumentException("No valid values remain for parameter domain " + domain.name());
            }
            normalizedValuesByDomain.add(List.copyOf(normalizedValues));
        }

        List<StrategyCandidate> candidates = new ArrayList<>();
        Set<String> seenCandidateIds = new LinkedHashSet<>();
        collectCombinations(normalizedValuesByDomain, 0, new ArrayList<>(), parameters -> {
            ParameterSet parameterSet = new ParameterSet(parameters);
            String candidateId = parameterSet.stableId();
            try {
                effectiveValidator.validate(parameterSet);
            } catch (RuntimeException ex) {
                invalidCandidates.add(new InvalidCandidate(candidateId, parameterSet.asMap(),
                        CandidateFailureStage.COMBINATION_VALIDATION, ex.getMessage()));
                return;
            }

            if (!seenCandidateIds.add(candidateId)) {
                invalidCandidates.add(new InvalidCandidate(candidateId, parameterSet.asMap(),
                        CandidateFailureStage.DUPLICATE_NORMALIZED, "Duplicate normalized parameter set"));
                return;
            }
            candidates.add(new StrategyCandidate(candidateId, parameterSet));
        });

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidates remain after normalization and validation");
        }
        return new CandidateGenerationResult(candidates, invalidCandidates, rawCombinationCount,
                hashCandidateIds(candidates));
    }

    private static long declaredRawCombinationCount(List<ParameterDomain> domains, int maxCombinations) {
        long rawCombinationCount = 1L;
        for (ParameterDomain domain : domains) {
            long domainCount = domain.rawValues().size();
            if (rawCombinationCount > maxCombinations / domainCount) {
                rawCombinationCount = Long.MAX_VALUE;
            } else {
                rawCombinationCount *= domainCount;
            }
        }
        if (rawCombinationCount > maxCombinations) {
            if (rawCombinationCount == Long.MAX_VALUE) {
                throw new IllegalArgumentException("Declared candidate space exceeds the maximum of " + maxCombinations
                        + " combinations; narrow the parameter domains or raise maxCombinations");
            }
            throw new IllegalArgumentException(
                    "Declared candidate space has " + rawCombinationCount + " combinations, exceeding the maximum of "
                            + maxCombinations + "; narrow the parameter domains or raise maxCombinations");
        }
        return rawCombinationCount;
    }

    /**
     * Runs parameter research by generating candidates from the training window,
     * selecting on training data, and validating selected representatives on a
     * holdout window.
     *
     * @param series          full series
     * @param domains         ordered parameter domains
     * @param strategyFactory strategy factory
     * @param config          research configuration
     * @return structured research report
     * @since 0.22.8
     */
    public static ParameterResearchReport run(BarSeries series, List<ParameterDomain> domains,
            StrategyFactory strategyFactory, ResearchConfig config) {
        return run(series, domains, CandidateValidator.acceptAll(), strategyFactory, config);
    }

    /**
     * Runs parameter research by generating candidates from the training window,
     * selecting on training data, and validating selected representatives on a
     * holdout window.
     *
     * <p>
     * Candidates are normalized against a training-window sub-series (restarting at
     * index 0) so period-like parameters cannot leak validation-window length, but
     * strategies are executed on the full series with original-series indexes so
     * indicators see the same history they would see in production.
     * </p>
     *
     * @param series          full series
     * @param domains         ordered parameter domains
     * @param validator       optional cross-parameter validator
     * @param strategyFactory strategy factory
     * @param config          research configuration
     * @return structured research report
     * @since 0.22.8
     */
    public static ParameterResearchReport run(BarSeries series, List<ParameterDomain> domains,
            CandidateValidator validator, StrategyFactory strategyFactory, ResearchConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(domains, "domains");
        Objects.requireNonNull(strategyFactory, "strategyFactory");
        Objects.requireNonNull(config, "config");
        ResearchWindow window = resolveWindow(series, config);
        BarSeries trainingSeries = trainingWindowSeries(series, window);
        CandidateGenerationResult candidateSpace = generateCandidateSpace(trainingSeries, domains, validator,
                config.maxCombinations());
        return runWithCandidateSpace(series, candidateSpace, strategyFactory, config, window, List.of());
    }

    /**
     * Runs parameter research against a caller-supplied candidate space.
     *
     * <p>
     * Prefer
     * {@link #run(BarSeries, List, CandidateValidator, StrategyFactory, ResearchConfig)}
     * when period-like normalizers depend on series length; that overload builds
     * candidates from the training window and avoids validation-window leakage.
     * </p>
     *
     * @param series          full series
     * @param candidateSpace  normalized candidate space
     * @param strategyFactory strategy factory
     * @param config          research configuration
     * @return structured research report
     * @since 0.22.8
     */
    public static ParameterResearchReport run(BarSeries series, CandidateGenerationResult candidateSpace,
            StrategyFactory strategyFactory, ResearchConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(candidateSpace, "candidateSpace");
        Objects.requireNonNull(strategyFactory, "strategyFactory");
        Objects.requireNonNull(config, "config");
        ResearchWindow window = resolveWindow(series, config);
        return runWithCandidateSpace(series, candidateSpace, strategyFactory, config, window,
                List.of("Candidate space supplied by caller; ensure it was generated from training data only."));
    }

    /**
     * Converts generated strategy candidates into walk-forward tuner candidates.
     *
     * @param candidateSpace generated candidate space
     * @return candidates suitable for {@code WalkForwardTuner}
     * @since 0.22.8
     */
    public static List<WalkForwardCandidate<ParameterSet>> toWalkForwardCandidates(
            CandidateGenerationResult candidateSpace) {
        Objects.requireNonNull(candidateSpace, "candidateSpace");
        return candidateSpace.candidates()
                .stream()
                .map(candidate -> new WalkForwardCandidate<>(candidate.id(), candidate.parameters()))
                .toList();
    }

    private static ParameterResearchReport runWithCandidateSpace(BarSeries fullSeries,
            CandidateGenerationResult candidateSpace, StrategyFactory strategyFactory, ResearchConfig config,
            ResearchWindow window, List<String> initialWarnings) {
        if (fullSeries.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        ResearchExecutor executor = new ResearchExecutor(fullSeries, config);
        ExecutionBundle trainingExecution = executeCandidates(fullSeries, candidateSpace.candidates(), strategyFactory,
                executor, config.amount(), config.tradeType(), window.trainingStartIndex(), window.trainingEndIndex(),
                CandidateFailureStage.STRATEGY_BUILD, CandidateFailureStage.TRAINING_EXECUTION);
        if (trainingExecution.candidates().isEmpty()) {
            throw new IllegalArgumentException("No candidates could be evaluated on the training window: "
                    + trainingExecution.invalidCandidates());
        }

        BacktestExecutionResult trainingResult = trainingExecution.result();
        List<RankedTradingStatement> baselineRanking = trainingResult.rankTradingStatements(config.rankingProfile());
        PruningResult pruningResult = buildPruningGroups(config, fullSeries, trainingExecution, baselineRanking,
                window.trainingStartIndex(), window.trainingEndIndex());
        List<PruningGroup> pruningGroups = pruningResult.groups();
        Set<String> representativeIds = representativeIds(pruningGroups);
        List<CandidateScore> baselineScores = toScores(baselineRanking, trainingExecution.candidates(),
                representativeIds);
        List<StrategyCandidate> representativeCandidates = filterCandidates(trainingExecution.candidates(),
                representativeIds);
        List<TradingStatement> representativeStatements = filterStatements(trainingExecution.candidates(),
                trainingResult.tradingStatements(), representativeIds);

        BacktestExecutionResult representativeTrainingResult = new BacktestExecutionResult(fullSeries,
                representativeStatements, trainingResult.runtimeReport());
        List<RankedTradingStatement> representativeRanking = representativeTrainingResult
                .rankTradingStatements(config.rankingProfile());
        List<CandidateScore> trainingScores = toScores(representativeRanking, representativeCandidates,
                representativeIds);

        List<StrategyCandidate> selectedCandidates = topCandidates(trainingScores, representativeCandidates,
                config.topK());
        ValidationBundle validationBundle = validateSelected(fullSeries, executor, selectedCandidates, strategyFactory,
                config, window);

        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        invalidCandidates.addAll(candidateSpace.invalidCandidates());
        invalidCandidates.addAll(trainingExecution.invalidCandidates());
        invalidCandidates.addAll(pruningResult.invalidCandidates());
        invalidCandidates.addAll(validationBundle.invalidCandidates());

        List<String> warnings = new ArrayList<>(initialWarnings);
        warnings.addAll(windowWarnings(config, window, fullSeries));
        warnings.addAll(policyWarnings(config, validationBundle.validationScores()));

        String baselineTopCandidateId = baselineRanking.isEmpty() ? ""
                : trainingExecution.candidates().get(baselineRanking.getFirst().originalIndex()).id();
        String selectedTopCandidateId = trainingScores.isEmpty() ? "" : trainingScores.getFirst().candidateId();

        return new ParameterResearchReport(resolveDatasetId(fullSeries), fullSeries.getBarCount(), window,
                candidateSpace.candidateSpaceHash(), config.pruningPolicy(), candidateSpace.rawCandidateCount(),
                candidateSpace.generatedCandidateCount(), trainingExecution.candidates().size(),
                invalidCandidates.size(), candidateSpace.candidates(), baselineTopCandidateId, selectedTopCandidateId,
                pruningGroups, baselineScores, trainingScores, validationBundle.validationScores(), invalidCandidates,
                warnings, trainingResult.runtimeReport(), validationBundle.runtimeReport());
    }

    /**
     * Executes candidates on one window of the full series with per-candidate
     * failure isolation.
     *
     * <p>
     * Strategy construction and execution failures are recorded as invalid
     * candidates with stage-specific labels instead of aborting the run. Parallel
     * execution writes into index-ordered arrays so result ordering stays
     * deterministic regardless of scheduling.
     * </p>
     *
     * @param fullSeries            full series
     * @param candidates            candidates to evaluate
     * @param strategyFactory       strategy factory
     * @param executor              shared execution context
     * @param amount                trade amount
     * @param tradeTypeOverride     starting trade type, or null for per-strategy
     * @param startIndex            inclusive window start on the full series
     * @param endIndex              inclusive window end on the full series
     * @param buildFailureStage     stage for strategy-build failures
     * @param executionFailureStage stage for execution failures
     * @return execution bundle
     */
    private static ExecutionBundle executeCandidates(BarSeries fullSeries, List<StrategyCandidate> candidates,
            StrategyFactory strategyFactory, ResearchExecutor executor, Num amount, TradeType tradeTypeOverride,
            int startIndex, int endIndex, CandidateFailureStage buildFailureStage,
            CandidateFailureStage executionFailureStage) {
        List<StrategyCandidate> executableCandidates = new ArrayList<>(candidates.size());
        List<Strategy> strategies = new ArrayList<>(candidates.size());
        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        for (StrategyCandidate candidate : candidates) {
            try {
                Strategy strategy = Objects.requireNonNull(strategyFactory.create(fullSeries, candidate.parameters()),
                        "strategyFactory returned null");
                strategies.add(strategy);
                executableCandidates.add(candidate);
            } catch (RuntimeException ex) {
                invalidCandidates.add(new InvalidCandidate(candidate.id(), candidate.parameters().asMap(),
                        buildFailureStage, ex.getMessage()));
            }
        }

        int executableCount = strategies.size();
        TradingStatement[] statements = new TradingStatement[executableCount];
        long[] durations = new long[executableCount];
        InvalidCandidate[] executionFailures = new InvalidCandidate[executableCount];
        long overallStart = System.nanoTime();
        IntStream stream = IntStream.range(0, executableCount);
        if (executableCount > 1) {
            stream = stream.parallel();
        }
        stream.forEach(index -> {
            StrategyCandidate candidate = executableCandidates.get(index);
            Strategy strategy = strategies.get(index);
            long startNanos = System.nanoTime();
            try {
                TradeType tradeType = tradeTypeOverride != null ? tradeTypeOverride : strategy.getStartingType();
                TradingRecord record = executor.manager().run(strategy, tradeType, amount, startIndex, endIndex);
                statements[index] = executor.statementGenerator().generate(strategy, record, fullSeries);
                durations[index] = System.nanoTime() - startNanos;
            } catch (RuntimeException ex) {
                executionFailures[index] = new InvalidCandidate(candidate.id(), candidate.parameters().asMap(),
                        executionFailureStage, ex.getMessage());
            }
        });
        Duration overallRuntime = Duration.ofNanos(System.nanoTime() - overallStart);

        List<StrategyCandidate> executedCandidates = new ArrayList<>(executableCount);
        List<Strategy> executedStrategies = new ArrayList<>(executableCount);
        List<TradingStatement> executedStatements = new ArrayList<>(executableCount);
        List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes = new ArrayList<>(executableCount);
        long[] executedDurations = new long[executableCount];
        int executedCount = 0;
        for (int index = 0; index < executableCount; index++) {
            if (executionFailures[index] != null) {
                invalidCandidates.add(executionFailures[index]);
                continue;
            }
            executedCandidates.add(executableCandidates.get(index));
            executedStrategies.add(strategies.get(index));
            executedStatements.add(statements[index]);
            executedDurations[executedCount] = durations[index];
            strategyRuntimes.add(new BacktestRuntimeReport.StrategyRuntime(strategies.get(index),
                    Duration.ofNanos(durations[index])));
            executedCount++;
        }

        BacktestExecutionResult result = executedStrategies.isEmpty()
                ? new BacktestExecutionResult(fullSeries, List.of(), BacktestRuntimeReport.empty())
                : new BacktestExecutionResult(fullSeries, List.copyOf(executedStatements), buildRuntimeReport(
                        Arrays.copyOf(executedDurations, executedCount), overallRuntime, strategyRuntimes));
        return new ExecutionBundle(executedCandidates, result, invalidCandidates);
    }

    /**
     * Mirrors {@code BacktestExecutor.buildRuntimeReport} statistics.
     */
    private static BacktestRuntimeReport buildRuntimeReport(long[] durations, Duration overallRuntime,
            List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes) {
        LongSummaryStatistics summaryStatistics = Arrays.stream(durations).summaryStatistics();
        if (summaryStatistics.getCount() == 0) {
            return new BacktestRuntimeReport(overallRuntime, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    strategyRuntimes);
        }
        long[] sortedDurations = durations.clone();
        Arrays.sort(sortedDurations);
        int midPoint = sortedDurations.length / 2;
        long medianNanos;
        if (sortedDurations.length % 2 == 0) {
            medianNanos = (sortedDurations[midPoint - 1] + sortedDurations[midPoint]) / 2;
        } else {
            medianNanos = sortedDurations[midPoint];
        }
        return new BacktestRuntimeReport(overallRuntime, Duration.ofNanos(summaryStatistics.getMin()),
                Duration.ofNanos(summaryStatistics.getMax()),
                Duration.ofNanos(Math.round(summaryStatistics.getAverage())), Duration.ofNanos(medianNanos),
                strategyRuntimes);
    }

    private static ValidationBundle validateSelected(BarSeries fullSeries, ResearchExecutor executor,
            List<StrategyCandidate> selectedCandidates, StrategyFactory strategyFactory, ResearchConfig config,
            ResearchWindow window) {
        if (!window.hasValidationWindow() || selectedCandidates.isEmpty()) {
            return new ValidationBundle(List.of(), BacktestRuntimeReport.empty(), List.of());
        }

        ExecutionBundle execution = executeCandidates(fullSeries, selectedCandidates, strategyFactory, executor,
                config.amount(), config.tradeType(), window.validationStartIndex(), window.validationEndIndex(),
                CandidateFailureStage.VALIDATION_STRATEGY_BUILD, CandidateFailureStage.VALIDATION_EXECUTION);
        if (execution.candidates().isEmpty()) {
            return new ValidationBundle(List.of(), execution.result().runtimeReport(), execution.invalidCandidates());
        }
        List<RankedTradingStatement> ranked = execution.result().rankTradingStatements(config.rankingProfile());
        Set<String> selectedIds = candidateIds(selectedCandidates);
        List<CandidateScore> validationScores = toScores(ranked, execution.candidates(), selectedIds);
        return new ValidationBundle(validationScores, execution.result().runtimeReport(),
                execution.invalidCandidates());
    }

    private static PruningResult buildPruningGroups(ResearchConfig config, BarSeries fullSeries,
            ExecutionBundle execution, List<RankedTradingStatement> baselineRanking, int windowStartIndex,
            int windowEndIndex) {
        return switch (config.pruningPolicy()) {
        case NONE -> new PruningResult(noneGroups(execution.candidates()), List.of());
        case EXACT_TRADING_RECORD -> new PruningResult(exactSignatureGroups(execution,
                candidateIndex -> tradingRecordSignature(
                        execution.result().tradingStatements().get(candidateIndex).getTradingRecord()),
                "exact trading record", NO_DISTANCE), List.of());
        case INDICATOR_DISTANCE ->
            indicatorDistanceGroups(config, fullSeries, execution, baselineRanking, windowStartIndex, windowEndIndex);
        case OBJECTIVE_DISTANCE ->
            new PruningResult(objectiveDistanceGroups(config, execution, baselineRanking), List.of());
        };
    }

    private static List<PruningGroup> noneGroups(List<StrategyCandidate> candidates) {
        List<PruningGroup> groups = new ArrayList<>(candidates.size());
        for (StrategyCandidate candidate : candidates) {
            groups.add(new PruningGroup(candidate.id(), List.of(candidate.id()), "no pruning", NO_DISTANCE));
        }
        return List.copyOf(groups);
    }

    private static List<PruningGroup> exactSignatureGroups(ExecutionBundle execution,
            IntFunction<String> signatureSupplier, String reason, double maximumDistance) {
        Map<String, PruningGroupBuilder> groupsBySignature = new LinkedHashMap<>();
        for (int i = 0; i < execution.candidates().size(); i++) {
            StrategyCandidate candidate = execution.candidates().get(i);
            String signature = signatureSupplier.apply(i);
            PruningGroupBuilder builder = groupsBySignature.computeIfAbsent(signature,
                    ignored -> new PruningGroupBuilder(candidate.id(), reason));
            builder.add(candidate.id(), maximumDistance);
        }
        return groupsBySignature.values().stream().map(PruningGroupBuilder::build).toList();
    }

    private static PruningResult indicatorDistanceGroups(ResearchConfig config, BarSeries fullSeries,
            ExecutionBundle execution, List<RankedTradingStatement> baselineRanking, int windowStartIndex,
            int windowEndIndex) {
        if (config.indicatorFactory() == null) {
            throw new IllegalArgumentException("indicatorFactory is required for INDICATOR_DISTANCE pruning");
        }

        Map<String, double[]> signaturesByCandidateId = new LinkedHashMap<>();
        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        for (StrategyCandidate candidate : execution.candidates()) {
            try {
                Indicator<Num> indicator = Objects.requireNonNull(
                        config.indicatorFactory().create(fullSeries, candidate.parameters()),
                        "indicatorFactory returned null");
                signaturesByCandidateId.put(candidate.id(),
                        captureIndicatorSignature(fullSeries, indicator, windowStartIndex, windowEndIndex));
            } catch (RuntimeException ex) {
                invalidCandidates.add(new InvalidCandidate(candidate.id(), candidate.parameters().asMap(),
                        CandidateFailureStage.PRUNING_INDICATOR, ex.getMessage()));
            }
        }

        List<PruningGroupBuilder> builders = new ArrayList<>();
        List<double[]> representativeSignatures = new ArrayList<>();
        Set<Integer> groupedIndexes = new LinkedHashSet<>();
        for (RankedTradingStatement ranked : baselineRanking) {
            groupedIndexes.add(ranked.originalIndex());
            StrategyCandidate candidate = execution.candidates().get(ranked.originalIndex());
            double[] signature = signaturesByCandidateId.get(candidate.id());
            if (signature != null) {
                addIndicatorDistanceGroup(config, builders, representativeSignatures, candidate, signature);
            }
        }
        for (int i = 0; i < execution.candidates().size(); i++) {
            if (groupedIndexes.contains(i)) {
                continue;
            }
            StrategyCandidate candidate = execution.candidates().get(i);
            double[] signature = signaturesByCandidateId.get(candidate.id());
            if (signature != null) {
                addIndicatorDistanceGroup(config, builders, representativeSignatures, candidate, signature);
            }
        }
        return new PruningResult(builders.stream().map(PruningGroupBuilder::build).toList(),
                List.copyOf(invalidCandidates));
    }

    private static void addIndicatorDistanceGroup(ResearchConfig config, List<PruningGroupBuilder> builders,
            List<double[]> representativeSignatures, StrategyCandidate candidate, double[] signature) {
        for (int groupIndex = 0; groupIndex < representativeSignatures.size(); groupIndex++) {
            double distance = rmsDistance(representativeSignatures.get(groupIndex), signature);
            if (distance <= config.distanceTolerance()) {
                builders.get(groupIndex).add(candidate.id(), distance);
                return;
            }
        }
        PruningGroupBuilder builder = new PruningGroupBuilder(candidate.id(), "indicator RMS distance");
        builder.add(candidate.id(), NO_DISTANCE);
        builders.add(builder);
        representativeSignatures.add(signature);
    }

    private static List<PruningGroup> objectiveDistanceGroups(ResearchConfig config, ExecutionBundle execution,
            List<RankedTradingStatement> baselineRanking) {
        List<PruningGroupBuilder> builders = new ArrayList<>();
        List<Double> representativeScores = new ArrayList<>();
        Set<Integer> groupedIndexes = new LinkedHashSet<>();
        for (RankedTradingStatement ranked : baselineRanking) {
            groupedIndexes.add(ranked.originalIndex());
            StrategyCandidate candidate = execution.candidates().get(ranked.originalIndex());
            double score = ranked.compositeScore().doubleValue();
            boolean matched = false;
            if (Double.isFinite(score)) {
                for (int groupIndex = 0; groupIndex < representativeScores.size(); groupIndex++) {
                    double distance = Math.abs(representativeScores.get(groupIndex) - score);
                    if (distance <= config.distanceTolerance()) {
                        builders.get(groupIndex).add(candidate.id(), distance);
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                PruningGroupBuilder builder = new PruningGroupBuilder(candidate.id(), "objective score distance");
                builder.add(candidate.id(), NO_DISTANCE);
                builders.add(builder);
                representativeScores.add(score);
            }
        }
        for (int i = 0; i < execution.candidates().size(); i++) {
            if (!groupedIndexes.contains(i)) {
                StrategyCandidate candidate = execution.candidates().get(i);
                PruningGroupBuilder builder = new PruningGroupBuilder(candidate.id(), "objective score unavailable");
                builder.add(candidate.id(), NO_DISTANCE);
                builders.add(builder);
            }
        }
        return builders.stream().map(PruningGroupBuilder::build).toList();
    }

    private static String tradingRecordSignature(TradingRecord tradingRecord) {
        StringBuilder builder = new StringBuilder();
        builder.append("start=")
                .append(tradingRecord.getStartIndex())
                .append(";end=")
                .append(tradingRecord.getEndIndex());
        for (Trade trade : tradingRecord.getTrades()) {
            builder.append('|')
                    .append(trade.getType())
                    .append('@')
                    .append(trade.getIndex())
                    .append(':')
                    .append(formatNum(trade.getPricePerAsset()))
                    .append(':')
                    .append(formatNum(trade.getAmount()));
        }
        return builder.toString();
    }

    private static double[] captureIndicatorSignature(BarSeries series, Indicator<Num> indicator, int windowStartIndex,
            int windowEndIndex) {
        int effectiveStart = Math.max(series.getBeginIndex(),
                Math.max(windowStartIndex, indicator.getCountOfUnstableBars()));
        if (effectiveStart > windowEndIndex) {
            return new double[0];
        }
        long length = (long) windowEndIndex - effectiveStart + 1L;
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Indicator signature window is too large: " + length + " values");
        }
        double[] values = new double[(int) length];
        int index = effectiveStart;
        for (int offset = 0;; offset++) {
            double value = indicator.getValue(index).doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Indicator produced a non-finite value at index " + index);
            }
            values[offset] = value;
            if (offset == values.length - 1) {
                break;
            }
            index++;
        }
        return values;
    }

    private static double rmsDistance(double[] left, double[] right) {
        if (left.length != right.length) {
            return Double.POSITIVE_INFINITY;
        }
        if (left.length == 0) {
            return 0d;
        }
        double sumSquared = 0d;
        for (int i = 0; i < left.length; i++) {
            double delta = left[i] - right[i];
            sumSquared += delta * delta;
        }
        return Math.sqrt(sumSquared / left.length);
    }

    private static List<CandidateScore> toScores(List<RankedTradingStatement> rankedStatements,
            List<StrategyCandidate> candidates, Set<String> representativeIds) {
        List<CandidateScore> scores = new ArrayList<>(rankedStatements.size());
        int rank = 1;
        for (RankedTradingStatement ranked : rankedStatements) {
            StrategyCandidate candidate = candidates.get(ranked.originalIndex());
            scores.add(new CandidateScore(candidate.id(), ranked.statement().getStrategy().getName(), rank,
                    ranked.compositeScore(), ranked.rawScores(), representativeIds.contains(candidate.id())));
            rank++;
        }
        return List.copyOf(scores);
    }

    private static List<StrategyCandidate> topCandidates(List<CandidateScore> scores,
            List<StrategyCandidate> representativeCandidates, int topK) {
        Map<String, StrategyCandidate> candidatesById = new LinkedHashMap<>();
        for (StrategyCandidate candidate : representativeCandidates) {
            candidatesById.put(candidate.id(), candidate);
        }
        List<StrategyCandidate> selected = new ArrayList<>();
        int limit = Math.min(topK, scores.size());
        for (int i = 0; i < limit; i++) {
            StrategyCandidate candidate = candidatesById.get(scores.get(i).candidateId());
            if (candidate != null) {
                selected.add(candidate);
            }
        }
        return List.copyOf(selected);
    }

    private static List<StrategyCandidate> filterCandidates(List<StrategyCandidate> candidates,
            Set<String> representativeIds) {
        List<StrategyCandidate> filtered = new ArrayList<>();
        for (StrategyCandidate candidate : candidates) {
            if (representativeIds.contains(candidate.id())) {
                filtered.add(candidate);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<TradingStatement> filterStatements(List<StrategyCandidate> candidates,
            List<TradingStatement> statements, Set<String> representativeIds) {
        List<TradingStatement> filtered = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (representativeIds.contains(candidates.get(i).id())) {
                filtered.add(statements.get(i));
            }
        }
        return List.copyOf(filtered);
    }

    private static Set<String> representativeIds(List<PruningGroup> pruningGroups) {
        Set<String> ids = new LinkedHashSet<>();
        for (PruningGroup group : pruningGroups) {
            ids.add(group.representativeId());
        }
        return Set.copyOf(ids);
    }

    private static Set<String> candidateIds(List<StrategyCandidate> selectedCandidates) {
        Set<String> ids = new LinkedHashSet<>();
        for (StrategyCandidate candidate : selectedCandidates) {
            ids.add(candidate.id());
        }
        return Set.copyOf(ids);
    }

    private static List<String> policyWarnings(ResearchConfig config, List<CandidateScore> validationScores) {
        List<String> warnings = new ArrayList<>();
        if (config.pruningPolicy() == PruningPolicy.INDICATOR_DISTANCE) {
            warnings.add(
                    "INDICATOR_DISTANCE is fuzzy and can hide trading-behavior differences; use exact policies for selection gates.");
        }
        if (config.pruningPolicy() == PruningPolicy.OBJECTIVE_DISTANCE) {
            warnings.add(
                    "OBJECTIVE_DISTANCE clusters by score similarity after evaluation; it is a reporting reduction, not a compute-saving gate.");
        }
        if (validationScores.isEmpty()) {
            warnings.add(
                    "No validation scores were produced; configure a positive validationBarCount to hold out data.");
        }
        return List.copyOf(warnings);
    }

    private static List<String> windowWarnings(ResearchConfig config, ResearchWindow window, BarSeries series) {
        List<String> warnings = new ArrayList<>();
        int actualValidationBars = window.hasValidationWindow()
                ? window.validationEndIndex() - window.validationStartIndex() + 1
                : 0;
        if (config.validationBarCount() > actualValidationBars) {
            warnings.add("validationBarCount was reduced from " + config.validationBarCount() + " to "
                    + actualValidationBars + " to leave at least one training bar.");
        }

        int actualTrainingBars = window.trainingEndIndex() - window.trainingStartIndex() + 1;
        if (config.trainingBarCount() > 0 && config.trainingBarCount() > actualTrainingBars) {
            warnings.add("trainingBarCount was reduced from " + config.trainingBarCount() + " to " + actualTrainingBars
                    + " by the available pre-validation data.");
        }
        if (series.getBeginIndex() != 0) {
            warnings.add(
                    "Candidate normalization uses a training-window sub-series that restarts at index 0; execution uses original-series indexes.");
        }
        return List.copyOf(warnings);
    }

    private static ResearchWindow resolveWindow(BarSeries series, ResearchConfig config) {
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        int barCount = series.getBarCount();
        int validationCount = Math.max(0, Math.min(config.validationBarCount(), barCount - 1));
        int validationStart = validationCount == 0 ? -1 : end - validationCount + 1;
        int validationEnd = validationCount == 0 ? -1 : end;
        int latestTrainingEnd = validationCount == 0 ? end : validationStart - 1;
        int availableTrainingCount = latestTrainingEnd - begin + 1;
        int requestedTrainingCount = config.trainingBarCount() <= 0 ? availableTrainingCount
                : Math.min(config.trainingBarCount(), availableTrainingCount);
        if (requestedTrainingCount <= 0) {
            throw new IllegalArgumentException("training window must contain at least one bar");
        }
        int trainingStart = latestTrainingEnd - requestedTrainingCount + 1;
        return new ResearchWindow(trainingStart, latestTrainingEnd, validationStart, validationEnd);
    }

    /**
     * Builds the training-window sub-series used for candidate normalization.
     *
     * <p>
     * The regular {@code getSubSeries(begin, end)} path is used whenever the window
     * end is not the maximum possible index; the terminal-index window (end ==
     * {@code Integer.MAX_VALUE}) cannot be represented by {@code endIndex + 1}, so
     * a fresh series is built bar by bar instead.
     * </p>
     */
    private static BarSeries trainingWindowSeries(BarSeries fullSeries, ResearchWindow window) {
        int trainingStartIndex = window.trainingStartIndex();
        int trainingEndIndex = window.trainingEndIndex();
        if (trainingEndIndex != Integer.MAX_VALUE) {
            return fullSeries.getSubSeries(trainingStartIndex, trainingEndIndex + 1);
        }
        BarSeries trainingSeries = new BaseBarSeriesBuilder().withName(fullSeries.getName())
                .withNumFactory(fullSeries.numFactory())
                .build();
        for (int index = trainingStartIndex;; index++) {
            trainingSeries.addBar(fullSeries.getBar(index));
            if (index == trainingEndIndex) {
                break;
            }
        }
        return trainingSeries;
    }

    private static void collectCombinations(List<List<ParameterValue>> valuesByDomain, int domainIndex,
            List<ParameterValue> current, Consumer<List<ParameterValue>> consumer) {
        if (domainIndex == valuesByDomain.size()) {
            consumer.accept(List.copyOf(current));
            return;
        }
        for (ParameterValue value : valuesByDomain.get(domainIndex)) {
            current.add(value);
            collectCombinations(valuesByDomain, domainIndex + 1, current, consumer);
            current.remove(current.size() - 1);
        }
    }

    private static String hashCandidateIds(List<StrategyCandidate> candidates) {
        StringJoiner joiner = new StringJoiner("\n");
        for (StrategyCandidate candidate : candidates) {
            joiner.add(candidate.id());
        }
        return shortHash(joiner.toString());
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

    private static String resolveDatasetId(BarSeries series) {
        String name = series.getName();
        if (name == null || name.isBlank()) {
            return "series";
        }
        return name;
    }

    private static String formatNum(Num value) {
        if (value == null) {
            return "null";
        }
        return value.toString();
    }

    /**
     * Strategy construction callback used by the research runner.
     *
     * @since 0.22.8
     */
    @FunctionalInterface
    public interface StrategyFactory {

        /**
         * Builds a strategy for one candidate on the supplied series.
         *
         * @param series     target series
         * @param parameters normalized parameter set
         * @return strategy to evaluate
         * @since 0.22.8
         */
        Strategy create(BarSeries series, ParameterSet parameters);
    }

    /**
     * Indicator construction callback for explicit fuzzy indicator-distance
     * reports.
     *
     * @since 0.22.8
     */
    @FunctionalInterface
    public interface IndicatorFactory {

        /**
         * Builds an indicator for one candidate on the supplied series.
         *
         * @param series     target series
         * @param parameters normalized parameter set
         * @return indicator signature source
         * @since 0.22.8
         */
        Indicator<Num> create(BarSeries series, ParameterSet parameters);
    }

    /**
     * Cross-parameter validation callback.
     *
     * @since 0.22.8
     */
    @FunctionalInterface
    public interface CandidateValidator {

        /**
         * Accepts or rejects one parameter set.
         *
         * @param parameters normalized parameter set
         * @since 0.22.8
         */
        void validate(ParameterSet parameters);

        /**
         * Returns a validator that accepts all parameter sets.
         *
         * @return no-op validator
         * @since 0.22.8
         */
        static CandidateValidator acceptAll() {
            return parameters -> {
                // no-op
            };
        }
    }

    /**
     * Normalizes one raw parameter value.
     *
     * @since 0.22.8
     */
    @FunctionalInterface
    public interface ParameterNormalizer {

        /**
         * Normalizes one raw parameter value.
         *
         * @param series   series context
         * @param name     parameter name
         * @param rawValue raw value token
         * @return normalized parameter value
         * @since 0.22.8
         */
        ParameterValue normalize(BarSeries series, String name, String rawValue);
    }

    /**
     * Parameter domain for candidate-space generation.
     *
     * @param name       parameter name
     * @param rawValues  ordered raw values
     * @param normalizer value normalizer
     * @since 0.22.8
     */
    public record ParameterDomain(String name, List<String> rawValues, ParameterNormalizer normalizer) {

        /**
         * Creates a validated parameter domain.
         *
         * <p>
         * The raw value list is wrapped as an unmodifiable view; callers must not
         * mutate the backing list after construction. Large lazy ranges (see
         * {@link #integerRange}) are only iterated, never copied.
         * </p>
         *
         * @since 0.22.8
         */
        public ParameterDomain {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            Objects.requireNonNull(rawValues, "rawValues");
            if (rawValues.isEmpty()) {
                throw new IllegalArgumentException("rawValues cannot be empty");
            }
            for (String rawValue : rawValues) {
                if (rawValue == null || rawValue.isBlank()) {
                    throw new IllegalArgumentException("rawValues cannot contain blank values");
                }
            }
            rawValues = Collections.unmodifiableList(rawValues);
            Objects.requireNonNull(normalizer, "normalizer");
        }

        /**
         * Creates a domain with literal string values.
         *
         * @param name   parameter name
         * @param values ordered values
         * @return parameter domain
         * @since 0.22.8
         */
        public static ParameterDomain values(String name, List<?> values) {
            Objects.requireNonNull(values, "values");
            List<String> rawValues = new ArrayList<>(values.size());
            for (Object value : values) {
                if (value == null) {
                    throw new IllegalArgumentException("values cannot contain null entries");
                }
                rawValues.add(String.valueOf(value));
            }
            return new ParameterDomain(name, rawValues, (series, parameterName,
                    rawValue) -> new ParameterValue(parameterName, rawValue, rawValue, false, ""));
        }

        /**
         * Creates an inclusive integer range domain.
         *
         * @param name  parameter name
         * @param start first value
         * @param stop  last value
         * @param step  positive increment
         * @return integer range domain
         * @since 0.22.8
         */
        public static ParameterDomain integerRange(String name, int start, int stop, int step) {
            return integerRange(name, start, stop, step, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
        }

        /**
         * Creates an inclusive integer range for lookback/period-like parameters.
         *
         * <p>
         * Values are normalized to the available training series length so generated
         * strategies cannot request more bars than the selection window contains.
         * </p>
         *
         * @param name  parameter name
         * @param start first value
         * @param stop  last value
         * @param step  positive increment
         * @return period range domain capped to {@code [1, series.getBarCount()]}
         * @since 0.22.8
         */
        public static ParameterDomain periodRange(String name, int start, int stop, int step) {
            return integerRange(name, start, stop, step, 1, Integer.MAX_VALUE, true);
        }

        /**
         * Creates an inclusive integer range domain with natural bounds.
         *
         * @param name              parameter name
         * @param start             first value
         * @param stop              last value
         * @param step              positive increment
         * @param minimum           inclusive lower bound
         * @param maximum           inclusive upper bound
         * @param capAtSeriesLength whether the maximum is capped at series length
         * @return integer range domain
         * @since 0.22.8
         */
        public static ParameterDomain integerRange(String name, int start, int stop, int step, int minimum, int maximum,
                boolean capAtSeriesLength) {
            if (step <= 0) {
                throw new IllegalArgumentException("step must be positive");
            }
            if (start > stop) {
                throw new IllegalArgumentException("start cannot be greater than stop");
            }
            if (minimum > maximum) {
                throw new IllegalArgumentException("minimum cannot be greater than maximum");
            }
            long rawCount = ((long) stop - start) / step + 1L;
            if (rawCount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Integer range is too large: " + rawCount + " values (max " + Integer.MAX_VALUE + ")");
            }
            int count = (int) rawCount;
            List<String> values = new AbstractList<>() {
                @Override
                public String get(int index) {
                    return String.valueOf((long) start + (long) index * step);
                }

                @Override
                public int size() {
                    return count;
                }
            };
            return new ParameterDomain(name, values, (series, parameterName, rawValue) -> {
                int rawInteger;
                try {
                    rawInteger = Integer.parseInt(rawValue);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Parameter " + parameterName + " must be an integer, but was '" + rawValue + "'", ex);
                }
                int effectiveMaximum = capAtSeriesLength ? Math.max(minimum, Math.min(maximum, series.getBarCount()))
                        : maximum;
                int normalizedInteger = Math.max(minimum, Math.min(effectiveMaximum, rawInteger));
                boolean normalized = rawInteger != normalizedInteger;
                String note = normalized ? "clamped from " + rawInteger + " to " + normalizedInteger : "";
                return new ParameterValue(parameterName, rawValue, String.valueOf(normalizedInteger), normalized, note);
            });
        }
    }

    /**
     * One normalized parameter value.
     *
     * @param name       parameter name
     * @param rawValue   raw input value
     * @param value      normalized value
     * @param normalized whether the raw value changed
     * @param note       normalization note
     * @since 0.22.8
     */
    public record ParameterValue(String name, String rawValue, String value, boolean normalized, String note) {

        /**
         * Creates a validated parameter value.
         *
         * @since 0.22.8
         */
        public ParameterValue {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("rawValue cannot be blank");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value cannot be blank");
            }
            note = note == null ? "" : note;
        }
    }

    /**
     * Ordered normalized parameter set.
     *
     * @param values normalized values in domain order
     * @since 0.22.8
     */
    public record ParameterSet(List<ParameterValue> values) {

        /**
         * Creates a validated parameter set.
         *
         * @since 0.22.8
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
         * Returns the normalized value for a parameter.
         *
         * @param name parameter name
         * @return normalized value
         * @since 0.22.8
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
         * Returns the normalized value parsed as an integer.
         *
         * @param name parameter name
         * @return integer value
         * @since 0.22.8
         */
        public int intValue(String name) {
            String rawValue = value(name);
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Parameter " + name + " is not a valid integer: " + rawValue, ex);
            }
        }

        /**
         * Returns normalized values in domain order.
         *
         * @return ordered values
         * @since 0.22.8
         */
        public List<String> valuesInOrder() {
            return values.stream().map(ParameterValue::value).toList();
        }

        /**
         * Returns normalized values as a string array.
         *
         * @return ordered value array
         * @since 0.22.8
         */
        public String[] asStringArray() {
            return valuesInOrder().toArray(String[]::new);
        }

        /**
         * Returns normalized values keyed by parameter name.
         *
         * @return ordered parameter map
         * @since 0.22.8
         */
        public Map<String, String> asMap() {
            Map<String, String> map = new LinkedHashMap<>();
            for (ParameterValue value : values) {
                map.put(value.name(), value.value());
            }
            return Collections.unmodifiableMap(map);
        }

        /**
         * Returns a stable candidate identifier based on normalized values.
         *
         * <p>
         * Name/value tokens are escaped so ids are unambiguous and collision-free even
         * when values contain the separators ({@code |}, {@code =}).
         * </p>
         *
         * @return stable id
         * @since 0.22.8
         */
        public String stableId() {
            StringJoiner joiner = new StringJoiner("|");
            for (ParameterValue value : values) {
                joiner.add(escapeToken(value.name()) + "=" + escapeToken(value.value()));
            }
            return joiner.toString();
        }

        private static String escapeToken(String token) {
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
    }

    /**
     * Candidate descriptor used by parameter research and walk-forward tuning.
     *
     * @param id         stable candidate id
     * @param parameters normalized parameter set
     * @since 0.22.8
     */
    public record StrategyCandidate(String id, ParameterSet parameters) {

        /**
         * Creates a validated strategy candidate.
         *
         * @since 0.22.8
         */
        public StrategyCandidate {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            Objects.requireNonNull(parameters, "parameters");
            if (!id.equals(parameters.stableId())) {
                throw new IllegalArgumentException(
                        "id must equal the stable id of the parameters, but was '" + id + "'");
            }
        }
    }

    /**
     * Candidate-space generation output.
     *
     * @param candidates         valid normalized candidates
     * @param invalidCandidates  rejected or duplicate candidates
     * @param rawCandidateCount  declared raw combination count before normalization
     * @param candidateSpaceHash stable hash of valid candidate ids
     * @since 0.22.8
     */
    public record CandidateGenerationResult(List<StrategyCandidate> candidates,
            List<InvalidCandidate> invalidCandidates, long rawCandidateCount, String candidateSpaceHash) {

        /**
         * Creates a validated candidate generation result.
         *
         * @since 0.22.8
         */
        public CandidateGenerationResult {
            List<StrategyCandidate> copiedCandidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            List<InvalidCandidate> copiedInvalid = List
                    .copyOf(Objects.requireNonNull(invalidCandidates, "invalidCandidates"));
            candidates = copiedCandidates;
            invalidCandidates = copiedInvalid;
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("candidates cannot be empty");
            }
            Set<String> ids = new HashSet<>(candidates.size());
            for (StrategyCandidate candidate : candidates) {
                if (!ids.add(candidate.id())) {
                    throw new IllegalArgumentException("Duplicate candidate id: " + candidate.id());
                }
            }
            if (rawCandidateCount < 0 || rawCandidateCount < candidates.size() + invalidCandidates.size()) {
                throw new IllegalArgumentException(
                        "rawCandidateCount must be at least the number of generated candidates");
            }
            if (candidateSpaceHash == null || candidateSpaceHash.isBlank()) {
                throw new IllegalArgumentException("candidateSpaceHash cannot be blank");
            }
            if (!candidateSpaceHash.equals(hashCandidateIds(candidates))) {
                throw new IllegalArgumentException("candidateSpaceHash does not match the candidate ids");
            }
        }

        /**
         * Counts valid and rejected candidates.
         *
         * <p>
         * Generation-stage failures represent malformed raw values that never formed a
         * complete combination, so they are excluded from the generated count;
         * combination-validation and duplicate rows are included.
         * </p>
         *
         * @return total generated count
         * @since 0.22.8
         */
        public int generatedCandidateCount() {
            return candidates.size() + generatedInvalidCount();
        }

        private int generatedInvalidCount() {
            int count = 0;
            for (InvalidCandidate invalidCandidate : invalidCandidates) {
                if (invalidCandidate.stage() != CandidateFailureStage.GENERATION) {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * Candidate failure stage.
     *
     * @since 0.22.8
     */
    public enum CandidateFailureStage {
        /** Failure occurred while generating candidate values. */
        GENERATION,
        /** Validator rejected a complete combination. */
        COMBINATION_VALIDATION,
        /** Candidate normalized to an already-seen parameter set. */
        DUPLICATE_NORMALIZED,
        /** Strategy construction failed during training evaluation. */
        STRATEGY_BUILD,
        /** Strategy execution failed during training evaluation. */
        TRAINING_EXECUTION,
        /** Strategy construction failed during validation evaluation. */
        VALIDATION_STRATEGY_BUILD,
        /** Strategy execution failed during validation evaluation. */
        VALIDATION_EXECUTION,
        /** Indicator-distance signature construction failed during pruning. */
        PRUNING_INDICATOR
    }

    /**
     * Rejected candidate descriptor.
     *
     * @param candidateId stable or raw candidate id
     * @param parameters  candidate parameters when available
     * @param stage       failure stage
     * @param reason      failure reason
     * @since 0.22.8
     */
    public record InvalidCandidate(String candidateId, Map<String, String> parameters, CandidateFailureStage stage,
            String reason) {

        /**
         * Creates a validated invalid-candidate row.
         *
         * @since 0.22.8
         */
        public InvalidCandidate {
            if (candidateId == null || candidateId.isBlank()) {
                throw new IllegalArgumentException("candidateId cannot be blank");
            }
            parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
            Objects.requireNonNull(stage, "stage");
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * Pruning policy for representative selection.
     *
     * @since 0.22.8
     */
    public enum PruningPolicy {
        /** Keep every valid candidate. */
        NONE,
        /** Group candidates with identical executed trading records. */
        EXACT_TRADING_RECORD,
        /** Group candidates by fuzzy indicator RMS distance. */
        INDICATOR_DISTANCE,
        /** Group already-evaluated candidates by composite objective distance. */
        OBJECTIVE_DISTANCE
    }

    /**
     * Parameter research configuration.
     *
     * @param trainingBarCount     number of bars before holdout used for selection
     * @param validationBarCount   number of final bars held out for validation
     * @param pruningPolicy        representative-selection policy
     * @param rankingProfile       weighted ranking profile
     * @param topK                 number of selected candidates to validate
     * @param amount               trade amount
     * @param tradeType            starting trade type, or {@code null} to honor
     *                             each strategy's own starting type
     * @param distanceTolerance    fuzzy distance tolerance
     * @param indicatorFactory     optional indicator factory for indicator-distance
     *                             reports
     * @param transactionCostModel per-order transaction cost model
     * @param holdingCostModel     per-bar holding cost model
     * @param tradeExecutionModel  execution model applied to generated signals
     * @param tradingRecordFactory factory for per-candidate trading records
     * @param maxCombinations      maximum declared raw candidate combinations
     * @since 0.22.8
     */
    public record ResearchConfig(int trainingBarCount, int validationBarCount, PruningPolicy pruningPolicy,
            RankingProfile rankingProfile, int topK, Num amount, TradeType tradeType, double distanceTolerance,
            IndicatorFactory indicatorFactory, CostModel transactionCostModel, CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel, TradingRecordFactory tradingRecordFactory, int maxCombinations) {

        private static final TradingRecordFactory DEFAULT_TRADING_RECORD_FACTORY = (tradeType, startIndex, endIndex,
                transactionCostModel, holdingCostModel) -> new BaseTradingRecord(tradeType, startIndex, endIndex,
                        transactionCostModel, holdingCostModel);

        /**
         * Creates a validated research config.
         *
         * @since 0.22.8
         */
        public ResearchConfig {
            if (trainingBarCount < 0) {
                throw new IllegalArgumentException("trainingBarCount must be >= 0");
            }
            if (validationBarCount < 0) {
                throw new IllegalArgumentException("validationBarCount must be >= 0");
            }
            pruningPolicy = pruningPolicy == null ? PruningPolicy.EXACT_TRADING_RECORD : pruningPolicy;
            Objects.requireNonNull(rankingProfile, "rankingProfile");
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be > 0");
            }
            Objects.requireNonNull(amount, "amount");
            if (distanceTolerance < 0d || !Double.isFinite(distanceTolerance)) {
                throw new IllegalArgumentException("distanceTolerance must be finite and >= 0");
            }
            if (pruningPolicy == PruningPolicy.INDICATOR_DISTANCE && indicatorFactory == null) {
                throw new IllegalArgumentException("indicatorFactory is required for INDICATOR_DISTANCE pruning");
            }
            transactionCostModel = transactionCostModel == null ? new ZeroCostModel() : transactionCostModel;
            holdingCostModel = holdingCostModel == null ? new ZeroCostModel() : holdingCostModel;
            tradeExecutionModel = tradeExecutionModel == null ? new TradeOnNextOpenModel() : tradeExecutionModel;
            tradingRecordFactory = tradingRecordFactory == null ? DEFAULT_TRADING_RECORD_FACTORY : tradingRecordFactory;
            if (maxCombinations <= 0) {
                throw new IllegalArgumentException("maxCombinations must be > 0");
            }
        }

        /**
         * Creates a holdout research config using exact trading-record pruning.
         *
         * @param trainingBarCount   training bars; use {@code 0} for all pre-holdout
         * @param validationBarCount final bars held out for validation
         * @param rankingProfile     weighted ranking profile
         * @param amount             trade amount
         * @param topK               number of selected candidates to validate
         * @return research config
         * @since 0.22.8
         */
        public static ResearchConfig holdout(int trainingBarCount, int validationBarCount,
                RankingProfile rankingProfile, Num amount, int topK) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.EXACT_TRADING_RECORD,
                    rankingProfile, topK, amount, null, NO_DISTANCE, null, null, null, null, null,
                    DEFAULT_MAX_COMBINATIONS);
        }

        /**
         * Creates a holdout research config using all pre-holdout bars for training and
         * exact trading-record pruning.
         *
         * @param validationBarCount final bars held out for validation
         * @param rankingProfile     weighted ranking profile
         * @param amount             trade amount
         * @param topK               number of selected candidates to validate
         * @return research config
         * @since 0.22.8
         */
        public static ResearchConfig holdout(int validationBarCount, RankingProfile rankingProfile, Num amount,
                int topK) {
            return holdout(0, validationBarCount, rankingProfile, amount, topK);
        }

        /**
         * Returns a copy with a different pruning policy.
         *
         * @param policy pruning policy
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withPruningPolicy(PruningPolicy policy) {
            return new ResearchConfig(trainingBarCount, validationBarCount, policy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy configured for fuzzy indicator-distance reporting.
         *
         * @param tolerance tolerance in indicator units
         * @param factory   indicator factory
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withIndicatorDistance(double tolerance, IndicatorFactory factory) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.INDICATOR_DISTANCE,
                    rankingProfile, topK, amount, tradeType, tolerance, factory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy configured for objective-distance reporting.
         *
         * @param tolerance normalized objective-score tolerance
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withObjectiveDistance(double tolerance) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.OBJECTIVE_DISTANCE,
                    rankingProfile, topK, amount, tradeType, tolerance, indicatorFactory, transactionCostModel,
                    holdingCostModel, tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy with a different starting trade type.
         *
         * <p>
         * Pass {@code null} to honor each strategy's own starting type instead of
         * forcing a single type on every candidate.
         * </p>
         *
         * @param tradeType starting trade type, or {@code null}
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withTradeType(TradeType tradeType) {
            return new ResearchConfig(trainingBarCount, validationBarCount, pruningPolicy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy with a different declared-candidate budget.
         *
         * @param maxCombinations maximum declared raw combinations, inclusive
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withMaxCombinations(int maxCombinations) {
            return new ResearchConfig(trainingBarCount, validationBarCount, pruningPolicy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy with different transaction and holding costs.
         *
         * @param transactionCostModel per-order transaction cost model
         * @param holdingCostModel     per-bar holding cost model
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withCosts(CostModel transactionCostModel, CostModel holdingCostModel) {
            return new ResearchConfig(trainingBarCount, validationBarCount, pruningPolicy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy with a different trade execution model.
         *
         * @param tradeExecutionModel execution model applied to generated signals
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withTradeExecutionModel(TradeExecutionModel tradeExecutionModel) {
            return new ResearchConfig(trainingBarCount, validationBarCount, pruningPolicy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }

        /**
         * Returns a copy with a different trading-record factory.
         *
         * @param tradingRecordFactory factory for per-candidate trading records
         * @return updated config
         * @since 0.22.8
         */
        public ResearchConfig withTradingRecordFactory(TradingRecordFactory tradingRecordFactory) {
            return new ResearchConfig(trainingBarCount, validationBarCount, pruningPolicy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory, transactionCostModel, holdingCostModel,
                    tradeExecutionModel, tradingRecordFactory, maxCombinations);
        }
    }

    /**
     * Training and validation index window on the original series.
     *
     * @param trainingStartIndex   inclusive training start index
     * @param trainingEndIndex     inclusive training end index
     * @param validationStartIndex inclusive validation start index, or {@code -1}
     * @param validationEndIndex   inclusive validation end index, or {@code -1}
     * @since 0.22.8
     */
    public record ResearchWindow(int trainingStartIndex, int trainingEndIndex, int validationStartIndex,
            int validationEndIndex) {

        /**
         * Creates a validated research window.
         *
         * @since 0.22.8
         */
        public ResearchWindow {
            if (trainingStartIndex < 0 || trainingEndIndex < trainingStartIndex) {
                throw new IllegalArgumentException("training window is invalid");
            }
            if ((validationStartIndex == -1) != (validationEndIndex == -1)) {
                throw new IllegalArgumentException("validation indexes must both be present or absent");
            }
            if (validationStartIndex != -1 && validationEndIndex < validationStartIndex) {
                throw new IllegalArgumentException("validation window is invalid");
            }
            if (validationStartIndex != -1 && validationStartIndex <= trainingEndIndex) {
                throw new IllegalArgumentException("validation window must start after the training window");
            }
        }

        /**
         * Returns whether the report includes a validation window.
         *
         * @return true when validation indexes are present
         * @since 0.22.8
         */
        public boolean hasValidationWindow() {
            return validationStartIndex != -1;
        }
    }

    /**
     * Candidate pruning group.
     *
     * @param representativeId representative candidate id
     * @param memberIds        representative plus discarded member ids
     * @param reason           grouping reason
     * @param maximumDistance  maximum distance observed inside the group
     * @since 0.22.8
     */
    public record PruningGroup(String representativeId, List<String> memberIds, String reason, double maximumDistance) {

        /**
         * Creates a validated pruning group.
         *
         * <p>
         * The representative must be the first member and all member ids must be unique
         * and non-blank.
         * </p>
         *
         * @since 0.22.8
         */
        public PruningGroup {
            if (representativeId == null || representativeId.isBlank()) {
                throw new IllegalArgumentException("representativeId cannot be blank");
            }
            memberIds = List.copyOf(Objects.requireNonNull(memberIds, "memberIds"));
            if (memberIds.isEmpty()) {
                throw new IllegalArgumentException("memberIds cannot be empty");
            }
            if (!memberIds.getFirst().equals(representativeId)) {
                throw new IllegalArgumentException("the representative must be the first member");
            }
            Set<String> uniqueMembers = new HashSet<>(memberIds.size());
            for (String memberId : memberIds) {
                if (memberId == null || memberId.isBlank()) {
                    throw new IllegalArgumentException("memberIds cannot contain blank values");
                }
                if (!uniqueMembers.add(memberId)) {
                    throw new IllegalArgumentException("memberIds cannot contain duplicates");
                }
            }
            reason = reason == null ? "" : reason;
            if (maximumDistance < 0d || !Double.isFinite(maximumDistance)) {
                throw new IllegalArgumentException("maximumDistance must be finite and >= 0");
            }
        }

        /**
         * Returns candidates represented by the first group member.
         *
         * @return discarded member ids
         * @since 0.22.8
         */
        public List<String> discardedIds() {
            return memberIds.subList(1, memberIds.size());
        }
    }

    /**
     * Ranked candidate score row.
     *
     * @param candidateId    candidate id
     * @param strategyName   strategy name
     * @param rank           one-based rank
     * @param compositeScore weighted normalized score
     * @param metricValues   raw metric values keyed by criterion identity
     * @param representative whether this row is a representative candidate
     * @since 0.22.8
     */
    public record CandidateScore(String candidateId, String strategyName, int rank, Num compositeScore,
            Map<AnalysisCriterion, Num> metricValues, boolean representative) {

        /**
         * Creates a validated candidate score row.
         *
         * @since 0.22.8
         */
        public CandidateScore {
            if (candidateId == null || candidateId.isBlank()) {
                throw new IllegalArgumentException("candidateId cannot be blank");
            }
            strategyName = strategyName == null ? "" : strategyName;
            if (rank <= 0) {
                throw new IllegalArgumentException("rank must be > 0");
            }
            Objects.requireNonNull(compositeScore, "compositeScore");
            metricValues = Collections
                    .unmodifiableMap(new LinkedHashMap<>(metricValues == null ? Map.of() : metricValues));
        }
    }

    /**
     * Structured parameter research report.
     *
     * @param datasetId               dataset identifier
     * @param barCount                full-series bar count
     * @param window                  training and validation window
     * @param candidateSpaceHash      stable candidate-space hash
     * @param pruningPolicy           pruning policy
     * @param rawCandidateCount       declared raw combination count before
     *                                normalization
     * @param generatedCandidateCount generated candidate count
     * @param validCandidateCount     evaluated candidate count
     * @param invalidCandidateCount   rejected candidate count
     * @param candidates              normalized candidate space
     * @param baselineTopCandidateId  best full-space training candidate
     * @param selectedTopCandidateId  best representative training candidate
     * @param pruningGroups           pruning groups
     * @param baselineScores          full-space training scores before pruning
     * @param trainingScores          representative training scores
     * @param validationScores        selected holdout scores
     * @param invalidCandidates       rejected candidates
     * @param warnings                report warnings
     * @param trainingRuntimeReport   training runtime report
     * @param validationRuntimeReport validation runtime report
     * @since 0.22.8
     */
    public record ParameterResearchReport(String datasetId, int barCount, ResearchWindow window,
            String candidateSpaceHash, PruningPolicy pruningPolicy, long rawCandidateCount, int generatedCandidateCount,
            int validCandidateCount, int invalidCandidateCount, List<StrategyCandidate> candidates,
            String baselineTopCandidateId, String selectedTopCandidateId, List<PruningGroup> pruningGroups,
            List<CandidateScore> baselineScores, List<CandidateScore> trainingScores,
            List<CandidateScore> validationScores, List<InvalidCandidate> invalidCandidates, List<String> warnings,
            BacktestRuntimeReport trainingRuntimeReport, BacktestRuntimeReport validationRuntimeReport) {

        /**
         * Creates a validated research report.
         *
         * @since 0.22.8
         */
        public ParameterResearchReport {
            datasetId = datasetId == null || datasetId.isBlank() ? "series" : datasetId;
            if (barCount < 0 || generatedCandidateCount < 0 || validCandidateCount < 0 || invalidCandidateCount < 0
                    || rawCandidateCount < 0) {
                throw new IllegalArgumentException("report counts must be >= 0");
            }
            Objects.requireNonNull(window, "window");
            if (candidateSpaceHash == null || candidateSpaceHash.isBlank()) {
                throw new IllegalArgumentException("candidateSpaceHash cannot be blank");
            }
            Objects.requireNonNull(pruningPolicy, "pruningPolicy");
            List<StrategyCandidate> copiedCandidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            List<PruningGroup> copiedPruningGroups = List
                    .copyOf(Objects.requireNonNull(pruningGroups, "pruningGroups"));
            List<CandidateScore> copiedBaselineScores = List
                    .copyOf(Objects.requireNonNull(baselineScores, "baselineScores"));
            List<CandidateScore> copiedTrainingScores = List
                    .copyOf(Objects.requireNonNull(trainingScores, "trainingScores"));
            List<CandidateScore> copiedValidationScores = List
                    .copyOf(Objects.requireNonNull(validationScores, "validationScores"));
            List<InvalidCandidate> copiedInvalidCandidates = List
                    .copyOf(Objects.requireNonNull(invalidCandidates, "invalidCandidates"));
            List<String> copiedWarnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            candidates = copiedCandidates;
            pruningGroups = copiedPruningGroups;
            baselineScores = copiedBaselineScores;
            trainingScores = copiedTrainingScores;
            validationScores = copiedValidationScores;
            invalidCandidates = copiedInvalidCandidates;
            warnings = copiedWarnings;
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("candidates cannot be empty");
            }
            if (rawCandidateCount < generatedCandidateCount) {
                throw new IllegalArgumentException("rawCandidateCount must be >= generatedCandidateCount");
            }
            if (generatedCandidateCount < validCandidateCount) {
                throw new IllegalArgumentException("generatedCandidateCount must be >= validCandidateCount");
            }
            if (validCandidateCount > candidates.size()) {
                throw new IllegalArgumentException("validCandidateCount cannot exceed the candidate space size");
            }
            if (invalidCandidateCount != invalidCandidates.size()) {
                throw new IllegalArgumentException("invalidCandidateCount must match invalidCandidates.size()");
            }
            Objects.requireNonNull(trainingRuntimeReport, "trainingRuntimeReport");
            Objects.requireNonNull(validationRuntimeReport, "validationRuntimeReport");
        }

        /**
         * Counts representative candidates after pruning.
         *
         * @return representative count
         * @since 0.22.8
         */
        public int representativeCount() {
            return pruningGroups.size();
        }

        /**
         * Counts candidates removed by pruning.
         *
         * @return pruned candidate count
         * @since 0.22.8
         */
        public int prunedCandidateCount() {
            int members = pruningGroups.stream().mapToInt(group -> group.memberIds().size()).sum();
            return members - pruningGroups.size();
        }

        /**
         * Returns invalid-candidate counts broken down by failure stage.
         *
         * @return unmodifiable stage-count map in declaration order
         * @since 0.22.8
         */
        public Map<CandidateFailureStage, Integer> invalidCandidateCountByStage() {
            Map<CandidateFailureStage, Integer> counts = new EnumMap<>(CandidateFailureStage.class);
            for (InvalidCandidate invalidCandidate : invalidCandidates) {
                counts.merge(invalidCandidate.stage(), 1, Integer::sum);
            }
            return Collections.unmodifiableMap(counts);
        }

        /**
         * Formats the invalid-candidate stage breakdown.
         *
         * @return stage breakdown text
         * @since 0.22.8
         */
        public String invalidStageBreakdown() {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (Map.Entry<CandidateFailureStage, Integer> entry : invalidCandidateCountByStage().entrySet()) {
                joiner.add(stageLabel(entry.getKey()) + "=" + entry.getValue());
            }
            return joiner.toString();
        }

        private static String stageLabel(CandidateFailureStage stage) {
            return switch (stage) {
            case GENERATION -> "generation";
            case COMBINATION_VALIDATION -> "combination";
            case DUPLICATE_NORMALIZED -> "duplicate";
            case STRATEGY_BUILD -> "strategy-build";
            case TRAINING_EXECUTION -> "training-execution";
            case VALIDATION_STRATEGY_BUILD -> "validation-strategy-build";
            case VALIDATION_EXECUTION -> "validation-execution";
            case PRUNING_INDICATOR -> "pruning-indicator";
            };
        }

        /**
         * Formats a concise human-readable report summary.
         *
         * @return summary text
         * @since 0.22.8
         */
        public String formatSummary() {
            return formatSummary(5);
        }

        /**
         * Formats a concise human-readable report summary.
         *
         * @param maxRows maximum number of score rows to include for each section
         * @return summary text
         * @since 0.22.8
         */
        public String formatSummary(int maxRows) {
            if (maxRows < 0) {
                throw new IllegalArgumentException("maxRows must be >= 0");
            }
            StringBuilder builder = new StringBuilder();
            builder.append("Parameter research '")
                    .append(datasetId)
                    .append("'")
                    .append(System.lineSeparator())
                    .append("Candidate space: hash=")
                    .append(candidateSpaceHash)
                    .append(", raw=")
                    .append(rawCandidateCount)
                    .append(", generated=")
                    .append(generatedCandidateCount)
                    .append(", valid=")
                    .append(validCandidateCount)
                    .append(", representatives=")
                    .append(representativeCount())
                    .append(", pruned=")
                    .append(prunedCandidateCount())
                    .append(", invalid=")
                    .append(invalidCandidateCount);
            if (invalidCandidateCount > 0) {
                builder.append(", stages=").append(invalidStageBreakdown());
            }
            builder.append(", policy=").append(pruningPolicy);
            builder.append(System.lineSeparator())
                    .append("Windows: bars=")
                    .append(barCount)
                    .append(", training=")
                    .append(window.trainingStartIndex())
                    .append('-')
                    .append(window.trainingEndIndex());
            if (window.hasValidationWindow()) {
                builder.append(", validation=")
                        .append(window.validationStartIndex())
                        .append('-')
                        .append(window.validationEndIndex());
            } else {
                builder.append(", validation=none");
            }
            builder.append(System.lineSeparator())
                    .append("Selection: baselineTop=")
                    .append(baselineTopCandidateId)
                    .append(", selectedTop=")
                    .append(selectedTopCandidateId);
            appendScores(builder, "Training top candidates", trainingScores, maxRows);
            appendScores(builder, "Validation top candidates", validationScores, maxRows);
            if (!warnings.isEmpty()) {
                builder.append(System.lineSeparator()).append("Warnings:");
                for (String warning : warnings) {
                    builder.append(System.lineSeparator()).append("- ").append(warning);
                }
            }
            return builder.toString();
        }

        private static void appendScores(StringBuilder builder, String label, List<CandidateScore> scores,
                int maxRows) {
            builder.append(System.lineSeparator()).append(label).append(":");
            if (scores.isEmpty()) {
                builder.append(" none");
                return;
            }
            int limit = Math.min(maxRows, scores.size());
            for (int i = 0; i < limit; i++) {
                CandidateScore score = scores.get(i);
                builder.append(System.lineSeparator())
                        .append("- #")
                        .append(score.rank())
                        .append(' ')
                        .append(score.candidateId())
                        .append(" score=")
                        .append(score.compositeScore());
                if (!score.metricValues().isEmpty()) {
                    builder.append(" metrics=").append(formatMetrics(score.metricValues()));
                }
            }
            if (scores.size() > limit) {
                builder.append(System.lineSeparator()).append("- ... ").append(scores.size() - limit).append(" more");
            }
        }

        private static String formatMetrics(Map<AnalysisCriterion, Num> metricValues) {
            Map<String, Integer> labelCounts = new LinkedHashMap<>();
            for (AnalysisCriterion criterion : metricValues.keySet()) {
                labelCounts.merge(criterion.toString(), 1, Integer::sum);
            }
            Map<String, Integer> occurrenceByLabel = new LinkedHashMap<>();
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (Map.Entry<AnalysisCriterion, Num> entry : metricValues.entrySet()) {
                String label = entry.getKey().toString();
                int occurrence = occurrenceByLabel.merge(label, 1, Integer::sum);
                String displayLabel = labelCounts.get(label) > 1 ? label + " #" + occurrence : label;
                joiner.add(displayLabel + "=" + entry.getValue());
            }
            return joiner.toString();
        }
    }

    private record ExecutionBundle(List<StrategyCandidate> candidates, BacktestExecutionResult result,
            List<InvalidCandidate> invalidCandidates) {
    }

    private record ValidationBundle(List<CandidateScore> validationScores, BacktestRuntimeReport runtimeReport,
            List<InvalidCandidate> invalidCandidates) {
    }

    private record PruningResult(List<PruningGroup> groups, List<InvalidCandidate> invalidCandidates) {
    }

    private record ResearchExecutor(BarSeriesManager manager, TradingStatementGenerator statementGenerator) {

        private ResearchExecutor(BarSeries series, ResearchConfig config) {
            this(new BarSeriesManager(series, config.transactionCostModel(), config.holdingCostModel(),
                    config.tradeExecutionModel(), config.tradingRecordFactory()), new TradingStatementGenerator());
        }
    }

    private static final class PruningGroupBuilder {

        private final String representativeId;
        private final String reason;
        private final List<String> memberIds = new ArrayList<>();
        private double maximumDistance;

        private PruningGroupBuilder(String representativeId, String reason) {
            this.representativeId = representativeId;
            this.reason = reason;
        }

        private void add(String candidateId, double distance) {
            memberIds.add(candidateId);
            maximumDistance = Math.max(maximumDistance, distance);
        }

        private PruningGroup build() {
            return new PruningGroup(representativeId, memberIds, reason, maximumDistance);
        }
    }
}
