/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankedTradingStatement;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
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
 * @since 0.22.7
 */
public final class ParameterResearch {

    private static final int SHORT_HASH_LENGTH = 12;
    private static final double NO_DISTANCE = 0d;

    private ParameterResearch() {
    }

    /**
     * Generates a deterministic candidate space from one or more parameter domains.
     *
     * @param series  series used by domain normalizers
     * @param domains ordered parameter domains
     * @return candidate generation result
     * @since 0.22.7
     */
    public static CandidateGenerationResult generateCandidateSpace(BarSeries series, List<ParameterDomain> domains) {
        return generateCandidateSpace(series, domains, CandidateValidator.acceptAll());
    }

    /**
     * Generates a deterministic candidate space and captures rejected combinations.
     *
     * @param series    series used by domain normalizers
     * @param domains   ordered parameter domains
     * @param validator optional cross-parameter validator
     * @return candidate generation result
     * @since 0.22.7
     */
    public static CandidateGenerationResult generateCandidateSpace(BarSeries series, List<ParameterDomain> domains,
            CandidateValidator validator) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(domains, "domains");
        CandidateValidator effectiveValidator = validator == null ? CandidateValidator.acceptAll() : validator;
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        if (domains.isEmpty()) {
            throw new IllegalArgumentException("domains cannot be empty");
        }

        List<List<ParameterValue>> normalizedValuesByDomain = new ArrayList<>(domains.size());
        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        Set<String> domainNames = new LinkedHashSet<>();
        for (ParameterDomain domain : domains) {
            Objects.requireNonNull(domain, "domains cannot contain null values");
            if (!domainNames.add(domain.name())) {
                throw new IllegalArgumentException("Duplicate parameter domain name: " + domain.name());
            }

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
                        CandidateFailureStage.GENERATION, ex.getMessage()));
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
        return new CandidateGenerationResult(candidates, invalidCandidates, hashCandidateIds(candidates));
    }

    /**
     * Runs parameter research by generating candidates from the training window,
     * selecting on training data, and validating selected representatives on a
     * holdout window.
     *
     * @param series          full series
     * @param domains         ordered parameter domains
     * @param validator       optional cross-parameter validator
     * @param strategyFactory strategy factory
     * @param config          research configuration
     * @return structured research report
     * @since 0.22.7
     */
    public static ParameterResearchReport run(BarSeries series, List<ParameterDomain> domains,
            CandidateValidator validator, StrategyFactory strategyFactory, ResearchConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(domains, "domains");
        Objects.requireNonNull(strategyFactory, "strategyFactory");
        Objects.requireNonNull(config, "config");
        ResearchWindow window = resolveWindow(series, config);
        BarSeries trainingSeries = series.getSubSeries(window.trainingStartIndex(), window.trainingEndIndex() + 1);
        CandidateGenerationResult candidateSpace = generateCandidateSpace(trainingSeries, domains, validator);
        return runWithCandidateSpace(series, trainingSeries, candidateSpace, strategyFactory, config, window,
                List.of());
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
     * @since 0.22.7
     */
    public static ParameterResearchReport run(BarSeries series, CandidateGenerationResult candidateSpace,
            StrategyFactory strategyFactory, ResearchConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(candidateSpace, "candidateSpace");
        Objects.requireNonNull(strategyFactory, "strategyFactory");
        Objects.requireNonNull(config, "config");
        ResearchWindow window = resolveWindow(series, config);
        BarSeries trainingSeries = series.getSubSeries(window.trainingStartIndex(), window.trainingEndIndex() + 1);
        return runWithCandidateSpace(series, trainingSeries, candidateSpace, strategyFactory, config, window,
                List.of("Candidate space supplied by caller; ensure it was generated from training data only."));
    }

    /**
     * Converts generated strategy candidates into walk-forward tuner candidates.
     *
     * @param candidateSpace generated candidate space
     * @return candidates suitable for {@code WalkForwardTuner}
     * @since 0.22.7
     */
    public static List<WalkForwardCandidate<ParameterSet>> toWalkForwardCandidates(
            CandidateGenerationResult candidateSpace) {
        Objects.requireNonNull(candidateSpace, "candidateSpace");
        return candidateSpace.candidates()
                .stream()
                .map(candidate -> new WalkForwardCandidate<>(candidate.id(), candidate.parameters()))
                .toList();
    }

    private static ParameterResearchReport runWithCandidateSpace(BarSeries fullSeries, BarSeries trainingSeries,
            CandidateGenerationResult candidateSpace, StrategyFactory strategyFactory, ResearchConfig config,
            ResearchWindow window, List<String> initialWarnings) {
        if (fullSeries.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        ExecutionBundle trainingExecution = executeCandidates(trainingSeries, candidateSpace.candidates(),
                strategyFactory, config.amount(), config.tradeType(), CandidateFailureStage.STRATEGY_BUILD);
        if (trainingExecution.candidates().isEmpty()) {
            throw new IllegalArgumentException("No strategies could be built for the generated candidate space: "
                    + trainingExecution.invalidCandidates());
        }

        BacktestExecutionResult trainingResult = trainingExecution.result();
        List<RankedTradingStatement> baselineRanking = trainingResult.rankTradingStatements(config.rankingProfile());
        List<PruningGroup> pruningGroups = buildPruningGroups(config, trainingSeries, trainingExecution,
                baselineRanking);
        Set<String> representativeIds = representativeIds(pruningGroups);
        List<CandidateScore> baselineScores = toScores(baselineRanking, trainingExecution.candidates(),
                representativeIds);
        List<StrategyCandidate> representativeCandidates = filterCandidates(trainingExecution.candidates(),
                representativeIds);
        List<TradingStatement> representativeStatements = filterStatements(trainingExecution.candidates(),
                trainingResult.tradingStatements(), representativeIds);

        BacktestExecutionResult representativeTrainingResult = new BacktestExecutionResult(trainingSeries,
                representativeStatements, trainingResult.runtimeReport());
        List<RankedTradingStatement> representativeRanking = representativeTrainingResult
                .rankTradingStatements(config.rankingProfile());
        List<CandidateScore> trainingScores = toScores(representativeRanking, representativeCandidates,
                representativeIds);

        List<StrategyCandidate> selectedCandidates = topCandidates(trainingScores, representativeCandidates,
                config.topK());
        ValidationBundle validationBundle = validateSelected(fullSeries, selectedCandidates, strategyFactory, config,
                window);

        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        invalidCandidates.addAll(candidateSpace.invalidCandidates());
        invalidCandidates.addAll(trainingExecution.invalidCandidates());
        invalidCandidates.addAll(validationBundle.invalidCandidates());

        List<String> warnings = new ArrayList<>(initialWarnings);
        warnings.addAll(windowWarnings(config, window, fullSeries));
        warnings.addAll(policyWarnings(config, validationBundle.validationScores()));

        String baselineTopCandidateId = baselineRanking.isEmpty() ? ""
                : trainingExecution.candidates().get(baselineRanking.getFirst().originalIndex()).id();
        String selectedTopCandidateId = trainingScores.isEmpty() ? "" : trainingScores.getFirst().candidateId();

        return new ParameterResearchReport(resolveDatasetId(fullSeries), fullSeries.getBarCount(), window,
                candidateSpace.candidateSpaceHash(), config.pruningPolicy(), candidateSpace.generatedCandidateCount(),
                trainingExecution.candidates().size(), invalidCandidates.size(), candidateSpace.candidates(),
                baselineTopCandidateId, selectedTopCandidateId, pruningGroups, baselineScores, trainingScores,
                validationBundle.validationScores(), invalidCandidates, warnings, trainingResult.runtimeReport(),
                validationBundle.runtimeReport());
    }

    private static ExecutionBundle executeCandidates(BarSeries series, List<StrategyCandidate> candidates,
            StrategyFactory strategyFactory, Num amount, TradeType tradeType, CandidateFailureStage failureStage) {
        List<StrategyCandidate> executableCandidates = new ArrayList<>();
        List<Strategy> strategies = new ArrayList<>();
        List<InvalidCandidate> invalidCandidates = new ArrayList<>();
        for (StrategyCandidate candidate : candidates) {
            try {
                Strategy strategy = Objects.requireNonNull(strategyFactory.create(series, candidate.parameters()),
                        "strategyFactory returned null");
                strategies.add(strategy);
                executableCandidates.add(candidate);
            } catch (RuntimeException ex) {
                invalidCandidates.add(new InvalidCandidate(candidate.id(), candidate.parameters().asMap(), failureStage,
                        ex.getMessage()));
            }
        }

        BacktestExecutionResult result = strategies.isEmpty()
                ? new BacktestExecutionResult(series, List.of(), BacktestRuntimeReport.empty())
                : new BacktestExecutor(series).executeWithRuntimeReport(strategies, amount, tradeType);
        return new ExecutionBundle(executableCandidates, strategies, result, invalidCandidates);
    }

    private static ValidationBundle validateSelected(BarSeries fullSeries, List<StrategyCandidate> selectedCandidates,
            StrategyFactory strategyFactory, ResearchConfig config, ResearchWindow window) {
        if (!window.hasValidationWindow() || selectedCandidates.isEmpty()) {
            return new ValidationBundle(List.of(), BacktestRuntimeReport.empty(), List.of());
        }

        BarSeries validationSeries = fullSeries.getSubSeries(window.validationStartIndex(),
                window.validationEndIndex() + 1);
        ExecutionBundle execution = executeCandidates(validationSeries, selectedCandidates, strategyFactory,
                config.amount(), config.tradeType(), CandidateFailureStage.VALIDATION_STRATEGY_BUILD);
        if (execution.candidates().isEmpty()) {
            return new ValidationBundle(List.of(), execution.result().runtimeReport(), execution.invalidCandidates());
        }
        List<RankedTradingStatement> ranked = execution.result().rankTradingStatements(config.rankingProfile());
        Set<String> selectedIds = candidateIds(selectedCandidates);
        List<CandidateScore> validationScores = toScores(ranked, execution.candidates(), selectedIds);
        return new ValidationBundle(validationScores, execution.result().runtimeReport(),
                execution.invalidCandidates());
    }

    private static List<PruningGroup> buildPruningGroups(ResearchConfig config, BarSeries trainingSeries,
            ExecutionBundle execution, List<RankedTradingStatement> baselineRanking) {
        return switch (config.pruningPolicy()) {
        case NONE -> noneGroups(execution.candidates());
        case EXACT_SIGNAL -> exactSignatureGroups(execution,
                candidateIndex -> signalSignature(trainingSeries, execution.strategies().get(candidateIndex)),
                "exact signal sequence", NO_DISTANCE);
        case EXACT_TRADING_RECORD -> exactSignatureGroups(execution,
                candidateIndex -> tradingRecordSignature(
                        execution.result().tradingStatements().get(candidateIndex).getTradingRecord()),
                "exact trading record", NO_DISTANCE);
        case INDICATOR_DISTANCE -> indicatorDistanceGroups(config, trainingSeries, execution);
        case OBJECTIVE_DISTANCE -> objectiveDistanceGroups(config, execution, baselineRanking);
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
            SignatureSupplier signatureSupplier, String reason, double maximumDistance) {
        Map<String, PruningGroupBuilder> groupsBySignature = new LinkedHashMap<>();
        for (int i = 0; i < execution.candidates().size(); i++) {
            StrategyCandidate candidate = execution.candidates().get(i);
            String signature = signatureSupplier.signatureFor(i);
            PruningGroupBuilder builder = groupsBySignature.computeIfAbsent(signature,
                    ignored -> new PruningGroupBuilder(candidate.id(), reason));
            builder.add(candidate.id(), maximumDistance);
        }
        return groupsBySignature.values().stream().map(PruningGroupBuilder::build).toList();
    }

    private static List<PruningGroup> indicatorDistanceGroups(ResearchConfig config, BarSeries trainingSeries,
            ExecutionBundle execution) {
        if (config.indicatorFactory() == null) {
            throw new IllegalArgumentException("indicatorFactory is required for INDICATOR_DISTANCE pruning");
        }

        List<double[]> signatures = new ArrayList<>(execution.candidates().size());
        for (StrategyCandidate candidate : execution.candidates()) {
            Indicator<Num> indicator = Objects.requireNonNull(
                    config.indicatorFactory().create(trainingSeries, candidate.parameters()),
                    "indicatorFactory returned null");
            signatures.add(captureIndicatorSignature(trainingSeries, indicator));
        }

        List<PruningGroupBuilder> builders = new ArrayList<>();
        List<double[]> representativeSignatures = new ArrayList<>();
        for (int i = 0; i < execution.candidates().size(); i++) {
            StrategyCandidate candidate = execution.candidates().get(i);
            double[] signature = signatures.get(i);
            boolean matched = false;
            for (int groupIndex = 0; groupIndex < representativeSignatures.size(); groupIndex++) {
                double distance = rmsDistance(representativeSignatures.get(groupIndex), signature);
                if (distance <= config.distanceTolerance()) {
                    builders.get(groupIndex).add(candidate.id(), distance);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                PruningGroupBuilder builder = new PruningGroupBuilder(candidate.id(), "indicator RMS distance");
                builder.add(candidate.id(), NO_DISTANCE);
                builders.add(builder);
                representativeSignatures.add(signature);
            }
        }
        return builders.stream().map(PruningGroupBuilder::build).toList();
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

    private static String signalSignature(BarSeries series, Strategy strategy) {
        StringBuilder builder = new StringBuilder();
        builder.append("unstable=").append(strategy.getUnstableBars()).append(';');
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            builder.append(index)
                    .append(':')
                    .append(strategy.shouldEnter(index))
                    .append('/')
                    .append(strategy.shouldExit(index))
                    .append(';');
        }
        return builder.toString();
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

    private static double[] captureIndicatorSignature(BarSeries series, Indicator<Num> indicator) {
        int startIndex = Math.max(series.getBeginIndex(), indicator.getCountOfUnstableBars());
        if (startIndex > series.getEndIndex()) {
            return new double[0];
        }
        double[] values = new double[series.getEndIndex() - startIndex + 1];
        int offset = 0;
        for (int index = startIndex; index <= series.getEndIndex(); index++) {
            double value = indicator.getValue(index).doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Indicator produced a non-finite value at index " + index);
            }
            values[offset] = value;
            offset++;
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
                    ranked.compositeScore(), toMetricMap(ranked.rawScores()),
                    representativeIds.contains(candidate.id())));
            rank++;
        }
        return List.copyOf(scores);
    }

    private static Map<String, Num> toMetricMap(Map<AnalysisCriterion, Num> rawScores) {
        Map<String, Num> metrics = new LinkedHashMap<>();
        for (Map.Entry<AnalysisCriterion, Num> entry : rawScores.entrySet()) {
            metrics.put(entry.getKey().getClass().getSimpleName(), entry.getValue());
        }
        return metrics;
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
                    "Research windows use original series indexes; generated training/validation sub-series restart at index 0.");
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
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface StrategyFactory {

        /**
         * Builds a strategy for one candidate on the supplied series.
         *
         * @param series     target series
         * @param parameters normalized parameter set
         * @return strategy to evaluate
         * @since 0.22.7
         */
        Strategy create(BarSeries series, ParameterSet parameters);
    }

    /**
     * Indicator construction callback for explicit fuzzy indicator-distance
     * reports.
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface IndicatorFactory {

        /**
         * Builds an indicator for one candidate on the supplied series.
         *
         * @param series     target series
         * @param parameters normalized parameter set
         * @return indicator signature source
         * @since 0.22.7
         */
        Indicator<Num> create(BarSeries series, ParameterSet parameters);
    }

    /**
     * Cross-parameter validation callback.
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface CandidateValidator {

        /**
         * Accepts or rejects one parameter set.
         *
         * @param parameters normalized parameter set
         * @since 0.22.7
         */
        void validate(ParameterSet parameters);

        /**
         * Returns a validator that accepts all parameter sets.
         *
         * @return no-op validator
         * @since 0.22.7
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
     * @since 0.22.7
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
         * @since 0.22.7
         */
        ParameterValue normalize(BarSeries series, String name, String rawValue);
    }

    /**
     * Parameter domain for candidate-space generation.
     *
     * @param name       parameter name
     * @param rawValues  ordered raw values
     * @param normalizer value normalizer
     * @since 0.22.7
     */
    public record ParameterDomain(String name, List<String> rawValues, ParameterNormalizer normalizer) {

        /**
         * Creates a validated parameter domain.
         *
         * @since 0.22.7
         */
        public ParameterDomain {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            rawValues = List.copyOf(Objects.requireNonNull(rawValues, "rawValues"));
            if (rawValues.isEmpty()) {
                throw new IllegalArgumentException("rawValues cannot be empty");
            }
            for (String rawValue : rawValues) {
                if (rawValue == null || rawValue.isBlank()) {
                    throw new IllegalArgumentException("rawValues cannot contain blank values");
                }
            }
            Objects.requireNonNull(normalizer, "normalizer");
        }

        /**
         * Creates a domain with literal string values.
         *
         * @param name   parameter name
         * @param values ordered values
         * @return parameter domain
         * @since 0.22.7
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
         * @since 0.22.7
         */
        public static ParameterDomain integerRange(String name, int start, int stop, int step) {
            return integerRange(name, start, stop, step, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
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
         * @since 0.22.7
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
            List<String> values = new ArrayList<>();
            for (long value = start; value <= stop; value += step) {
                values.add(String.valueOf(value));
            }
            return new ParameterDomain(name, values, (series, parameterName, rawValue) -> {
                int rawInteger = Integer.parseInt(rawValue);
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
     * @since 0.22.7
     */
    public record ParameterValue(String name, String rawValue, String value, boolean normalized, String note) {

        /**
         * Creates a validated parameter value.
         *
         * @since 0.22.7
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
     * @since 0.22.7
     */
    public record ParameterSet(List<ParameterValue> values) {

        /**
         * Creates a validated parameter set.
         *
         * @since 0.22.7
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
         * @since 0.22.7
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
         * @since 0.22.7
         */
        public int intValue(String name) {
            return Integer.parseInt(value(name));
        }

        /**
         * Returns normalized values in domain order.
         *
         * @return ordered values
         * @since 0.22.7
         */
        public List<String> valuesInOrder() {
            return values.stream().map(ParameterValue::value).toList();
        }

        /**
         * Returns normalized values as a string array.
         *
         * @return ordered value array
         * @since 0.22.7
         */
        public String[] asStringArray() {
            return valuesInOrder().toArray(String[]::new);
        }

        /**
         * Returns normalized values keyed by parameter name.
         *
         * @return ordered parameter map
         * @since 0.22.7
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
         * @return stable id
         * @since 0.22.7
         */
        public String stableId() {
            StringJoiner joiner = new StringJoiner("|");
            for (ParameterValue value : values) {
                joiner.add(value.name() + "=" + value.value());
            }
            return joiner.toString();
        }
    }

    /**
     * Candidate descriptor used by parameter research and walk-forward tuning.
     *
     * @param id         stable candidate id
     * @param parameters normalized parameter set
     * @since 0.22.7
     */
    public record StrategyCandidate(String id, ParameterSet parameters) {

        /**
         * Creates a validated strategy candidate.
         *
         * @since 0.22.7
         */
        public StrategyCandidate {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            Objects.requireNonNull(parameters, "parameters");
        }
    }

    /**
     * Candidate-space generation output.
     *
     * @param candidates         valid normalized candidates
     * @param invalidCandidates  rejected or duplicate candidates
     * @param candidateSpaceHash stable hash of valid candidate ids
     * @since 0.22.7
     */
    public record CandidateGenerationResult(List<StrategyCandidate> candidates,
            List<InvalidCandidate> invalidCandidates, String candidateSpaceHash) {

        /**
         * Creates a validated candidate generation result.
         *
         * @since 0.22.7
         */
        public CandidateGenerationResult {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            invalidCandidates = List.copyOf(Objects.requireNonNull(invalidCandidates, "invalidCandidates"));
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("candidates cannot be empty");
            }
            if (candidateSpaceHash == null || candidateSpaceHash.isBlank()) {
                throw new IllegalArgumentException("candidateSpaceHash cannot be blank");
            }
        }

        /**
         * Counts valid and rejected candidates.
         *
         * @return total generated count
         * @since 0.22.7
         */
        public int generatedCandidateCount() {
            return candidates.size() + invalidCandidates.size();
        }
    }

    /**
     * Candidate failure stage.
     *
     * @since 0.22.7
     */
    public enum CandidateFailureStage {
        /** Failure occurred while generating candidate values. */
        GENERATION,
        /** Candidate normalized to an already-seen parameter set. */
        DUPLICATE_NORMALIZED,
        /** Strategy construction failed during training evaluation. */
        STRATEGY_BUILD,
        /** Strategy construction failed during validation evaluation. */
        VALIDATION_STRATEGY_BUILD
    }

    /**
     * Rejected candidate descriptor.
     *
     * @param candidateId stable or raw candidate id
     * @param parameters  candidate parameters when available
     * @param stage       failure stage
     * @param reason      failure reason
     * @since 0.22.7
     */
    public record InvalidCandidate(String candidateId, Map<String, String> parameters, CandidateFailureStage stage,
            String reason) {

        /**
         * Creates a validated invalid-candidate row.
         *
         * @since 0.22.7
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
     * @since 0.22.7
     */
    public enum PruningPolicy {
        /** Keep every valid candidate. */
        NONE,
        /** Group candidates with identical entry/exit signal sequences. */
        EXACT_SIGNAL,
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
     * @param trainingBarCount   number of bars before holdout used for selection
     * @param validationBarCount number of final bars held out for validation
     * @param pruningPolicy      representative-selection policy
     * @param rankingProfile     weighted ranking profile
     * @param topK               number of selected candidates to validate
     * @param amount             trade amount
     * @param tradeType          starting trade type
     * @param distanceTolerance  fuzzy distance tolerance
     * @param indicatorFactory   optional indicator factory for indicator-distance
     *                           reports
     * @since 0.22.7
     */
    public record ResearchConfig(int trainingBarCount, int validationBarCount, PruningPolicy pruningPolicy,
            RankingProfile rankingProfile, int topK, Num amount, TradeType tradeType, double distanceTolerance,
            IndicatorFactory indicatorFactory) {

        /**
         * Creates a validated research config.
         *
         * @since 0.22.7
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
            tradeType = tradeType == null ? Trade.TradeType.BUY : tradeType;
            if (distanceTolerance < 0d || !Double.isFinite(distanceTolerance)) {
                throw new IllegalArgumentException("distanceTolerance must be finite and >= 0");
            }
            if (pruningPolicy == PruningPolicy.INDICATOR_DISTANCE && indicatorFactory == null) {
                throw new IllegalArgumentException("indicatorFactory is required for INDICATOR_DISTANCE pruning");
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
         * @since 0.22.7
         */
        public static ResearchConfig holdout(int trainingBarCount, int validationBarCount,
                RankingProfile rankingProfile, Num amount, int topK) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.EXACT_TRADING_RECORD,
                    rankingProfile, topK, amount, Trade.TradeType.BUY, NO_DISTANCE, null);
        }

        /**
         * Returns a copy with a different pruning policy.
         *
         * @param policy pruning policy
         * @return updated config
         * @since 0.22.7
         */
        public ResearchConfig withPruningPolicy(PruningPolicy policy) {
            return new ResearchConfig(trainingBarCount, validationBarCount, policy, rankingProfile, topK, amount,
                    tradeType, distanceTolerance, indicatorFactory);
        }

        /**
         * Returns a copy configured for fuzzy indicator-distance reporting.
         *
         * @param tolerance tolerance in indicator units
         * @param factory   indicator factory
         * @return updated config
         * @since 0.22.7
         */
        public ResearchConfig withIndicatorDistance(double tolerance, IndicatorFactory factory) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.INDICATOR_DISTANCE,
                    rankingProfile, topK, amount, tradeType, tolerance, factory);
        }

        /**
         * Returns a copy configured for objective-distance reporting.
         *
         * @param tolerance normalized objective-score tolerance
         * @return updated config
         * @since 0.22.7
         */
        public ResearchConfig withObjectiveDistance(double tolerance) {
            return new ResearchConfig(trainingBarCount, validationBarCount, PruningPolicy.OBJECTIVE_DISTANCE,
                    rankingProfile, topK, amount, tradeType, tolerance, indicatorFactory);
        }
    }

    /**
     * Training and validation index window on the original series.
     *
     * @param trainingStartIndex   inclusive training start index
     * @param trainingEndIndex     inclusive training end index
     * @param validationStartIndex inclusive validation start index, or {@code -1}
     * @param validationEndIndex   inclusive validation end index, or {@code -1}
     * @since 0.22.7
     */
    public record ResearchWindow(int trainingStartIndex, int trainingEndIndex, int validationStartIndex,
            int validationEndIndex) {

        /**
         * Creates a validated research window.
         *
         * @since 0.22.7
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
         * @since 0.22.7
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
     * @since 0.22.7
     */
    public record PruningGroup(String representativeId, List<String> memberIds, String reason, double maximumDistance) {

        /**
         * Creates a validated pruning group.
         *
         * @since 0.22.7
         */
        public PruningGroup {
            if (representativeId == null || representativeId.isBlank()) {
                throw new IllegalArgumentException("representativeId cannot be blank");
            }
            memberIds = List.copyOf(Objects.requireNonNull(memberIds, "memberIds"));
            if (memberIds.isEmpty()) {
                throw new IllegalArgumentException("memberIds cannot be empty");
            }
            if (!memberIds.contains(representativeId)) {
                throw new IllegalArgumentException("memberIds must contain representativeId");
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
         * @since 0.22.7
         */
        public List<String> discardedIds() {
            return memberIds.stream().skip(1).toList();
        }
    }

    /**
     * Ranked candidate score row.
     *
     * @param candidateId    candidate id
     * @param strategyName   strategy name
     * @param rank           one-based rank
     * @param compositeScore weighted normalized score
     * @param metricValues   raw metric values
     * @param representative whether this row is a representative candidate
     * @since 0.22.7
     */
    public record CandidateScore(String candidateId, String strategyName, int rank, Num compositeScore,
            Map<String, Num> metricValues, boolean representative) {

        /**
         * Creates a validated candidate score row.
         *
         * @since 0.22.7
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
     * @since 0.22.7
     */
    public record ParameterResearchReport(String datasetId, int barCount, ResearchWindow window,
            String candidateSpaceHash, PruningPolicy pruningPolicy, int generatedCandidateCount,
            int validCandidateCount, int invalidCandidateCount, List<StrategyCandidate> candidates,
            String baselineTopCandidateId, String selectedTopCandidateId, List<PruningGroup> pruningGroups,
            List<CandidateScore> baselineScores, List<CandidateScore> trainingScores,
            List<CandidateScore> validationScores, List<InvalidCandidate> invalidCandidates, List<String> warnings,
            BacktestRuntimeReport trainingRuntimeReport, BacktestRuntimeReport validationRuntimeReport) {

        /**
         * Creates a validated research report.
         *
         * @since 0.22.7
         */
        public ParameterResearchReport {
            datasetId = datasetId == null || datasetId.isBlank() ? "series" : datasetId;
            if (barCount < 0 || generatedCandidateCount < 0 || validCandidateCount < 0 || invalidCandidateCount < 0) {
                throw new IllegalArgumentException("report counts must be >= 0");
            }
            Objects.requireNonNull(window, "window");
            if (candidateSpaceHash == null || candidateSpaceHash.isBlank()) {
                throw new IllegalArgumentException("candidateSpaceHash cannot be blank");
            }
            Objects.requireNonNull(pruningPolicy, "pruningPolicy");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("candidates cannot be empty");
            }
            pruningGroups = List.copyOf(Objects.requireNonNull(pruningGroups, "pruningGroups"));
            baselineScores = List.copyOf(Objects.requireNonNull(baselineScores, "baselineScores"));
            trainingScores = List.copyOf(Objects.requireNonNull(trainingScores, "trainingScores"));
            validationScores = List.copyOf(Objects.requireNonNull(validationScores, "validationScores"));
            invalidCandidates = List.copyOf(Objects.requireNonNull(invalidCandidates, "invalidCandidates"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            Objects.requireNonNull(trainingRuntimeReport, "trainingRuntimeReport");
            Objects.requireNonNull(validationRuntimeReport, "validationRuntimeReport");
        }

        /**
         * Counts representative candidates after pruning.
         *
         * @return representative count
         * @since 0.22.7
         */
        public int representativeCount() {
            return pruningGroups.size();
        }

        /**
         * Counts candidates removed by pruning.
         *
         * @return pruned candidate count
         * @since 0.22.7
         */
        public int prunedCandidateCount() {
            int members = pruningGroups.stream().mapToInt(group -> group.memberIds().size()).sum();
            return members - pruningGroups.size();
        }

        /**
         * Formats a concise human-readable report summary.
         *
         * @return summary text
         * @since 0.22.7
         */
        public String formatSummary() {
            StringBuilder builder = new StringBuilder();
            builder.append("Parameter research '")
                    .append(datasetId)
                    .append("': bars=")
                    .append(barCount)
                    .append(", hash=")
                    .append(candidateSpaceHash)
                    .append(", generated=")
                    .append(generatedCandidateCount)
                    .append(", valid=")
                    .append(validCandidateCount)
                    .append(", representatives=")
                    .append(representativeCount())
                    .append(", pruned=")
                    .append(prunedCandidateCount())
                    .append(", invalid=")
                    .append(invalidCandidateCount)
                    .append(", policy=")
                    .append(pruningPolicy);
            builder.append(System.lineSeparator())
                    .append("train=")
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
                    .append("baselineTop=")
                    .append(baselineTopCandidateId)
                    .append(", selectedTop=")
                    .append(selectedTopCandidateId);
            appendScores(builder, "training", trainingScores);
            appendScores(builder, "validation", validationScores);
            if (!warnings.isEmpty()) {
                builder.append(System.lineSeparator()).append("warnings=").append(warnings);
            }
            return builder.toString();
        }

        private static void appendScores(StringBuilder builder, String label, List<CandidateScore> scores) {
            builder.append(System.lineSeparator()).append(label).append("Scores=");
            if (scores.isEmpty()) {
                builder.append("[]");
                return;
            }
            List<String> rows = new ArrayList<>();
            for (CandidateScore score : scores) {
                rows.add("#" + score.rank() + " " + score.candidateId() + " score=" + score.compositeScore());
            }
            builder.append(rows);
        }
    }

    private record ExecutionBundle(List<StrategyCandidate> candidates, List<Strategy> strategies,
            BacktestExecutionResult result, List<InvalidCandidate> invalidCandidates) {
    }

    private record ValidationBundle(List<CandidateScore> validationScores, BacktestRuntimeReport runtimeReport,
            List<InvalidCandidate> invalidCandidates) {
    }

    @FunctionalInterface
    private interface SignatureSupplier {
        String signatureFor(int candidateIndex);
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
