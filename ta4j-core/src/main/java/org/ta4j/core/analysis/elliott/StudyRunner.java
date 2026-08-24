/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;

/**
 * Package-private, protocol-agnostic Phase 3 study engine.
 *
 * <p>
 * The caller supplies the detector, grammars, and relationship rules. The
 * runner only evaluates bars inside the supplied locked partitions and never
 * consults truth anchors or a runtime registry. The confirmation-aware kernel
 * is always evaluated through an as-of view.
 * </p>
 *
 * <p>
 * Real-data execution is intentionally caller-owned: load a classpath or
 * file-backed {@link BarSeries} in the examples/test data plane, then invoke
 * {@link #evaluateAndWrite(String, BarSeries, int, int, Path)}. This core test
 * scope does not download or fabricate market data.
 * </p>
 */
final class StudyRunner {

    /**
     * Provenance token for the in-kernel default configuration. This is NOT a
     * content hash: the frozen protocol resource lives in ta4j-examples and pins
     * its own fingerprint there. Reports must never fake a digest.
     */
    private static final String DEFAULT_FINGERPRINT = "in-kernel-default-unpinned";

    private final Supplier<SwingDetector> detectorFactory;
    private final List<TopologyGrammar> grammars;
    private final List<RelationshipRule> rules;
    private final Configuration configuration;

    StudyRunner(final Supplier<SwingDetector> detectorFactory, final List<TopologyGrammar> grammars,
            final List<RelationshipRule> rules) {
        this(detectorFactory, grammars, rules, Configuration.lockedDefault());
    }

    StudyRunner(final Supplier<SwingDetector> detectorFactory, final List<TopologyGrammar> grammars,
            final List<RelationshipRule> rules, final Configuration configuration) {
        this.detectorFactory = Objects.requireNonNull(detectorFactory, "detectorFactory");
        this.grammars = validateGrammars(grammars);
        this.rules = validateRules(rules);
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Evaluates the locked study over the supplied index range.
     *
     * @param series    source bars
     * @param fromIndex first requested index, inclusive
     * @param toIndex   last requested index, inclusive
     * @return immutable study report
     */
    StudyReport evaluate(final BarSeries series, final int fromIndex, final int toIndex) {
        return evaluate("series", series, fromIndex, toIndex);
    }

    /**
     * Evaluates and persists one named asset as one deterministic JSON report.
     *
     * @param assetId   report asset identifier
     * @param series    source bars
     * @param fromIndex first requested index, inclusive
     * @param toIndex   last requested index, inclusive
     * @param path      caller-selected output path
     * @return the report written to {@code path}
     */
    StudyReport evaluateAndWrite(final String assetId, final BarSeries series, final int fromIndex, final int toIndex,
            final Path path) {
        final StudyReport report = evaluate(assetId, series, fromIndex, toIndex);
        report.write(path);
        return report;
    }

    /**
     * Evaluates one named asset. The name is carried into the report so transfer
     * assets remain separately identifiable and are never merged with BTC.
     *
     * @param assetId   report asset identifier
     * @param series    source bars
     * @param fromIndex first requested index, inclusive
     * @param toIndex   last requested index, inclusive
     * @return immutable study report
     */
    StudyReport evaluate(final String assetId, final BarSeries series, final int fromIndex, final int toIndex) {
        Objects.requireNonNull(series, "series");
        validateRange(series, fromIndex, toIndex);
        configuration.partitions().assertCalibrationConfiguration();

        final int start = Math.max(fromIndex, series.getBeginIndex());
        final int end = Math.min(toIndex, series.getEndIndex());
        final List<StudyReport.ModeReport> h1Modes = new ArrayList<>();
        // H1 is topology-only classification over the caller-declared grammar
        // set; the full kernel grammar spread is covered by the competing
        // section, so no forced additions here.
        for (final TopologyGrammar grammar : grammars) {
            h1Modes.add(evaluateTopologyMode(series, start, end, configuration.partitions(), detectorFactory, grammar,
                    "topology-only"));
        }

        final List<StudyReport.ModeReport> ablations = new ArrayList<>();
        for (final RuleAblation.Mode mode : RuleAblation.modes(rules)) {
            ablations.add(evaluateMode(series, start, end, configuration.partitions(), detectorFactory,
                    TopologyGrammar.CYCLE_5_3, mode.name(), mode.rules()));
        }

        final List<StudyReport.ModeReport> competing = new ArrayList<>();
        final Set<String> competingNames = new LinkedHashSet<>();
        for (final TopologyGrammar grammar : TopologyGrammar.values()) {
            competingNames.add(grammar.name());
        }
        for (final TopologyGrammar grammar : grammars) {
            competingNames.add(grammar.name());
        }
        competingNames.add("3+3");
        competingNames.add("5+5");
        competingNames.add("7+3");
        competingNames.add("change-point-baseline");
        for (final String grammarName : competingNames) {
            final StudyReport.ModeReport mode;
            if ("change-point-baseline".equals(grammarName)) {
                mode = evaluateChangePointBaseline(series, start, end, configuration.partitions());
            } else {
                final TopologyGrammar grammar = parseKernelGrammar(grammarName);
                if (grammar != null) {
                    mode = evaluateTopologyMode(series, start, end, configuration.partitions(), detectorFactory,
                            grammar, "competing-" + grammarName);
                } else {
                    mode = evaluateAlternativeGrammar(series, start, end, configuration.partitions(), detectorFactory,
                            grammarName);
                }
            }
            competing.add(mode);
        }

        final StudyReport.RobustnessReport robustness = DetectorRobustnessMatrix.evaluate(series, start, end,
                configuration.partitions(), configuration.robustnessDetectors());
        final List<StudyReport.NullReport> nullReports = evaluateNulls(series, start, end);
        final StudyReport.HypothesisReport h1 = new StudyReport.HypothesisReport("H1", TopologyGrammar.MOTIVE_5.name(),
                h1Modes);
        // H2 modes are exactly the preregistered ablation ladder, whose first
        // rung is already the topology-only baseline.
        final StudyReport.HypothesisReport h2 = new StudyReport.HypothesisReport("H2", TopologyGrammar.CYCLE_5_3.name(),
                ablations);
        final List<StudyReport.PartitionSpec> partitionSpecs = configuration.partitions()
                .entries()
                .stream()
                .map(entry -> new StudyReport.PartitionSpec(entry.name(), entry.start(), entry.end()))
                .toList();
        return new StudyReport(assetId, configuration.protocolFingerprint(), configuration.seed(),
                configuration.primaryDetector(), partitionSpecs, configuration.partitions().forbiddenCalibrationStart(),
                h1, h2, competing, ablations, robustness, nullReports);
    }

    private List<StudyReport.NullReport> evaluateNulls(final BarSeries source, final int start, final int end) {
        final List<StudyReport.NullReport> reports = new ArrayList<>();
        final boolean hasEvaluationWindow = start <= end;
        final int sourceBegin = source.getBeginIndex();
        final BarSeries causalSource = hasEvaluationWindow ? source.getSubSeries(sourceBegin, end + 1) : source;
        // Both preregistered hypotheses need a null baseline: H1 claims about
        // MOTIVE_5 and the frozen H2 claim about complete CYCLE_5_3 cycles.
        final List<TopologyGrammar> nullGrammars = List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CYCLE_5_3);
        final Partitions partitions = configuration.partitions();
        for (final int blockLength : configuration.nullBlockLengths()) {
            for (final TopologyGrammar grammar : nullGrammars) {
                final List<MetricAccumulator> totals = newAccumulators(List.of());
                // Look-ahead-free sampling: every partition's ensemble is drawn
                // only from returns available at that partition's last bar, so a
                // calibration partition's null baseline can never incorporate
                // validation or holdout returns. The shared seed keeps the RNG
                // stream comparable across partitions over different tapes.
                for (int partitionIndex = 0; partitionIndex < totals.size(); partitionIndex++) {
                    final int partitionLastBar = lastBarInPartition(causalSource, partitions, partitionIndex);
                    if (partitionLastBar - causalSource.getBeginIndex() < 1) {
                        continue;
                    }
                    final BarSeries truncated = causalSource.getSubSeries(causalSource.getBeginIndex(),
                            partitionLastBar + 1);
                    final List<BarSeries> nullSeries = BlockBootstrapNulls.generate(truncated, blockLength,
                            configuration.nullEnsembleSize(), configuration.seed());
                    for (final BarSeries member : nullSeries) {
                        // Fresh accumulators per member so label-stability
                        // transitions never leak across ensemble members.
                        final List<MetricAccumulator> memberAccumulators = newAccumulators(List.of());
                        final ConfirmationTracker.CausalReplay replay = observeReplay(member);
                        recordTopology(member, member.getBeginIndex(), member.getEndIndex(), partitions, replay,
                                grammar, List.of(), memberAccumulators);
                        totals.get(partitionIndex).mergeFrom(memberAccumulators.get(partitionIndex));
                    }
                }
                reports.add(new StudyReport.NullReport(grammar.name(), blockLength, configuration.nullEnsembleSize(),
                        configuration.seed(), metrics(totals, partitions)));
            }
        }
        return List.copyOf(reports);
    }

    /**
     * Returns the last bar of {@code series} whose date belongs to the given
     * partition, or -1 when the partition has no bars in the series.
     */
    private static int lastBarInPartition(final BarSeries series, final Partitions partitions,
            final int partitionIndex) {
        int last = -1;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            if (partitionIndex(series, index, partitions) == partitionIndex) {
                last = index;
            }
        }
        return last;
    }

    private ConfirmationTracker.CausalReplay observeReplay(final BarSeries series) {
        return observeReplay(series, detectorFactory);
    }

    private StudyReport.ModeReport evaluateMode(final BarSeries series, final int start, final int end,
            final Partitions partitions, final Supplier<SwingDetector> factory, final TopologyGrammar grammar,
            final String mode, final List<RelationshipRule> activeRules) {
        final List<MetricAccumulator> accumulators = newAccumulators(activeRules);
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory);
        recordTopology(series, start, end, partitions, replay, grammar, activeRules, accumulators);
        return new StudyReport.ModeReport(mode, grammar.name(), activeRuleIds(activeRules),
                metrics(accumulators, partitions));
    }

    static StudyReport.ModeReport evaluateTopologyMode(final BarSeries series, final int start, final int end,
            final Partitions partitions, final Supplier<SwingDetector> factory, final TopologyGrammar grammar,
            final String mode) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(partitions, "partitions");
        Objects.requireNonNull(factory, "factory");
        final List<MetricAccumulator> accumulators = newAccumulators(List.of(), partitions);
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory);
        recordTopology(series, start, end, partitions, replay, grammar, List.of(), accumulators);
        return new StudyReport.ModeReport(mode, grammar.name(), List.of(), metrics(accumulators, partitions));
    }

    private static void recordTopology(final BarSeries series, final int start, final int end,
            final Partitions partitions, final ConfirmationTracker.CausalReplay replay, final TopologyGrammar grammar,
            final List<RelationshipRule> activeRules, final List<MetricAccumulator> accumulators) {
        if (start > end) {
            return;
        }
        for (int index = start; index <= end; index++) {
            final int partitionIndex = partitionIndex(series, index, partitions);
            if (partitionIndex < 0) {
                continue;
            }
            final LocalDate date = barDate(series, index);
            partitions.assertCalibrationDateAllowed(date);
            final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(grammar, replay.at(index));
            accumulators.get(partitionIndex).record(analysis, index, activeRules, series);
        }
    }

    private static StudyReport.ModeReport evaluateAlternativeGrammar(final BarSeries series, final int start,
            final int end, final Partitions partitions, final Supplier<SwingDetector> factory, final String name) {
        final AlternativeGrammar grammar = AlternativeGrammar.of(name);
        final List<MetricAccumulator> accumulators = newAccumulators(List.of(), partitions);
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory);
        if (start <= end) {
            for (int index = start; index <= end; index++) {
                final int partitionIndex = partitionIndex(series, index, partitions);
                if (partitionIndex < 0) {
                    continue;
                }
                final LocalDate date = barDate(series, index);
                final List<ConfirmedPivot> visible = replay.at(index);
                final List<String> matches = grammar.matches(visible);
                final MetricAccumulator accumulator = accumulators.get(partitionIndex);
                if (visible.size() < 2) {
                    accumulator.recordAlternative(index, false, false, false, "insufficient-history");
                } else if (matches.size() == 1) {
                    accumulator.recordAlternative(index, true, false, false, matches.get(0));
                } else if (matches.size() > 1) {
                    accumulator.recordAlternative(index, false, true, false, "ambiguous");
                } else if (grammar.hasPartial(visible)) {
                    accumulator.recordAlternative(index, false, false, true, "forming");
                } else {
                    accumulator.recordAlternative(index, false, false, false, "no-match");
                }
            }
        }
        return new StudyReport.ModeReport("competing-" + name, name, List.of(), metrics(accumulators, partitions));
    }

    private static StudyReport.ModeReport evaluateChangePointBaseline(final BarSeries series, final int start,
            final int end, final Partitions partitions) {
        final List<MetricAccumulator> accumulators = newAccumulators(List.of(), partitions);
        if (start <= end) {
            for (int index = start; index <= end; index++) {
                final int partitionIndex = partitionIndex(series, index, partitions);
                if (partitionIndex < 0) {
                    continue;
                }
                final LocalDate date = barDate(series, index);
                partitions.assertCalibrationDateAllowed(date);
                final MetricAccumulator accumulator = accumulators.get(partitionIndex);
                if (index - 2 < series.getBeginIndex()) {
                    accumulator.recordAlternative(index, false, false, false, "insufficient-history");
                    continue;
                }
                final Num first = series.getBar(index - 1)
                        .getClosePrice()
                        .minus(series.getBar(index - 2).getClosePrice());
                final Num second = series.getBar(index).getClosePrice().minus(series.getBar(index - 1).getClosePrice());
                final boolean change = !first.isZero() && !second.isZero() && first.isPositive() != second.isPositive();
                accumulator.recordAlternative(index, change, false, false, change ? "change" : "stable");
            }
        }
        return new StudyReport.ModeReport("competing-change-point-baseline", "change-point-baseline", List.of(),
                metrics(accumulators, partitions));
    }

    private static ConfirmationTracker.CausalReplay observeReplay(final BarSeries series,
            final Supplier<SwingDetector> factory) {
        final SwingDetector detector = Objects.requireNonNull(factory, "detectorFactory").get();
        final SwingDetector nonNullDetector = Objects.requireNonNull(detector, "detectorFactory returned null");
        return new ConfirmationTracker(nonNullDetector).observeReplay(series);
    }

    private List<MetricAccumulator> newAccumulators(final List<RelationshipRule> activeRules) {
        return newAccumulators(activeRules, configuration.partitions());
    }

    private static List<MetricAccumulator> newAccumulators(final List<RelationshipRule> activeRules,
            final Partitions partitions) {
        final List<MetricAccumulator> accumulators = new ArrayList<>(partitions.entries().size());
        for (int index = 0; index < partitions.entries().size(); index++) {
            accumulators.add(new MetricAccumulator(activeRules));
        }
        return accumulators;
    }

    private static List<StudyReport.PartitionMetrics> metrics(final List<MetricAccumulator> accumulators,
            final Partitions partitions) {
        final List<StudyReport.PartitionMetrics> metrics = new ArrayList<>(accumulators.size());
        for (int index = 0; index < accumulators.size(); index++) {
            metrics.add(accumulators.get(index).toMetrics(partitions.entries().get(index).name()));
        }
        return List.copyOf(metrics);
    }

    private static int partitionIndex(final BarSeries series, final int index, final Partitions partitions) {
        final LocalDate date = barDate(series, index);
        for (int partitionIndex = 0; partitionIndex < partitions.entries().size(); partitionIndex++) {
            if (partitions.entries().get(partitionIndex).contains(date)) {
                return partitionIndex;
            }
        }
        return -1;
    }

    private static LocalDate barDate(final BarSeries series, final int index) {
        return series.getBar(index).getBeginTime().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static List<String> activeRuleIds(final List<RelationshipRule> activeRules) {
        return activeRules.stream().map(RelationshipRule::id).toList();
    }

    private static TopologyGrammar parseKernelGrammar(final String name) {
        try {
            return TopologyGrammar.valueOf(name);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<TopologyGrammar> validateGrammars(final List<TopologyGrammar> supplied) {
        Objects.requireNonNull(supplied, "grammars");
        if (supplied.isEmpty()) {
            throw new IllegalArgumentException("grammars must not be empty");
        }
        return List.copyOf(supplied);
    }

    private static List<RelationshipRule> validateRules(final List<RelationshipRule> supplied) {
        Objects.requireNonNull(supplied, "rules");
        final List<RelationshipRule> copy = List.copyOf(supplied);
        final Set<String> identifiers = new HashSet<>();
        for (final RelationshipRule rule : copy) {
            Objects.requireNonNull(rule, "rules contains null");
            if (!identifiers.add(rule.id())) {
                throw new IllegalArgumentException("duplicate relationship rule id: " + rule.id());
            }
        }
        return copy;
    }

    private static void validateRange(final BarSeries series, final int fromIndex, final int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex must not exceed toIndex");
        }
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("series must contain at least one bar");
        }
    }

    private static final class MetricAccumulator {
        private final List<RuleCounter> ruleCounters;
        private long evaluationCount;
        private long completeCount;
        private long formingCount;
        private long ambiguousCount;
        private long noMatchCount;
        private long invalidatedCount;
        private long insufficientHistoryCount;
        private long evidenceEvaluationCount;
        private long evidencePassCount;
        private long evidenceFailCount;
        private long evidencePendingCount;
        private long evidenceUnavailableCount;
        private long evidenceNotApplicableCount;
        private double confirmationLagSum;
        private long confirmationLagCount;
        private double stabilitySum;
        private long stabilityCount;
        private Set<String> previousLabels;
        private boolean hasPreviousLabels;
        private int firstIndex = Integer.MAX_VALUE;
        private int lastIndex = Integer.MIN_VALUE;

        private MetricAccumulator(final List<RelationshipRule> activeRules) {
            this.ruleCounters = activeRules.stream().map(rule -> new RuleCounter(rule.id())).toList();
        }

        private void record(final TopologyAnalysis analysis, final int index, final List<RelationshipRule> activeRules,
                final BarSeries series) {
            evaluationCount++;
            firstIndex = Math.min(firstIndex, index);
            lastIndex = Math.max(lastIndex, index);
            switch (analysis.status()) {
            case COMPLETE -> {
                completeCount++;
                final TopologyCandidate candidate = analysis.candidates().get(0);
                final Set<String> labels = Set
                        .of(candidate.direction() + ":" + candidate.startBarIndex() + "-" + candidate.endBarIndex());
                updateStability(labels);
                double lag = 0.0d;
                for (final ConfirmedPivot pivot : candidate.pivots()) {
                    lag += Math.max(0, pivot.confirmationIndex() - pivot.pivotIndex());
                }
                confirmationLagSum += lag / candidate.pivots().size();
                confirmationLagCount++;
                evaluateRules(candidate, activeRules, series);
            }
            case FORMING -> {
                formingCount++;
                updateStability(Set.of("forming:" + analysis.direction()));
            }
            case AMBIGUOUS -> {
                ambiguousCount++;
                final Set<String> labels = new HashSet<>();
                for (final TopologyCandidate candidate : analysis.candidates()) {
                    labels.add(candidate.direction() + ":" + candidate.startBarIndex() + "-" + candidate.endBarIndex());
                }
                updateStability(labels);
            }
            case NO_MATCH -> {
                noMatchCount++;
                updateStability(Set.of());
            }
            case INVALIDATED -> {
                invalidatedCount++;
                updateStability(Set.of("invalidated"));
            }
            case INSUFFICIENT_HISTORY -> {
                insufficientHistoryCount++;
                updateStability(Set.of("insufficient-history"));
            }
            default -> throw new IllegalStateException("unhandled topology status " + analysis.status());
            }
        }

        private void recordAlternative(final int index, final boolean complete, final boolean ambiguous,
                final boolean forming, final String label) {
            evaluationCount++;
            firstIndex = Math.min(firstIndex, index);
            lastIndex = Math.max(lastIndex, index);
            if (complete) {
                completeCount++;
            } else if (ambiguous) {
                ambiguousCount++;
            } else if (forming) {
                formingCount++;
            } else if ("insufficient-history".equals(label)) {
                insufficientHistoryCount++;
            } else {
                noMatchCount++;
            }
            updateStability(Set.of(label));
        }

        private void evaluateRules(final TopologyCandidate candidate, final List<RelationshipRule> activeRules,
                final BarSeries series) {
            for (int index = 0; index < activeRules.size(); index++) {
                final RuleEvidence evidence = activeRules.get(index).evaluate(candidate, series);
                evidenceEvaluationCount++;
                final RuleCounter counter = ruleCounters.get(index);
                switch (evidence.state()) {
                case PASS -> {
                    evidencePassCount++;
                    counter.passCount++;
                    if (evidence.score().isPresent()) {
                        final double score = evidence.score().orElseThrow();
                        counter.scoredCount++;
                        counter.scoreSum += score;
                        counter.scoreMin = Math.min(counter.scoreMin, score);
                        counter.scoreMax = Math.max(counter.scoreMax, score);
                    }
                }
                case FAIL -> {
                    evidenceFailCount++;
                    counter.failCount++;
                }
                case PENDING -> {
                    evidencePendingCount++;
                    counter.pendingCount++;
                }
                case UNAVAILABLE -> {
                    evidenceUnavailableCount++;
                    counter.unavailableCount++;
                }
                case NOT_APPLICABLE -> {
                    evidenceNotApplicableCount++;
                    counter.notApplicableCount++;
                }
                default -> throw new IllegalStateException("unhandled evidence state " + evidence.state());
                }
                counter.evaluationCount++;
            }
        }

        private void updateStability(final Set<String> labels) {
            final Set<String> current = Set.copyOf(labels);
            if (hasPreviousLabels) {
                final Set<String> union = new HashSet<>(previousLabels);
                union.addAll(current);
                final Set<String> intersection = new HashSet<>(previousLabels);
                intersection.retainAll(current);
                stabilitySum += union.isEmpty() ? 1.0d : (double) intersection.size() / union.size();
                stabilityCount++;
            }
            previousLabels = current;
            hasPreviousLabels = true;
        }

        private void mergeFrom(final MetricAccumulator other) {
            evaluationCount += other.evaluationCount;
            firstIndex = Math.min(firstIndex, other.firstIndex);
            lastIndex = Math.max(lastIndex, other.lastIndex);
            completeCount += other.completeCount;
            ambiguousCount += other.ambiguousCount;
            formingCount += other.formingCount;
            noMatchCount += other.noMatchCount;
            invalidatedCount += other.invalidatedCount;
            insufficientHistoryCount += other.insufficientHistoryCount;
            confirmationLagSum += other.confirmationLagSum;
            confirmationLagCount += other.confirmationLagCount;
            stabilitySum += other.stabilitySum;
            stabilityCount += other.stabilityCount;
            evidenceEvaluationCount += other.evidenceEvaluationCount;
            evidencePassCount += other.evidencePassCount;
            evidenceFailCount += other.evidenceFailCount;
            evidencePendingCount += other.evidencePendingCount;
            evidenceUnavailableCount += other.evidenceUnavailableCount;
            evidenceNotApplicableCount += other.evidenceNotApplicableCount;
            for (int index = 0; index < ruleCounters.size() && index < other.ruleCounters.size(); index++) {
                ruleCounters.get(index).mergeFrom(other.ruleCounters.get(index));
            }
        }

        private StudyReport.PartitionMetrics toMetrics(final String partition) {
            final long denominator = evaluationCount;
            final long evidenceDenominator = evidenceEvaluationCount;
            final List<StudyReport.RuleMetrics> rules = ruleCounters.stream().map(RuleCounter::toMetrics).toList();
            return new StudyReport.PartitionMetrics(partition, firstIndex == Integer.MAX_VALUE ? -1 : firstIndex,
                    lastIndex == Integer.MIN_VALUE ? -1 : lastIndex, evaluationCount, completeCount, formingCount,
                    ambiguousCount, noMatchCount, invalidatedCount, insufficientHistoryCount,
                    ratio(completeCount, denominator), ratio(ambiguousCount, denominator),
                    ratio(noMatchCount, denominator), ratio(confirmationLagSum, confirmationLagCount),
                    ratio(stabilitySum, stabilityCount), evidenceEvaluationCount, evidencePassCount, evidenceFailCount,
                    evidencePendingCount, evidenceUnavailableCount, evidenceNotApplicableCount,
                    ratio(evidencePassCount, evidenceDenominator), rules);
        }

        private static double ratio(final long numerator, final long denominator) {
            return denominator == 0 ? 0.0d : (double) numerator / denominator;
        }

        private static double ratio(final double numerator, final long denominator) {
            return denominator == 0 ? 0.0d : numerator / denominator;
        }
    }

    private static final class RuleCounter {
        private final String id;
        private long evaluationCount;
        private long passCount;
        private long failCount;
        private long pendingCount;
        private long unavailableCount;
        private long notApplicableCount;
        private long scoredCount;
        private double scoreSum;
        private double scoreMin = Double.POSITIVE_INFINITY;
        private double scoreMax = Double.NEGATIVE_INFINITY;

        private RuleCounter(final String id) {
            this.id = id;
        }

        private StudyReport.RuleMetrics toMetrics() {
            return new StudyReport.RuleMetrics(id, evaluationCount, passCount, failCount, pendingCount,
                    unavailableCount, notApplicableCount,
                    evaluationCount == 0 ? 0.0d : (double) passCount / evaluationCount, scoredCount, scoreMean(),
                    scoredCount == 0 ? 0.0d : scoreMin, scoredCount == 0 ? 0.0d : scoreMax);
        }

        private double scoreMean() {
            return scoredCount == 0 ? 0.0d : scoreSum / scoredCount;
        }

        private void mergeFrom(final RuleCounter other) {
            evaluationCount += other.evaluationCount;
            passCount += other.passCount;
            failCount += other.failCount;
            pendingCount += other.pendingCount;
            unavailableCount += other.unavailableCount;
            notApplicableCount += other.notApplicableCount;
            scoredCount += other.scoredCount;
            scoreSum += other.scoreSum;
            scoreMin = Math.min(scoreMin, other.scoreMin);
            scoreMax = Math.max(scoreMax, other.scoreMax);
        }
    }

    record AlternativeGrammar(String name, int[] segmentLegs) {
        static AlternativeGrammar of(final String name) {
            return switch (name) {
            case "3+3" -> new AlternativeGrammar(name, new int[] { 3, 3 });
            case "5+5" -> new AlternativeGrammar(name, new int[] { 5, 5 });
            case "7+3" -> new AlternativeGrammar(name, new int[] { 7, 3 });
            default -> throw new IllegalArgumentException("unknown alternative grammar: " + name);
            };
        }

        List<String> matches(final List<ConfirmedPivot> pivots) {
            final int legCount = segmentLegs[0] + segmentLegs[1];
            final int required = legCount + 1;
            final List<String> matches = new ArrayList<>();
            for (int start = 0; start + required <= pivots.size(); start++) {
                final List<ConfirmedPivot> window = pivots.subList(start, start + required);
                if (matchesWindow(window)) {
                    matches.add(window.get(0).pivotIndex() + "-" + window.get(window.size() - 1).pivotIndex());
                }
            }
            return matches;
        }

        private boolean hasPartial(final List<ConfirmedPivot> pivots) {
            final int required = segmentLegs[0] + segmentLegs[1] + 1;
            final int maxSuffix = Math.min(pivots.size(), required - 1);
            // A two-pivot suffix satisfies one orientation of the leading leg
            // for every non-flat tail, which would make noMatchRate unreachable
            // and inflate forming counts. A forming claim requires the whole
            // leading segment to be observable in the suffix window.
            final int minSuffix = segmentLegs[0] + 1;
            if (maxSuffix < minSuffix) {
                // The leading segment is not observable yet: no honest forming
                // claim is possible, however well the short tail happens to fit.
                return false;
            }
            for (int suffix = maxSuffix; suffix >= minSuffix; suffix--) {
                final List<ConfirmedPivot> window = pivots.subList(pivots.size() - suffix, pivots.size());
                if (matchesPartialWindow(window)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesWindow(final List<ConfirmedPivot> window) {
            for (final WaveDirection direction : WaveDirection.values()) {
                if (matchesLegSequence(window, direction, true)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesPartialWindow(final List<ConfirmedPivot> window) {
            for (final WaveDirection direction : WaveDirection.values()) {
                if (matchesLegSequence(window, direction, false)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Num-domain leg validation. Complete windows require every leg to move
         * decisively in its assigned direction; partial windows tolerate an uncommitted
         * trailing leg so a pattern mid-swing is not rejected.
         */
        private boolean matchesLegSequence(final List<ConfirmedPivot> window, final WaveDirection direction,
                final boolean complete) {
            for (int leg = 0; leg < window.size() - 1; leg++) {
                if (window.get(leg).type() == window.get(leg + 1).type()) {
                    return false;
                }
                final Num delta = window.get(leg + 1).price().minus(window.get(leg).price());
                final Num signed = direction == WaveDirection.BULLISH ? delta : delta.negate();
                final boolean positive = leg < segmentLegs[0] ? leg % 2 == 0 : (leg - segmentLegs[0]) % 2 != 0;
                if (signed.isZero()) {
                    if (complete) {
                        return false;
                    }
                } else if (positive ? !signed.isPositive() : !signed.isNegative()) {
                    return false;
                }
            }
            return boundaryIsExtreme(window, direction);
        }

        /**
         * Preregistration integrity: the junction between the two named segments must
         * be observable, otherwise grammars sharing an odd first-segment length (3+3,
         * 5+5 and 7+3 over 11 pivots) would match identical windows under different
         * labels. The junction pivot is therefore required to be the window extreme on
         * the leading trend side (bullish high / bearish low), which separates the
         * match sets of the competing grammars.
         */
        private boolean boundaryIsExtreme(final List<ConfirmedPivot> window, final WaveDirection direction) {
            final int boundary = segmentLegs[0];
            if (boundary >= window.size()) {
                return true;
            }
            final Num extreme = window.get(boundary).price();
            for (int i = 0; i < window.size(); i++) {
                if (i == boundary) {
                    continue;
                }
                final Num price = window.get(i).price();
                if (direction == WaveDirection.BULLISH ? price.isGreaterThan(extreme) : price.isLessThan(extreme)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One inclusive date partition. */
    record Partition(String name, LocalDate start, LocalDate end) {
        Partition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("partition name must not be blank");
            }
            Objects.requireNonNull(start, "partition.start");
            Objects.requireNonNull(end, "partition.end");
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("partition start must not exceed end");
            }
        }

        boolean contains(final LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    /** Immutable locked partition set and calibration embargo. */
    record Partitions(List<Partition> entries, LocalDate forbiddenCalibrationStart) {
        Partitions {
            entries = entries == null ? List.of() : List.copyOf(entries);
            Objects.requireNonNull(forbiddenCalibrationStart, "forbiddenCalibrationStart");
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("at least one locked partition is required");
            }
            final Set<String> names = new HashSet<>();
            for (int index = 0; index < entries.size(); index++) {
                final Partition entry = Objects.requireNonNull(entries.get(index), "partition entry");
                if (!names.add(entry.name())) {
                    throw new IllegalArgumentException("duplicate partition name: " + entry.name());
                }
                if (index > 0 && !entries.get(index - 1).end().isBefore(entry.start())) {
                    throw new IllegalArgumentException("partitions must be ordered and non-overlapping");
                }
            }
        }

        static Partitions lockedDefault() {
            return new Partitions(
                    List.of(new Partition("calibration", LocalDate.of(2010, 1, 1), LocalDate.of(2019, 12, 31)),
                            new Partition("validation", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 6, 15)),
                            new Partition("holdout", LocalDate.of(2023, 6, 16), LocalDate.of(2026, 3, 6))),
                    LocalDate.of(2024, 1, 1));
        }

        Partition calibration() {
            return byName("calibration");
        }

        Partition validation() {
            return byName("validation");
        }

        Partition holdout() {
            return byName("holdout");
        }

        void assertCalibrationConfiguration() {
            final Partition calibration = calibration();
            if (!calibration.end().isBefore(forbiddenCalibrationStart)) {
                throw new IllegalStateException(
                        "calibration partition reaches forbidden date " + forbiddenCalibrationStart);
            }
        }

        /**
         * Hard post-2024 calibration guard. A calibration observation on or after the
         * embargo date is an error, never a silently skipped sample.
         */
        void assertCalibrationDateAllowed(final LocalDate date) {
            if ("calibration".equals(partitionName(date)) && !date.isBefore(forbiddenCalibrationStart)) {
                throw new IllegalStateException(
                        "calibration touched forbidden date " + date + " (start " + forbiddenCalibrationStart + ")");
            }
        }

        private String partitionName(final LocalDate date) {
            for (final Partition entry : entries) {
                if (entry.contains(date)) {
                    return entry.name();
                }
            }
            return "";
        }

        private Partition byName(final String name) {
            return entries.stream()
                    .filter(entry -> name.equals(entry.name()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("missing locked partition " + name));
        }
    }

    /** Complete protocol-independent study configuration. */
    /**
     * @param primaryDetector stable identifier of the detector factory that
     *                        produces H1/H2/null results; robustness rows carry
     *                        their own names
     */
    record Configuration(Partitions partitions, String protocolFingerprint, long seed, List<Integer> nullBlockLengths,
            int nullEnsembleSize, List<DetectorRobustnessMatrix.DetectorSpec> robustnessDetectors,
            String primaryDetector) {
        private static final String DEFAULT_PRIMARY_DETECTOR = "in-kernel-default";

        Configuration {
            Objects.requireNonNull(partitions, "partitions");
            if (protocolFingerprint == null || protocolFingerprint.isBlank()) {
                throw new IllegalArgumentException("protocolFingerprint must not be blank");
            }
            if (primaryDetector == null || primaryDetector.isBlank()) {
                throw new IllegalArgumentException("primaryDetector must not be blank");
            }
            nullBlockLengths = nullBlockLengths == null ? List.of() : List.copyOf(nullBlockLengths);
            if (nullBlockLengths.isEmpty()
                    || nullBlockLengths.stream().anyMatch(length -> length == null || length <= 0)) {
                throw new IllegalArgumentException("nullBlockLengths must contain positive values");
            }
            if (nullEnsembleSize <= 0) {
                throw new IllegalArgumentException("nullEnsembleSize must be positive");
            }
            robustnessDetectors = robustnessDetectors == null ? List.of() : List.copyOf(robustnessDetectors);
        }

        static Configuration lockedDefault() {
            return new Configuration(Partitions.lockedDefault(), DEFAULT_FINGERPRINT, 5_252_026L, List.of(20, 60), 200,
                    DetectorRobustnessMatrix.defaults(), DEFAULT_PRIMARY_DETECTOR);
        }

        /** Compact overload keeping the in-kernel default detector identity. */
        static Configuration of(final Partitions partitions, final String protocolFingerprint, final long seed,
                final List<Integer> nullBlockLengths, final int nullEnsembleSize) {
            return new Configuration(partitions, protocolFingerprint, seed, nullBlockLengths, nullEnsembleSize,
                    List.of(), DEFAULT_PRIMARY_DETECTOR);
        }
    }
}
