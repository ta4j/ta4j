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
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;

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
 *
 * @since 0.24.2
 */
final class StudyRunner {

    /**
     * Provenance token for the in-kernel default configuration. This is NOT a
     * content hash: the frozen protocol test resource pins its own fingerprint.
     * Reports must never fake a digest.
     */
    private static final String DEFAULT_FINGERPRINT = "in-kernel-default-unpinned";

    private final Supplier<SwingDetector> detectorFactory;
    private final List<TopologyGrammar> grammars;
    private final List<RelationshipRule> rules;
    private final List<RuleAblation.Mode> ablationModes;
    private static final List<String> STRUCTURAL_COMPETING_MODES = List.of("3+3", "5+5", "7+3",
            "change-point-baseline");

    private final Configuration configuration;

    StudyRunner(final Supplier<SwingDetector> detectorFactory, final List<TopologyGrammar> grammars,
            final List<RelationshipRule> rules) {
        this(detectorFactory, grammars, rules, Configuration.lockedDefault());
    }

    StudyRunner(final Supplier<SwingDetector> detectorFactory, final List<TopologyGrammar> grammars,
            final List<RelationshipRule> rules, final Configuration configuration) {
        this(detectorFactory, grammars, rules, configuration, false);
    }

    private StudyRunner(final Supplier<SwingDetector> detectorFactory, final List<TopologyGrammar> grammars,
            final List<RelationshipRule> rules, final Configuration configuration,
            final boolean frozenAblationProtocol) {
        this.detectorFactory = Objects.requireNonNull(detectorFactory, "detectorFactory");
        this.grammars = validateGrammars(grammars);
        this.rules = validateRules(rules);
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.ablationModes = frozenAblationProtocol ? RuleAblation.frozenModes(this.rules)
                : RuleAblation.modes(this.rules);
    }

    /**
     * Creates a runner preloaded with the preregistered classical relationship
     * ladder (three hard rules plus protocol-configured wave-5 divergence momentum)
     * over the motive and cycle grammars.
     *
     * <p>
     * This is the package-private execution surface for the frozen test-plane
     * harness. The rule and grammar types stay internal to this package.
     * </p>
     *
     * @param detectorFactory      primary detector supplier
     * @param wave5MomentumFactory per-series momentum indicator factory
     * @param configuration        locked study configuration
     * @param declaredAblationSet  protocol-declared frozen ablation labels
     * @return runner bound to the supplied configuration
     * @since 0.24.2
     */
    static StudyRunner frozenPreregistered(final Supplier<SwingDetector> detectorFactory,
            final Function<BarSeries, Indicator<Num>> wave5MomentumFactory, final Configuration configuration,
            final List<String> declaredAblationSet) {
        final List<String> declared = List.copyOf(Objects.requireNonNull(declaredAblationSet, "declaredAblationSet"));
        if (!RuleAblation.frozenModeNames().equals(declared)) {
            throw new IllegalArgumentException("declaredAblationSet must match the frozen protocol ladder");
        }
        return new StudyRunner(detectorFactory, List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CYCLE_5_3),
                ClassicalRelationshipRules.classicalRelationships(wave5MomentumFactory), configuration, true);
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
     * @since 0.24.2
     */
    StudyReport evaluate(final String assetId, final BarSeries series, final int fromIndex, final int toIndex) {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("assetId must not be blank");
        }
        Objects.requireNonNull(series, "series");
        validateRange(series, fromIndex, toIndex);
        configuration.partitions().assertCalibrationConfiguration();

        final int start = Math.max(fromIndex, series.getBeginIndex());
        final int end = Math.min(toIndex, series.getEndIndex());
        final List<StudyReport.ModeReport> h1Modes = new ArrayList<>();
        // H1 is exactly the preregistered MOTIVE_5 topology claim; additional
        // caller grammars belong to the competing-grammar section below and
        // must never appear under the motive-labeled hypothesis report.
        h1Modes.add(evaluateTopologyMode(series, start, end, configuration.partitions(), detectorFactory,
                TopologyGrammar.MOTIVE_5, "topology-only"));

        final List<StudyReport.ModeReport> ablations = new ArrayList<>();
        for (final RuleAblation.Mode mode : ablationModes) {
            ablations.add(evaluateMode(series, start, end, configuration.partitions(), detectorFactory,
                    TopologyGrammar.CYCLE_5_3, mode.name(), mode.rules()));
        }

        final List<StudyReport.ModeReport> competing = new ArrayList<>();
        final Set<String> competingNames = new LinkedHashSet<>();
        if (configuration.competingModes() != null) {
            // A frozen protocol executes exactly its declared competing set;
            // undeclared kernel experiments stay out of the report.
            competingNames.addAll(configuration.competingModes());
        } else {
            for (final TopologyGrammar grammar : TopologyGrammar.values()) {
                competingNames.add(grammar.name());
            }
            for (final TopologyGrammar grammar : grammars) {
                competingNames.add(grammar.name());
            }
            competingNames.addAll(STRUCTURAL_COMPETING_MODES);
        }
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
        final List<TopologyGrammar> nullGrammars = List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CYCLE_5_3);
        final boolean hasEvaluationWindow = start <= end;
        final Partitions partitions = configuration.partitions();
        for (final int blockLength : configuration.nullBlockLengths()) {
            final Map<TopologyGrammar, List<MetricAccumulator>> totalsByGrammar = new LinkedHashMap<>();
            final Map<TopologyGrammar, List<List<MetricAccumulator>>> memberTotalsByGrammar = new LinkedHashMap<>();
            for (final TopologyGrammar grammar : nullGrammars) {
                totalsByGrammar.put(grammar, newAccumulators(List.of(), partitions));
                final List<List<MetricAccumulator>> memberTotals = new ArrayList<>(configuration.nullEnsembleSize());
                for (int memberIndex = 0; memberIndex < configuration.nullEnsembleSize(); memberIndex++) {
                    memberTotals.add(newAccumulators(List.of(), partitions));
                }
                memberTotalsByGrammar.put(grammar, memberTotals);
            }
            final List<List<MetricAccumulator>> h2Totals = new ArrayList<>(ablationModes.size());
            final List<List<List<MetricAccumulator>>> h2MemberTotals = new ArrayList<>(ablationModes.size());
            for (final RuleAblation.Mode mode : ablationModes) {
                h2Totals.add(newAccumulators(mode.rules(), partitions));
                final List<List<MetricAccumulator>> memberTotals = new ArrayList<>(configuration.nullEnsembleSize());
                for (int memberIndex = 0; memberIndex < configuration.nullEnsembleSize(); memberIndex++) {
                    memberTotals.add(newAccumulators(mode.rules(), partitions));
                }
                h2MemberTotals.add(memberTotals);
            }
            // An evaluation window before or after the series records no
            // real topology; generating full-series null ensembles anyway
            // would compare non-empty null partitions against empty real
            // ones. Keep both sides symmetrically empty instead.
            if (hasEvaluationWindow) {
                final int sourceBegin = source.getBeginIndex();
                final BarSeries causalSource = subSeriesThrough(source, sourceBegin, end);
                // Look-ahead-free sampling: every partition's ensemble is drawn
                // only from returns available at that partition's last bar, so a
                // calibration partition's null baseline can never incorporate
                // validation or holdout returns. The shared seed keeps the RNG
                // stream comparable across partitions over different tapes.
                for (int partitionIndex = 0; partitionIndex < totalsByGrammar.get(nullGrammars.get(0))
                        .size(); partitionIndex++) {
                    final int partitionLastBar = lastBarInPartition(causalSource, partitions, partitionIndex);
                    if (partitionLastBar < causalSource.getBeginIndex()) {
                        continue;
                    }
                    if (partitionLastBar == causalSource.getBeginIndex()) {
                        recordSingleBarNullPartition(causalSource, sourceBegin, partitionIndex, partitionLastBar, start,
                                nullGrammars, totalsByGrammar, memberTotalsByGrammar, h2Totals, h2MemberTotals);
                        continue;
                    }
                    final BarSeries truncated = subSeriesThrough(causalSource, causalSource.getBeginIndex(),
                            partitionLastBar);
                    final int partition = partitionIndex;
                    BlockBootstrapNulls.forEachMember(truncated, blockLength, configuration.nullEnsembleSize(),
                            configuration.seed(), (memberIndex, member) -> {
                                final ConfirmationTracker.CausalReplay replay = observeReplay(member);
                                // Members are freshly-built series rebased to index 0;
                                // the requested window stays in source coordinates and
                                // must be translated before recording. Fresh accumulators
                                // per member and grammar so label-stability transitions
                                // never leak across ensemble members.
                                for (final TopologyGrammar grammar : nullGrammars) {
                                    final List<MetricAccumulator> memberAccumulators = newAccumulators(List.of(),
                                            partitions);
                                    final List<List<MetricAccumulator>> modeAccumulators = new ArrayList<>(
                                            ablationModes.size());
                                    final List<TopologyRecording> recordings = new ArrayList<>(
                                            1 + ablationModes.size());
                                    recordings.add(new TopologyRecording(List.of(), memberAccumulators));
                                    if (grammar == TopologyGrammar.CYCLE_5_3) {
                                        for (final RuleAblation.Mode mode : ablationModes) {
                                            final List<MetricAccumulator> modeMetrics = newAccumulators(mode.rules(),
                                                    partitions);
                                            modeAccumulators.add(modeMetrics);
                                            recordings.add(new TopologyRecording(mode.rules(), modeMetrics));
                                        }
                                    }
                                    recordTopologyWithRecordings(member,
                                            Math.max(member.getBeginIndex(), start - sourceBegin), member.getEndIndex(),
                                            partitions, replay, grammar, recordings, sourceBegin);
                                    totalsByGrammar.get(grammar)
                                            .get(partition)
                                            .mergeFrom(memberAccumulators.get(partition));
                                    memberTotalsByGrammar.get(grammar)
                                            .get(memberIndex)
                                            .get(partition)
                                            .mergeFrom(memberAccumulators.get(partition));
                                    if (grammar == TopologyGrammar.CYCLE_5_3) {
                                        for (int modeIndex = 0; modeIndex < modeAccumulators.size(); modeIndex++) {
                                            final MetricAccumulator modeMetrics = modeAccumulators.get(modeIndex)
                                                    .get(partition);
                                            h2Totals.get(modeIndex).get(partition).mergeFrom(modeMetrics);
                                            h2MemberTotals.get(modeIndex)
                                                    .get(memberIndex)
                                                    .get(partition)
                                                    .mergeFrom(modeMetrics);
                                        }
                                    }
                                }
                            });
                }
            }
            for (final TopologyGrammar grammar : nullGrammars) {
                final List<StudyReport.NullModeReport> modes = grammar == TopologyGrammar.CYCLE_5_3
                        ? nullModeReports(ablationModes, h2Totals, h2MemberTotals, partitions)
                        : List.of();
                reports.add(new StudyReport.NullReport(grammar.name(), blockLength, configuration.nullEnsembleSize(),
                        configuration.seed(), metrics(totalsByGrammar.get(grammar), partitions),
                        memberMetrics(memberTotalsByGrammar.get(grammar), partitions), modes));
            }
        }
        return List.copyOf(reports);
    }

    /**
     * Emits the configured null ensemble outcomes for a partition whose causal
     * prefix contains exactly one bar.
     *
     * <p>
     * The frozen stationary bootstrap requires at least two bars, so no member
     * series can be generated for such a prefix. The real modes still observe that
     * single bar (typically recording {@code INSUFFICIENT_HISTORY}), so every
     * configured ensemble member emits the matching degenerate outcome; skipping
     * the partition outright would report zero null evaluations against a non-empty
     * real sample.
     * </p>
     */
    private void recordSingleBarNullPartition(final BarSeries causalSource, final int sourceBegin,
            final int partitionIndex, final int partitionLastBar, final int requestedStart,
            final List<TopologyGrammar> nullGrammars,
            final Map<TopologyGrammar, List<MetricAccumulator>> totalsByGrammar,
            final Map<TopologyGrammar, List<List<MetricAccumulator>>> memberTotalsByGrammar,
            final List<List<MetricAccumulator>> h2Totals, final List<List<List<MetricAccumulator>>> h2MemberTotals) {
        // Members are rebased to position zero, and the recorded-index offset
        // restores source coordinates; the single prefix bar sits at position
        // zero of the causal tape.
        final int recordedIndex = sourceBegin + partitionLastBar - causalSource.getBeginIndex();
        if (recordedIndex < requestedStart) {
            // Outside the requested window the real modes record nothing, so
            // the null side stays symmetrically silent too.
            return;
        }
        final Partitions partitions = configuration.partitions();
        partitions.assertCalibrationDateAllowed(barDate(causalSource, partitionLastBar));
        for (int memberIndex = 0; memberIndex < configuration.nullEnsembleSize(); memberIndex++) {
            for (final TopologyGrammar grammar : nullGrammars) {
                final MetricAccumulator memberMetrics = new MetricAccumulator(List.of());
                memberMetrics.recordInsufficientHistory(recordedIndex);
                totalsByGrammar.get(grammar).get(partitionIndex).mergeFrom(memberMetrics);
                memberTotalsByGrammar.get(grammar).get(memberIndex).get(partitionIndex).mergeFrom(memberMetrics);
                if (grammar == TopologyGrammar.CYCLE_5_3) {
                    for (int modeIndex = 0; modeIndex < h2Totals.size(); modeIndex++) {
                        final MetricAccumulator modeMetrics = new MetricAccumulator(
                                ablationModes.get(modeIndex).rules());
                        modeMetrics.recordInsufficientHistory(recordedIndex);
                        h2Totals.get(modeIndex).get(partitionIndex).mergeFrom(modeMetrics);
                        h2MemberTotals.get(modeIndex).get(memberIndex).get(partitionIndex).mergeFrom(modeMetrics);
                    }
                }
            }
        }
    }

    /**
     * Returns the last bar of {@code series} whose date belongs to the given
     * partition, or -1 when the partition has no bars in the series.
     */
    private static BarSeries subSeriesThrough(final BarSeries series, final int start, final int inclusiveEnd) {
        if (inclusiveEnd == series.getEndIndex()) {
            return series;
        }
        return series.getSubSeries(start, Math.addExact(inclusiveEnd, 1));
    }

    private static int lastBarInPartition(final BarSeries series, final Partitions partitions,
            final int partitionIndex) {
        int last = -1;
        final int begin = series.getBeginIndex();
        final int end = series.getEndIndex();
        if (begin <= end) {
            for (int index = begin;; index++) {
                if (partitionIndex(series, index, partitions) == partitionIndex) {
                    last = index;
                }
                if (index == end) {
                    break;
                }
            }
        }
        return last;
    }

    private ConfirmationTracker.CausalReplay observeReplay(final BarSeries series) {
        return observeReplay(series, detectorFactory, series.getEndIndex());
    }

    private StudyReport.ModeReport evaluateMode(final BarSeries series, final int start, final int end,
            final Partitions partitions, final Supplier<SwingDetector> factory, final TopologyGrammar grammar,
            final String mode, final List<RelationshipRule> activeRules) {
        final List<MetricAccumulator> accumulators = newAccumulators(activeRules);
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory, end);
        recordTopology(series, start, end, partitions, replay, grammar, activeRules, accumulators, 0);
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
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory, end);
        recordTopology(series, start, end, partitions, replay, grammar, List.of(), accumulators, 0);
        return new StudyReport.ModeReport(mode, grammar.name(), List.of(), metrics(accumulators, partitions));
    }

    private record TopologyRecording(List<RelationshipRule> activeRules, List<MetricAccumulator> accumulators) {
    }

    private static void recordTopology(final BarSeries series, final int start, final int end,
            final Partitions partitions, final ConfirmationTracker.CausalReplay replay, final TopologyGrammar grammar,
            final List<RelationshipRule> activeRules, final List<MetricAccumulator> accumulators,
            final int recordedIndexOffset) {
        recordTopologyWithRecordings(series, start, end, partitions, replay, grammar,
                List.of(new TopologyRecording(activeRules, accumulators)), recordedIndexOffset);
    }

    private static void recordTopologyWithRecordings(final BarSeries series, final int start, final int end,
            final Partitions partitions, final ConfirmationTracker.CausalReplay replay, final TopologyGrammar grammar,
            final List<TopologyRecording> recordings, final int recordedIndexOffset) {
        if (start > end) {
            return;
        }
        for (int index = start;; index++) {
            final int partitionIndex = partitionIndex(series, index, partitions);
            if (partitionIndex >= 0) {
                final LocalDate date = barDate(series, index);
                partitions.assertCalibrationDateAllowed(date);
                final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(grammar, replay.at(index), index);
                // Null ensemble members are rebased sub-series; the offset restores
                // source coordinates so null and real metric bounds are comparable.
                for (final TopologyRecording recording : recordings) {
                    recording.accumulators()
                            .get(partitionIndex)
                            .record(analysis, index + recordedIndexOffset, recording.activeRules(), series);
                }
            }
            if (index == end) {
                break;
            }
        }
    }

    private static StudyReport.ModeReport evaluateAlternativeGrammar(final BarSeries series, final int start,
            final int end, final Partitions partitions, final Supplier<SwingDetector> factory, final String name) {
        final AlternativeGrammar grammar = AlternativeGrammar.of(name);
        final List<MetricAccumulator> accumulators = newAccumulators(List.of(), partitions);
        final ConfirmationTracker.CausalReplay replay = observeReplay(series, factory, end);
        if (start <= end) {
            for (int index = start;; index++) {
                final int partitionIndex = partitionIndex(series, index, partitions);
                if (partitionIndex >= 0) {
                    final LocalDate date = barDate(series, index);
                    partitions.assertCalibrationDateAllowed(date);
                    final List<ConfirmedPivot> visible = replay.at(index);
                    final List<String> matches = grammar.matches(visible);
                    final MetricAccumulator accumulator = accumulators.get(partitionIndex);
                    if (visible.size() < 2) {
                        accumulator.recordAlternative(index, false, false, false, "insufficient-history",
                                Set.of("insufficient-history"));
                    } else if (matches.size() == 1) {
                        accumulator.recordAlternative(index, true, false, false, matches.get(0),
                                Set.of(matches.get(0)));
                    } else if (matches.size() > 1) {
                        // Ambiguity stability must compare the actual placement
                        // identities, not a constant token, or the Jaccard metric
                        // reads 1 across shifting match sets.
                        accumulator.recordAlternative(index, false, true, false, "ambiguous", Set.copyOf(matches));
                    } else {
                        final Set<String> partialMatches = grammar.partialMatches(visible);
                        if (!partialMatches.isEmpty()) {
                            accumulator.recordAlternative(index, false, false, true, "forming", partialMatches);
                        } else {
                            accumulator.recordAlternative(index, false, false, false, "no-match", Set.of("no-match"));
                        }
                    }
                }
                if (index == end) {
                    break;
                }
            }
        }
        return new StudyReport.ModeReport("competing-" + name, name, List.of(), metrics(accumulators, partitions));
    }

    private static StudyReport.ModeReport evaluateChangePointBaseline(final BarSeries series, final int start,
            final int end, final Partitions partitions) {
        final List<MetricAccumulator> accumulators = newAccumulators(List.of(), partitions);
        if (start <= end) {
            for (int index = start;; index++) {
                final int partitionIndex = partitionIndex(series, index, partitions);
                if (partitionIndex >= 0) {
                    final LocalDate date = barDate(series, index);
                    partitions.assertCalibrationDateAllowed(date);
                    final MetricAccumulator accumulator = accumulators.get(partitionIndex);
                    if (index - 2 < series.getBeginIndex()) {
                        accumulator.recordAlternative(index, false, false, false, "insufficient-history",
                                Set.of("insufficient-history"));
                    } else {
                        final Num previousPreviousClose = series.getBar(index - 2).getClosePrice();
                        final Num previousClose = series.getBar(index - 1).getClosePrice();
                        final Num currentClose = series.getBar(index).getClosePrice();
                        final Num first = previousClose.minus(previousPreviousClose);
                        final Num second = currentClose.minus(previousClose);
                        requireFiniteChangePointInputs(index, previousPreviousClose, previousClose, currentClose, first,
                                second);
                        final boolean change = !first.isZero() && !second.isZero()
                                && first.isPositive() != second.isPositive();
                        final String label = change ? "change@" + index : "stable";
                        accumulator.recordAlternative(index, change, false, false, label, Set.of(label));
                    }
                }
                if (index == end) {
                    break;
                }
            }
        }
        return new StudyReport.ModeReport("competing-change-point-baseline", "change-point-baseline", List.of(),
                metrics(accumulators, partitions));
    }

    /**
     * Hard finite-input guard for the change-point baseline. The classifier only
     * subtracts closes and compares signs, so a NaN or infinite close would
     * otherwise surface as a phantom sign flip ({@code 1, +Infinity, 1} classifies
     * as a change) or vanish into a zero delta and silently corrupt partition
     * metrics. Rejecting the whole classification window mirrors the bootstrap's
     * finite-close rejection instead of recording a dishonest sample.
     */
    private static void requireFiniteChangePointInputs(final int index, final Num previousPreviousClose,
            final Num previousClose, final Num currentClose, final Num firstDelta, final Num secondDelta) {
        if (!Num.isFinite(previousPreviousClose) || !Num.isFinite(previousClose) || !Num.isFinite(currentClose)
                || !Num.isFinite(firstDelta) || !Num.isFinite(secondDelta)) {
            throw new IllegalArgumentException("change-point-baseline requires finite close prices; bar " + index
                    + " holds closes [" + previousPreviousClose + ", " + previousClose + ", " + currentClose + "]");
        }
    }

    private static ConfirmationTracker.CausalReplay observeReplay(final BarSeries series,
            final Supplier<SwingDetector> factory, final int endIndex) {
        final SwingDetector detector = Objects.requireNonNull(factory, "detectorFactory").get();
        final SwingDetector nonNullDetector = Objects.requireNonNull(detector, "detectorFactory returned null");
        // Causally truncate: a detector contradiction on a bar beyond the
        // requested range must not abort a report about an earlier interval.
        return new ConfirmationTracker(nonNullDetector).observeReplay(series, endIndex);
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

    private static List<StudyReport.NullMemberMetrics> memberMetrics(
            final List<List<MetricAccumulator>> memberAccumulators, final Partitions partitions) {
        final int partitionCount = partitions.entries().size();
        final List<StudyReport.NullMemberMetrics> metrics = new ArrayList<>(memberAccumulators.size() * partitionCount);
        for (int memberIndex = 0; memberIndex < memberAccumulators.size(); memberIndex++) {
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
                final String partitionName = partitions.entries().get(partitionIndex).name();
                metrics.add(new StudyReport.NullMemberMetrics(memberIndex, partitionName,
                        List.of(memberAccumulators.get(memberIndex).get(partitionIndex).toMetrics(partitionName))));
            }
        }
        return List.copyOf(metrics);
    }

    private static List<StudyReport.NullModeReport> nullModeReports(final List<RuleAblation.Mode> modes,
            final List<List<MetricAccumulator>> totals, final List<List<List<MetricAccumulator>>> memberTotals,
            final Partitions partitions) {
        final List<StudyReport.NullModeReport> reports = new ArrayList<>(modes.size());
        for (int modeIndex = 0; modeIndex < modes.size(); modeIndex++) {
            final RuleAblation.Mode mode = modes.get(modeIndex);
            reports.add(new StudyReport.NullModeReport(mode.name(), activeRuleIds(mode.rules()),
                    metrics(totals.get(modeIndex), partitions),
                    memberMetrics(memberTotals.get(modeIndex), partitions)));
        }
        return List.copyOf(reports);
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
        // H1 is declared over MOTIVE_5; a configuration that omits it would
        // emit an H1 section whose label and measurements contradict each
        // other. Reject rather than silently widening the evaluation.
        if (!supplied.contains(TopologyGrammar.MOTIVE_5)) {
            throw new IllegalArgumentException("grammars must include MOTIVE_5 for the preregistered H1 claim");
        }
        // H2 is declared over CYCLE_5_3; a configuration that omits it would
        // emit an H2 ablation ladder whose label contradicts the measured
        // grammar. Reject rather than silently emitting an undeclared result.
        if (!supplied.contains(TopologyGrammar.CYCLE_5_3)) {
            throw new IllegalArgumentException("grammars must include CYCLE_5_3 for the preregistered H2 claim");
        }
        // Duplicate entries would emit indistinguishable H1/mode rows for the
        // same experiment and invite double counting, mirroring the duplicate
        // rule-id rejection below.
        if (supplied.size() != Set.copyOf(supplied).size()) {
            throw new IllegalArgumentException("grammars must not contain duplicates");
        }
        return List.copyOf(supplied);
    }

    private static List<RelationshipRule> validateRules(final List<RelationshipRule> supplied) {
        Objects.requireNonNull(supplied, "rules");
        final List<RelationshipRule> copy = List.copyOf(supplied);
        final Set<String> identifiers = new HashSet<>();
        for (final RelationshipRule rule : copy) {
            Objects.requireNonNull(rule, "rules contains null");
            final String identifier = rule.id();
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("rule id must not be blank");
            }
            if (!identifiers.add(identifier)) {
                throw new IllegalArgumentException("duplicate relationship rule id: " + identifier);
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
        private long jointEvaluationCount;
        private long jointPassCount;
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
                updateStability(Set.of("forming:" + analysis.direction() + ":" + analysis.formingStartBarIndex() + "-"
                        + analysis.formingEndBarIndex()));
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
                updateStability(Set.of("invalidated:" + analysis.explanation()));
            }
            case INSUFFICIENT_HISTORY -> {
                insufficientHistoryCount++;
                updateStability(Set.of("insufficient-history"));
            }
            default -> throw new IllegalStateException("unhandled topology status " + analysis.status());
            }
        }

        private void recordAlternative(final int index, final boolean complete, final boolean ambiguous,
                final boolean forming, final String label, final Set<String> stabilityLabels) {
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
            updateStability(stabilityLabels);
        }

        /**
         * Records the degenerate outcome of an evaluation whose history cannot support
         * topology analysis, mirroring the {@code INSUFFICIENT_HISTORY} branch of
         * {@link #record}.
         */
        private void recordInsufficientHistory(final int index) {
            evaluationCount++;
            firstIndex = Math.min(firstIndex, index);
            lastIndex = Math.max(lastIndex, index);
            insufficientHistoryCount++;
            updateStability(Set.of("insufficient-history"));
        }

        private void evaluateRules(final TopologyCandidate candidate, final List<RelationshipRule> activeRules,
                final BarSeries series) {
            if (activeRules.isEmpty()) {
                return;
            }
            boolean allRulesPass = true;
            for (int index = 0; index < activeRules.size(); index++) {
                final RelationshipRule rule = activeRules.get(index);
                final RuleEvidence evidence = rule.evaluate(candidate, series);
                // Evidence carrying another rule's id would silently credit a
                // foreign ledger row; reject before any state or score lands.
                if (!rule.id().equals(evidence.ruleId())) {
                    throw new IllegalArgumentException("rule evidence id mismatch: rule " + rule.id()
                            + " returned evidence for " + evidence.ruleId());
                }
                evidenceEvaluationCount++;
                if (evidence.state() != EvidenceState.PASS) {
                    allRulesPass = false;
                }
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
            jointEvaluationCount++;
            if (allRulesPass) {
                jointPassCount++;
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
            jointEvaluationCount += other.jointEvaluationCount;
            jointPassCount += other.jointPassCount;
            for (int index = 0; index < ruleCounters.size() && index < other.ruleCounters.size(); index++) {
                ruleCounters.get(index).mergeFrom(other.ruleCounters.get(index));
            }
        }

        private StudyReport.PartitionMetrics toMetrics(final String partition) {
            final long denominator = evaluationCount;
            final List<StudyReport.RuleMetrics> rules = ruleCounters.stream().map(RuleCounter::toMetrics).toList();
            return new StudyReport.PartitionMetrics(partition, firstIndex == Integer.MAX_VALUE ? -1 : firstIndex,
                    lastIndex == Integer.MIN_VALUE ? -1 : lastIndex, evaluationCount, completeCount, formingCount,
                    ambiguousCount, noMatchCount, invalidatedCount, insufficientHistoryCount,
                    ratio(completeCount, denominator), ratio(ambiguousCount, denominator),
                    ratio(noMatchCount, denominator), ratio(confirmationLagSum, confirmationLagCount),
                    ratio(stabilitySum, stabilityCount), evidenceEvaluationCount, evidencePassCount, evidenceFailCount,
                    evidencePendingCount, evidenceUnavailableCount, evidenceNotApplicableCount,
                    ratio(evidencePassCount, evidenceEvaluationCount), jointEvaluationCount, jointPassCount,
                    ratio(jointPassCount, jointEvaluationCount), rules);
        }

        private static double ratio(final long numerator, final long denominator) {
            return denominator == 0 ? Double.NaN : (double) numerator / denominator;
        }

        private static double ratio(final double numerator, final long denominator) {
            return denominator == 0 ? Double.NaN : numerator / denominator;
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
                    evaluationCount == 0 ? Double.NaN : (double) passCount / evaluationCount, scoredCount, scoreMean(),
                    scoredCount == 0 ? Double.NaN : scoreMin, scoredCount == 0 ? Double.NaN : scoreMax);
        }

        private double scoreMean() {
            return scoredCount == 0 ? Double.NaN : scoreSum / scoredCount;
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
            if (pivots.size() < required) {
                return List.of();
            }
            final List<String> matches = new ArrayList<>();
            // A placement stays a live hypothesis while its trailing edge is
            // within one pattern-length of the newest pivot, inclusive: a
            // complete placement ending exactly one pattern length behind the
            // newest pivot remains eligible. Older completions are retired so
            // distant history cannot freeze every later bar into permanent
            // ambiguity.
            final int horizonPosition = Math.max(0, pivots.size() - required);
            for (int start = 0; start + required <= pivots.size(); start++) {
                final List<ConfirmedPivot> window = pivots.subList(start, start + required);
                if (matchesWindow(window) && start + required - 1 >= horizonPosition) {
                    matches.add(window.get(0).pivotIndex() + "-" + window.get(window.size() - 1).pivotIndex());
                }
            }
            return matches;
        }

        private Set<String> partialMatches(final List<ConfirmedPivot> pivots) {
            final int required = segmentLegs[0] + segmentLegs[1] + 1;
            final int maxSuffix = Math.min(pivots.size(), required - 1);
            // A forming claim requires the whole leading segment to be observable
            // in the suffix window.
            final int minSuffix = segmentLegs[0] + 1;
            if (maxSuffix < minSuffix) {
                // The leading segment is not observable yet: no honest forming claim
                // is possible, however well the short tail happens to fit.
                return Set.of();
            }
            final Set<String> matches = new HashSet<>();
            for (int suffix = maxSuffix; suffix >= minSuffix; suffix--) {
                final List<ConfirmedPivot> window = pivots.subList(pivots.size() - suffix, pivots.size());
                for (final WaveDirection direction : WaveDirection.values()) {
                    if (matchesLegSequence(window, direction, false)) {
                        matches.add(direction + ":" + window.get(0).pivotIndex() + "-"
                                + window.get(window.size() - 1).pivotIndex());
                    }
                }
            }
            return Set.copyOf(matches);
        }

        private boolean matchesWindow(final List<ConfirmedPivot> window) {
            for (final WaveDirection direction : WaveDirection.values()) {
                if (matchesLegSequence(window, direction, true)) {
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
            final SwingPivotType expectedOrigin = direction == WaveDirection.BULLISH ? SwingPivotType.LOW
                    : SwingPivotType.HIGH;
            if (window.isEmpty() || window.get(0).type() != expectedOrigin) {
                return false;
            }
            for (int leg = 0; leg < window.size() - 1; leg++) {
                if (window.get(leg).type() == window.get(leg + 1).type()) {
                    return false;
                }
                final Num delta = window.get(leg + 1).price().minus(window.get(leg).price());
                final Num signed = direction == WaveDirection.BULLISH ? delta : delta.negate();
                final boolean positive = leg < segmentLegs[0] ? leg % 2 == 0 : (leg - segmentLegs[0]) % 2 != 0;
                if (signed.isZero()) {
                    // Flat legs are tolerated only as the uncommitted trailing
                    // leg of a partial window; an earlier flat leg has already
                    // contradicted the required decisive direction.
                    if (complete || leg < window.size() - 2) {
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

    /**
     * One inclusive date partition for a locked named evaluation window.
     *
     * @since 0.24.2
     */
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

    /**
     * Immutable locked partition set with the forbidden calibration boundary.
     *
     * @since 0.24.2
     */
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

    /**
     * Complete protocol-independent locked study configuration.
     *
     * @param partitions          locked evaluation windows
     * @param protocolFingerprint verified protocol hash this run executes
     * @param seed                null-ensemble seed
     * @param nullBlockLengths    stationary bootstrap block lengths
     * @param nullEnsembleSize    members generated per grammar and block length
     * @param robustnessDetectors detector matrix specifications
     * @param primaryDetector     stable name of the primary detector; robustness
     *                            rows carry their own names
     * @param competingModes      preregistered competing-mode names executed by the
     *                            study; {@code null} runs the engine-default spread
     *                            of every kernel grammar plus the structural
     *                            alternatives
     * @since 0.24.2
     */
    record Configuration(Partitions partitions, String protocolFingerprint, long seed, List<Integer> nullBlockLengths,
            int nullEnsembleSize, List<DetectorRobustnessMatrix.DetectorSpec> robustnessDetectors,
            String primaryDetector, List<String> competingModes) {
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
            // A duplicated block length would run the identical ensemble twice
            // and double-count it in every aggregate.
            if (nullBlockLengths.stream().distinct().count() != nullBlockLengths.size()) {
                throw new IllegalArgumentException("nullBlockLengths must not contain duplicates");
            }
            if (nullEnsembleSize <= 0) {
                throw new IllegalArgumentException("nullEnsembleSize must be positive");
            }
            robustnessDetectors = robustnessDetectors == null ? List.of() : List.copyOf(robustnessDetectors);
            final Set<String> detectorNames = new HashSet<>();
            for (final DetectorRobustnessMatrix.DetectorSpec detector : robustnessDetectors) {
                if (!detectorNames.add(detector.name())) {
                    throw new IllegalArgumentException(
                            "robustness detector names must not contain duplicates: " + detector.name());
                }
            }
            // A frozen protocol must never silently widen its declared
            // competing set: unknown names fail here instead of running an
            // undeclared experiment.
            if (competingModes != null) {
                final Set<String> declaredModes = new HashSet<>();
                for (final String mode : competingModes) {
                    if (mode == null || mode.isBlank()) {
                        throw new IllegalArgumentException("competingModes must not contain blank entries");
                    }
                    if (!declaredModes.add(mode)) {
                        throw new IllegalArgumentException("competingModes must not contain duplicates: " + mode);
                    }
                    if (STRUCTURAL_COMPETING_MODES.contains(mode)) {
                        continue;
                    }
                    boolean knownGrammar = false;
                    for (final TopologyGrammar grammar : TopologyGrammar.values()) {
                        if (grammar.name().equals(mode)) {
                            knownGrammar = true;
                            break;
                        }
                    }
                    if (!knownGrammar) {
                        throw new IllegalArgumentException("competingModes contains unsupported mode: " + mode);
                    }
                }
                competingModes = List.copyOf(competingModes);
            }
        }

        /** @return defensive copies; configuration is shared across modules. */
        @Override
        public List<Integer> nullBlockLengths() {
            return List.copyOf(nullBlockLengths);
        }

        /** @return defensive copies; configuration is shared across modules. */
        @Override
        public List<DetectorRobustnessMatrix.DetectorSpec> robustnessDetectors() {
            return List.copyOf(robustnessDetectors);
        }

        /** @return defensive copies when configured; null keeps the engine default. */
        @Override
        public List<String> competingModes() {
            return competingModes == null ? null : List.copyOf(competingModes);
        }

        /**
         * Engine-default configuration for in-kernel studies.
         *
         * @return locked engine-default configuration
         * @since 0.24.2
         */
        static Configuration lockedDefault() {
            return new Configuration(Partitions.lockedDefault(), DEFAULT_FINGERPRINT, 5_252_026L, List.of(20, 60), 200,
                    DetectorRobustnessMatrix.defaults(), DEFAULT_PRIMARY_DETECTOR, null);
        }

        /**
         * Compact overload keeping the in-kernel default detector identity.
         * 
         * @since 0.24.2
         */
        static Configuration of(final Partitions partitions, final String protocolFingerprint, final long seed,
                final List<Integer> nullBlockLengths, final int nullEnsembleSize) {
            return new Configuration(partitions, protocolFingerprint, seed, nullBlockLengths, nullEnsembleSize,
                    List.of(), DEFAULT_PRIMARY_DETECTOR, null);
        }
    }
}
