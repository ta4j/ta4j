/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;

/**
 * Replays an explicit, ordered chain of two or three scales over one series and
 * reports the causal parent/child relations between each adjacent pair.
 *
 * <p>
 * Each scale is a named swing detector replayed once through
 * {@link ConfirmationTracker}, so every bar sees exactly the pivots that were
 * confirmed at that bar. Parent candidates of an adjacent pair come from the
 * coarser scale's {@link TopologyAnalyzer} outcome; the finer scale only
 * contributes its confirmed pivot tape. Calibration-date guards and partition
 * membership are the ones {@link StudyRunner} uses, so a relation study cannot
 * touch dates the protocol forbids.
 *
 * <p>
 * Bounds: at most {@value ScaleRelation#MAX_SCALES} scales, at most the policy's
 * edge cap of retained edges per observation (the exact generated, retained,
 * and omitted counts are reported), and a bounded decomposition search per
 * parent leg.
 */
final class ScaleRelationStudy {

    /** A declared scale: its name and a fresh-detector supplier. */
    record ScaleInput(String name, Supplier<SwingDetector> detector) {
        ScaleInput {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("scale name must not be blank");
            }
            Objects.requireNonNull(detector, "detector");
        }
    }

    /**
     * One observation that changed the relation set or its coverage.
     *
     * @param partition  partition name of the bar
     * @param asOfIndex  observation bar index
     * @param asOfTime   observation bar end time
     * @param coverage   exact extraction bookkeeping of this observation
     * @param events     relation lifecycle events at this observation
     */
    record Frame(String partition, int asOfIndex, Instant asOfTime, ScaleRelation.Coverage coverage,
            List<ScaleRelation.Event> events) {
        Frame {
            events = List.copyOf(events);
        }
    }

    private final List<ScaleRelation.Scale> scales;
    private final List<ScaleInput> inputs;
    private final ScaleRelation.Policy policy;
    private final List<RelationshipRule> childRules;
    private final ScaleRelationExtractor.Identity identity;
    private final StudyRunner.Partitions partitions;

    ScaleRelationStudy(final List<ScaleInput> inputs, final ScaleRelation.Policy policy,
            final List<RelationshipRule> childRules, final ScaleRelationExtractor.Identity identity,
            final StudyRunner.Partitions partitions) {
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        final List<ScaleRelation.Scale> declared = new ArrayList<>(this.inputs.size());
        for (int i = 0; i < this.inputs.size(); i++) {
            declared.add(new ScaleRelation.Scale(this.inputs.get(i).name(), i));
        }
        this.scales = ScaleRelation.validateScales(declared);
        ScaleRelation.validateLinks(scales, ScaleRelation.adjacentLinks(scales));
        this.policy = Objects.requireNonNull(policy, "policy");
        this.childRules = List.copyOf(Objects.requireNonNull(childRules, "childRules"));
        this.identity = Objects.requireNonNull(identity, "identity");
        this.partitions = Objects.requireNonNull(partitions, "partitions");
    }

    /**
     * Runs the study over {@code [start, end]}.
     *
     * @param series series every scale is detected on
     * @param start  first observed bar
     * @param end    last observed bar
     * @param sink   receives each changed observation in bar order
     * @return number of observations emitted
     */
    int run(final BarSeries series, final int start, final int end, final Consumer<Frame> sink) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(sink, "sink");
        if (start > end) {
            return 0;
        }
        final List<ConfirmationTracker.CausalReplay> replays = new ArrayList<>(inputs.size());
        for (final ScaleInput input : inputs) {
            final SwingDetector detector = Objects.requireNonNull(input.detector().get(),
                    "detector supplier returned null for scale " + input.name());
            replays.add(new ConfirmationTracker(detector).observeReplay(series, end));
        }
        final TopologyAnalyzer analyzer = new TopologyAnalyzer();
        final ScaleRelationExtractor extractor = new ScaleRelationExtractor(policy, childRules, identity);
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        ScaleRelation.Coverage lastCoverage = emptyCoverage();
        int emitted = 0;
        for (int index = start;; index++) {
            final int partitionIndex = StudyRunner.partitionIndex(series, index, partitions);
            if (partitionIndex >= 0) {
                final LocalDate date = StudyRunner.barDate(series, index);
                partitions.assertCalibrationDateAllowed(date);
                final List<List<ConfirmedPivot>> tapes = new ArrayList<>(inputs.size());
                for (final ConfirmationTracker.CausalReplay replay : replays) {
                    tapes.add(replay.at(index));
                }
                final Observed observed = observe(analyzer, extractor, series, index, tapes);
                final List<ScaleRelation.Event> events = lineage.advance(index, observed.retained(),
                        new ScaleRelationLineage.Observation(observed.parentSlots(), observed.invalidatedScales(),
                                observed.omittedKeys(), edge -> childEvidencePresent(edge, tapes)));
                if (!events.isEmpty() || !observed.coverage().equals(lastCoverage)) {
                    sink.accept(new Frame(partitions.entries().get(partitionIndex).name(), index,
                            series.getBar(index).getEndTime(), observed.coverage(), events));
                    emitted++;
                    lastCoverage = observed.coverage();
                }
            }
            if (index == end) {
                break;
            }
        }
        return emitted;
    }

    private record Observed(List<ScaleRelation.Edge> retained, Set<String> omittedKeys,
            Map<String, String> parentSlots, Set<String> invalidatedScales, ScaleRelation.Coverage coverage) {
    }

    private Observed observe(final TopologyAnalyzer analyzer, final ScaleRelationExtractor extractor,
            final BarSeries series, final int index, final List<List<ConfirmedPivot>> tapes) {
        final List<ScaleRelation.Edge> generated = new ArrayList<>();
        final Map<String, String> parentSlots = new LinkedHashMap<>();
        final Set<String> invalidated = new HashSet<>();
        int parentCandidates = 0;
        int legs = 0;
        int truncatedLegs = 0;
        for (int i = 0; i + 1 < scales.size(); i++) {
            final ScaleRelation.Scale parentScale = scales.get(i);
            final ScaleRelation.Scale childScale = scales.get(i + 1);
            final TopologyAnalysis analysis = analyzer.analyze(policy.parentGrammar(), tapes.get(i), index);
            if (analysis.status() == TopologyStatus.INVALIDATED) {
                invalidated.add(parentScale.name());
            }
            final List<ScaleRelationExtractor.Parent> parents = new ArrayList<>();
            for (final TopologyCandidate candidate : analysis.candidates()) {
                final String key = identity.key(candidate);
                parents.add(new ScaleRelationExtractor.Parent(candidate, key, identity.version(candidate, List.of())));
                parentSlots.put(key, parentScale.name() + "|" + candidate.startBarIndex() + "|"
                        + candidate.direction() + "|" + candidate.grammar());
            }
            parentCandidates += parents.size();
            final ScaleRelationExtractor.Result result = extractor.extract(parentScale, childScale, parents,
                    tapes.get(i + 1), series);
            generated.addAll(result.edges());
            legs += result.legsChecked();
            truncatedLegs += result.decompositionLegsTruncated();
        }
        final List<ScaleRelation.Edge> ordered = new ArrayList<>(generated);
        // Newest evidence first, then a total order, so the cap is deterministic
        // and drops the oldest relations rather than an arbitrary subset.
        ordered.sort(Comparator.comparingInt(ScaleRelation.Edge::availableAt)
                .reversed()
                .thenComparing(ScaleRelation.Edge::parentScale)
                .thenComparing(ScaleRelation.Edge::key));
        final int retainedCount = Math.min(policy.edgeCap(), ordered.size());
        final List<ScaleRelation.Edge> retained = new ArrayList<>(ordered.subList(0, retainedCount));
        final Set<String> omitted = new HashSet<>();
        for (final ScaleRelation.Edge edge : ordered.subList(retainedCount, ordered.size())) {
            omitted.add(edge.key());
        }
        final ScaleRelation.Coverage coverage = new ScaleRelation.Coverage(parentCandidates, legs, ordered.size(),
                retainedCount, ordered.size() - retainedCount, policy.edgeCap(), truncatedLegs);
        return new Observed(retained, omitted, parentSlots, invalidated, coverage);
    }

    private ScaleRelation.Coverage emptyCoverage() {
        return new ScaleRelation.Coverage(0, 0, 0, 0, 0, policy.edgeCap(), 0);
    }

    private boolean childEvidencePresent(final ScaleRelation.Edge edge, final List<List<ConfirmedPivot>> tapes) {
        int childRank = -1;
        for (final ScaleRelation.Scale scale : scales) {
            if (scale.name().equals(edge.childScale())) {
                childRank = scale.rank();
            }
        }
        if (childRank < 0) {
            return false;
        }
        final List<ConfirmedPivot> tape = tapes.get(childRank);
        for (final ConfirmedPivot pivot : edge.childPivots()) {
            boolean found = false;
            for (final ConfirmedPivot candidate : tape) {
                if (candidate.pivotIndex() == pivot.pivotIndex()) {
                    found = candidate.type() == pivot.type() && candidate.price().compareTo(pivot.price()) == 0;
                    break;
                }
                if (candidate.pivotIndex() > pivot.pivotIndex()) {
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
