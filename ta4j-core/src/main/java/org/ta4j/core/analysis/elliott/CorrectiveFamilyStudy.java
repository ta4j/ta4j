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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;

/**
 * Replays a declared scale chain once and reports, for every parent candidate
 * and every selected {@link CorrectiveFamily} profile, a causal verdict built
 * from {@link ScaleRelationExtractor} edges.
 *
 * <p>
 * Hierarchy logic is not repeated here: parents come from the coarser scale's
 * {@link TopologyAnalyzer} outcome, child evidence from the same extractor and
 * causal confirmation replay as {@link ScaleRelationStudy}, and calibration
 * guards from {@link StudyRunner}. The study only chooses which child grammar
 * each parent leg is tested against. Profiles that need the same parent grammar
 * and child grammars (regular and expanded flat) share one extraction; a
 * profile with a different subdivision gets its own extraction with its own
 * edge budget, so enabling one profile never changes another's evidence. A
 * parent without any child evidence still gets a verdict
 * ({@code shape-compatible} with every leg {@code no-evidence}); nothing is
 * inferred from the absence of evidence.
 *
 * <p>
 * Bounds: the policy's edge cap and decomposition bounds apply per extraction
 * group; whenever a group's search was incomplete, verdicts that are not
 * already verified or excluded by their envelope are {@code unavailable}.
 *
 * @since 0.26.1
 */
final class CorrectiveFamilyStudy {

    /** Why a verdict became or stopped being the active view. */
    enum Reason {
        /** First observation of the verdict. */
        OBSERVED,
        /** Same profile and parent, new status or evidence. */
        REVISED,
        /** The parent candidate is no longer reported. */
        RETIRED,
        /** The parent scale was explicitly invalidated. */
        PARENT_INVALIDATED
    }

    /**
     * One immutable change in the active verdict set.
     *
     * @param asOfIndex observation bar index
     * @param lifecycle whether the verdict became or stopped being active
     * @param reason    cause
     * @param verdict   the verdict version concerned
     */
    record Event(int asOfIndex, ScaleRelation.Lifecycle lifecycle, Reason reason, CorrectiveFamily.Verdict verdict) {
        Event {
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /**
     * Exact extraction bookkeeping of one group at one observation.
     *
     * @param group    group identity
     * @param coverage extraction coverage
     */
    record GroupCoverage(String group, ScaleRelation.Coverage coverage) {
        GroupCoverage {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(coverage, "coverage");
        }
    }

    /**
     * One observation that changed the verdict set or a group's coverage.
     *
     * @param partition partition name of the bar
     * @param asOfIndex observation bar index
     * @param asOfTime  observation bar end time
     * @param coverage  coverage of every group in group order
     * @param events    verdict lifecycle events at this observation
     */
    record Frame(String partition, int asOfIndex, Instant asOfTime, List<GroupCoverage> coverage, List<Event> events) {
        Frame {
            coverage = List.copyOf(coverage);
            events = List.copyOf(events);
        }
    }

    /** Profiles sharing one parent grammar and child-grammar signature. */
    record Group(String id, TopologyGrammar parentGrammar, List<TopologyGrammar> childGrammars,
            List<CorrectiveFamily.Spec> specs) {
        Group {
            childGrammars = List.copyOf(childGrammars);
            specs = List.copyOf(specs);
        }
    }

    /**
     * Why a family study refuses a policy that lets child sequences skip interior
     * pivots.
     */
    static final String INTERIOR_REJECTION = "corrective-family experiments require contiguous child subdivision:"
            + " interior anchors must be contiguous because a family child sequence may not skip pivots"
            + " (a skipped interior pair would hide extra waves inside a leg)";

    private final List<ScaleRelation.Scale> scales;
    private final List<ScaleRelationStudy.ScaleInput> inputs;
    private final List<Group> groups;
    private final List<ScaleRelationExtractor> extractors;
    private final List<ScaleRelation.Policy> policies;
    private final int maxCompositions;
    private final StudyRunner.Partitions partitions;
    private final ScaleRelationExtractor.Identity identity;

    /**
     * @param inputs          ordered scales, parent first
     * @param basePolicy      interior, edge-cap and decomposition bounds shared by
     *                        every group
     * @param childRules      rules each child decomposition must satisfy
     * @param identity        candidate identity
     * @param partitions      partition protocol
     * @param specs           selected profiles, at least one, no duplicate profile
     * @param maxCompositions most compositions one verdict lists
     */
    CorrectiveFamilyStudy(final List<ScaleRelationStudy.ScaleInput> inputs, final ScaleRelation.Policy basePolicy,
            final List<RelationshipRule> childRules, final ScaleRelationExtractor.Identity identity,
            final StudyRunner.Partitions partitions, final List<CorrectiveFamily.Spec> specs,
            final int maxCompositions) {
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        final List<ScaleRelation.Scale> declared = new ArrayList<>(this.inputs.size());
        for (int i = 0; i < this.inputs.size(); i++) {
            declared.add(new ScaleRelation.Scale(this.inputs.get(i).name(), i));
        }
        this.scales = ScaleRelation.validateScales(declared);
        ScaleRelation.validateLinks(scales, ScaleRelation.adjacentLinks(scales));
        Objects.requireNonNull(basePolicy, "basePolicy");
        if (basePolicy.interior() != ScaleRelation.Interior.CONTIGUOUS) {
            throw new IllegalArgumentException(INTERIOR_REJECTION);
        }
        this.identity = Objects.requireNonNull(identity, "identity");
        this.partitions = Objects.requireNonNull(partitions, "partitions");
        if (maxCompositions < 1) {
            throw new IllegalArgumentException("maxCompositions must be positive");
        }
        this.maxCompositions = maxCompositions;
        this.groups = groupSpecs(Objects.requireNonNull(specs, "specs"));
        final List<ScaleRelationExtractor> built = new ArrayList<>(groups.size());
        final List<ScaleRelation.Policy> builtPolicies = new ArrayList<>(groups.size());
        for (final Group group : groups) {
            final List<TopologyGrammar> signature = group.childGrammars();
            final ScaleRelation.Policy policy = new ScaleRelation.Policy(group.parentGrammar(), basePolicy.interior(),
                    basePolicy.edgeCap(), basePolicy.maxDecompositionsPerLeg(), basePolicy.nodeBudgetPerLeg(),
                    (parent, leg) -> parent == group.parentGrammar() && leg < signature.size()
                            ? List.of(signature.get(leg))
                            : List.of());
            builtPolicies.add(policy);
            built.add(new ScaleRelationExtractor(policy, childRules, identity));
        }
        this.policies = List.copyOf(builtPolicies);
        this.extractors = List.copyOf(built);
    }

    /**
     * @param specs selected profiles
     * @return extraction groups ordered by id, each holding its profiles in the
     *         order given
     */
    static List<Group> groupSpecs(final List<CorrectiveFamily.Spec> specs) {
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("at least one corrective-family profile is required");
        }
        final Set<CorrectiveFamily.Profile> seen = new HashSet<>();
        final Map<String, List<CorrectiveFamily.Spec>> byId = new TreeMap<>();
        final Map<String, CorrectiveFamily.Profile> exemplar = new LinkedHashMap<>();
        for (final CorrectiveFamily.Spec spec : specs) {
            if (!seen.add(spec.profile())) {
                throw new IllegalArgumentException("duplicate corrective-family profile " + spec.profile().id());
            }
            final String id = groupId(spec.profile());
            byId.computeIfAbsent(id, key -> new ArrayList<>()).add(spec);
            exemplar.putIfAbsent(id, spec.profile());
        }
        final List<Group> groups = new ArrayList<>(byId.size());
        for (final Map.Entry<String, List<CorrectiveFamily.Spec>> entry : byId.entrySet()) {
            final CorrectiveFamily.Profile profile = exemplar.get(entry.getKey());
            groups.add(new Group(entry.getKey(), profile.parentGrammar(), profile.childGrammars(), entry.getValue()));
        }
        return List.copyOf(groups);
    }

    /** @return identity of a profile's extraction signature */
    static String groupId(final CorrectiveFamily.Profile profile) {
        final List<String> children = new ArrayList<>();
        for (final TopologyGrammar grammar : profile.childGrammars()) {
            children.add(grammar.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return profile.parentGrammar().name().toLowerCase(Locale.ROOT).replace('_', '-') + ">"
                + String.join(",", children);
    }

    /** @return the extraction groups in report order */
    List<Group> groups() {
        return groups;
    }

    /**
     * Runs the study over {@code [start, end]}.
     *
     * @param series  series every scale is detected on
     * @param start   first observed bar
     * @param end     last observed bar
     * @param replays replay cache; shared only for the series and end it is bound
     *                to
     * @param sink    receives each changed observation in bar order
     * @return number of observations emitted
     */
    int run(final BarSeries series, final int start, final int end, final DetectorReplays replays,
            final Consumer<Frame> sink) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(replays, "replays");
        Objects.requireNonNull(sink, "sink");
        if (start > end) {
            return 0;
        }
        final List<ConfirmationTracker.CausalReplay> replayList = new ArrayList<>(inputs.size());
        for (final ScaleRelationStudy.ScaleInput input : inputs) {
            replayList.add(replays.replay(series, input.detector(), end));
        }
        final TopologyAnalyzer analyzer = new TopologyAnalyzer();
        final Map<String, CorrectiveFamily.Verdict> active = new TreeMap<>();
        List<GroupCoverage> lastCoverage = emptyCoverage();
        int emitted = 0;
        for (int index = start;; index++) {
            final int partitionIndex = StudyRunner.partitionIndex(series, index, partitions);
            if (partitionIndex >= 0) {
                final LocalDate date = StudyRunner.barDate(series, index);
                partitions.assertCalibrationDateAllowed(date);
                final List<List<ConfirmedPivot>> tapes = new ArrayList<>(inputs.size());
                for (final ConfirmationTracker.CausalReplay replay : replayList) {
                    tapes.add(replay.at(index));
                }
                final Set<String> invalidated = new HashSet<>();
                final Map<String, CorrectiveFamily.Verdict> current = new TreeMap<>();
                final List<GroupCoverage> coverage = new ArrayList<>(groups.size());
                for (int g = 0; g < groups.size(); g++) {
                    coverage.add(observeGroup(g, analyzer, series, index, tapes, invalidated, current));
                }
                final List<Event> events = advance(index, active, current, invalidated);
                if (!events.isEmpty() || !coverage.equals(lastCoverage)) {
                    sink.accept(new Frame(partitions.entries().get(partitionIndex).name(), index,
                            series.getBar(index).getEndTime(), coverage, events));
                    emitted++;
                    lastCoverage = coverage;
                }
            }
            if (index == end) {
                break;
            }
        }
        return emitted;
    }

    private List<GroupCoverage> emptyCoverage() {
        final List<GroupCoverage> empty = new ArrayList<>(groups.size());
        for (int g = 0; g < groups.size(); g++) {
            empty.add(new GroupCoverage(groups.get(g).id(),
                    new ScaleRelation.Coverage(0, 0, 0, 0, 0, policies.get(g).edgeCap(), 0)));
        }
        return empty;
    }

    private GroupCoverage observeGroup(final int groupIndex, final TopologyAnalyzer analyzer, final BarSeries series,
            final int index, final List<List<ConfirmedPivot>> tapes, final Set<String> invalidated,
            final Map<String, CorrectiveFamily.Verdict> sink) {
        final Group group = groups.get(groupIndex);
        final ScaleRelation.Policy policy = policies.get(groupIndex);
        final List<ScaleRelation.Edge> generated = new ArrayList<>();
        final List<Map.Entry<ScaleRelation.Scale, List<ScaleRelationExtractor.Parent>>> parentsByScale = new ArrayList<>();
        int parentCandidates = 0;
        int legs = 0;
        int truncatedLegs = 0;
        for (int i = 0; i + 1 < scales.size(); i++) {
            final ScaleRelation.Scale parentScale = scales.get(i);
            final TopologyAnalysis analysis = analyzer.analyze(group.parentGrammar(), tapes.get(i), index);
            if (analysis.status() == TopologyStatus.INVALIDATED) {
                invalidated.add(invalidationKey(group.parentGrammar(), parentScale.name()));
            }
            final List<ScaleRelationExtractor.Parent> parents = new ArrayList<>();
            for (final TopologyCandidate candidate : analysis.candidates()) {
                parents.add(new ScaleRelationExtractor.Parent(candidate, identity.key(candidate),
                        identity.version(candidate, List.of())));
            }
            parentCandidates += parents.size();
            parentsByScale.add(Map.entry(parentScale, parents));
            final ScaleRelationExtractor.Result result = extractors.get(groupIndex)
                    .extract(parentScale, scales.get(i + 1), parents, tapes.get(i + 1), series);
            generated.addAll(result.edges());
            legs += result.legsChecked();
            truncatedLegs += result.decompositionLegsTruncated();
        }
        final List<ScaleRelation.Edge> ordered = new ArrayList<>(generated);
        ordered.sort(Comparator.comparingInt(ScaleRelation.Edge::availableAt)
                .reversed()
                .thenComparing(ScaleRelation.Edge::parentScale)
                .thenComparing(ScaleRelation.Edge::key));
        final int retainedCount = Math.min(policy.edgeCap(), ordered.size());
        final List<ScaleRelation.Edge> retained = ordered.subList(0, retainedCount);
        final ScaleRelation.Coverage coverage = new ScaleRelation.Coverage(parentCandidates, legs, ordered.size(),
                retainedCount, ordered.size() - retainedCount, policy.edgeCap(), truncatedLegs);
        final boolean complete = !coverage.incomplete();
        for (final Map.Entry<ScaleRelation.Scale, List<ScaleRelationExtractor.Parent>> entry : parentsByScale) {
            for (final ScaleRelationExtractor.Parent parent : entry.getValue()) {
                final List<ScaleRelation.Edge> own = retained.stream()
                        .filter(edge -> edge.parentScale().equals(entry.getKey().name())
                                && edge.parentCandidateKey().equals(parent.key()))
                        .toList();
                for (final CorrectiveFamily.Spec spec : group.specs()) {
                    final CorrectiveFamily.Verdict verdict = CorrectiveFamily.evaluate(spec, entry.getKey().name(),
                            parent, own, complete, maxCompositions);
                    sink.put(verdict.key(), verdict);
                }
            }
        }
        return new GroupCoverage(group.id(), coverage);
    }

    static List<Event> advance(final int index, final Map<String, CorrectiveFamily.Verdict> active,
            final Map<String, CorrectiveFamily.Verdict> current, final Set<String> invalidated) {
        final List<Event> events = new ArrayList<>();
        for (final Map.Entry<String, CorrectiveFamily.Verdict> previous : active.entrySet()) {
            if (!current.containsKey(previous.getKey())) {
                final CorrectiveFamily.Verdict verdict = previous.getValue();
                events.add(new Event(index, ScaleRelation.Lifecycle.ENDED,
                        invalidated.contains(
                                invalidationKey(verdict.spec().profile().parentGrammar(), verdict.parentScale()))
                                        ? Reason.PARENT_INVALIDATED
                                        : Reason.RETIRED,
                        verdict));
            }
        }
        for (final Map.Entry<String, CorrectiveFamily.Verdict> entry : current.entrySet()) {
            final CorrectiveFamily.Verdict before = active.get(entry.getKey());
            if (before == null) {
                events.add(new Event(index, ScaleRelation.Lifecycle.ACTIVE, Reason.OBSERVED, entry.getValue()));
            } else if (!before.version().equals(entry.getValue().version())) {
                events.add(new Event(index, ScaleRelation.Lifecycle.ACTIVE, Reason.REVISED, entry.getValue()));
            }
        }
        active.clear();
        active.putAll(current);
        return events;
    }

    /**
     * Invalidation is a property of one parent grammar on one scale: a
     * {@code MOTIVE_5} invalidation must not relabel a {@code CORRECTIVE_3} parent
     * that merely stopped being reported on the same scale.
     */
    static String invalidationKey(final TopologyGrammar parentGrammar, final String parentScale) {
        return parentGrammar.name() + '@' + parentScale;
    }
}
