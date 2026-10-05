/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.ta4j.core.BarSeries;

/**
 * Classifies how legs of parent-scale candidates relate to the confirmed pivot
 * tape of the next finer declared scale at one as-of cursor.
 *
 * <p>
 * The tape must already be causal: it is the child scale's confirmed pivots as
 * known at the cursor, so a relation never uses evidence that was not yet
 * confirmed. Anchors match only by exact pivot index, type, and price; nothing
 * is ever snapped to a nearest pivot. Every valid child decomposition is kept
 * as its own edge; none is preferred by score.
 *
 * <p>
 * Decision table for a parent leg from anchor {@code a} to anchor {@code b}
 * with {@code inside} the child pivots strictly between them:
 * <ul>
 * <li>a child pivot at {@code a} or {@code b}'s index that differs in type or
 * price: {@code not-nested};</li>
 * <li>no child grammar declared for the leg: {@code contained-only} when child
 * pivots lie inside;</li>
 * <li>both anchors exact: every grammar-satisfying ordered decomposition is an
 * edge ({@code subdivision-supported}, or {@code conflicting-evidence} /
 * {@code pending-confirmation} / {@code contained-only} when a rule fails, is
 * pending, or is unavailable); with no valid decomposition the leg is
 * {@code not-nested} when nothing lies inside and {@code conflicting-evidence}
 * otherwise;</li>
 * <li>start anchor exact, child tape not yet past {@code b}: {@code
 * pending-confirmation} while the prefix is still consistent, else
 * {@code conflicting-evidence};</li>
 * <li>start anchor exact but the tape continues past {@code b} without an exact
 * end anchor: {@code not-nested};</li>
 * <li>start anchor absent with child pivots inside:
 * {@code contained-only}.</li>
 * </ul>
 */
final class ScaleRelationExtractor {

    private static final int EVIDENCE_CACHE_LIMIT = 4_096;

    private final ScaleRelation.Policy policy;
    private final List<RelationshipRule> childRules;
    private final Identity identity;
    private final TopologyAnalyzer analyzer = new TopologyAnalyzer();
    private final Map<String, List<RuleEvidence>> evidenceCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, List<RuleEvidence>> eldest) {
            return size() > EVIDENCE_CACHE_LIMIT;
        }
    };

    /** Stable identity of candidates, shared with the observation trace. */
    interface Identity {
        String key(TopologyCandidate candidate);

        String version(TopologyCandidate candidate, List<RuleEvidence> evidence);
    }

    /** A parent-scale candidate with its identity. */
    record Parent(TopologyCandidate candidate, String key, String version) {
        Parent {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(version, "version");
        }
    }

    /**
     * Edges found for one parent/child scale pair.
     *
     * @param edges                      edges in deterministic order
     * @param legsChecked                parent legs examined
     * @param decompositionLegsTruncated legs whose search hit a bound
     */
    record Result(List<ScaleRelation.Edge> edges, int legsChecked, int decompositionLegsTruncated) {
        Result {
            edges = List.copyOf(edges);
        }
    }

    ScaleRelationExtractor(final ScaleRelation.Policy policy, final List<RelationshipRule> childRules,
            final Identity identity) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.childRules = List.copyOf(Objects.requireNonNull(childRules, "childRules"));
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    /**
     * Extracts the relations of one adjacent scale pair.
     *
     * @param parentScale parent (coarser) scale
     * @param childScale  child (finer) scale
     * @param parents     parent candidates available at the cursor
     * @param childTape   child confirmed pivots at the cursor, ordered
     * @param series      series the rules read
     * @return deterministic edges and bookkeeping
     * @throws IllegalArgumentException when the scales are the same or reversed
     */
    Result extract(final ScaleRelation.Scale parentScale, final ScaleRelation.Scale childScale,
            final List<Parent> parents, final List<ConfirmedPivot> childTape, final BarSeries series) {
        Objects.requireNonNull(parentScale, "parentScale");
        Objects.requireNonNull(childScale, "childScale");
        if (parentScale.name().equals(childScale.name())) {
            throw new IllegalArgumentException("same-scale link rejected: " + parentScale.name());
        }
        if (childScale.rank() <= parentScale.rank()) {
            throw new IllegalArgumentException("reversed link rejected: " + parentScale.name() + " -> "
                    + childScale.name() + " must run from a coarser to a finer declared scale");
        }
        final List<ScaleRelation.Edge> edges = new ArrayList<>();
        int legs = 0;
        int truncatedLegs = 0;
        final List<Parent> ordered = parents.stream()
                .sorted(Comparator.comparingInt((Parent parent) -> parent.candidate().startBarIndex())
                        .thenComparing(Parent::key))
                .toList();
        for (final Parent parent : ordered) {
            if (parent.candidate().grammar() != policy.parentGrammar()) {
                continue;
            }
            for (int leg = 0; leg < parent.candidate().grammar().legCount(); leg++) {
                legs++;
                if (relateLeg(parentScale, childScale, parent, leg, childTape, series, edges)) {
                    truncatedLegs++;
                }
            }
        }
        return new Result(edges, legs, truncatedLegs);
    }

    /** @return whether the leg's decomposition search was truncated */
    private boolean relateLeg(final ScaleRelation.Scale parentScale, final ScaleRelation.Scale childScale,
            final Parent parent, final int leg, final List<ConfirmedPivot> tape, final BarSeries series,
            final List<ScaleRelation.Edge> out) {
        final TopologyCandidate candidate = parent.candidate();
        final ConfirmedPivot a = candidate.pivots().get(leg);
        final ConfirmedPivot b = candidate.pivots().get(leg + 1);
        final LegContext context = new LegContext(parentScale, childScale, parent, leg, a, b,
                anchorWitnesses(tape, a, b));

        final int aPosition = positionOf(tape, a.pivotIndex());
        final int bPosition = positionOf(tape, b.pivotIndex());
        final ConfirmedPivot atA = aPosition >= 0 ? tape.get(aPosition) : null;
        final ConfirmedPivot atB = bPosition >= 0 ? tape.get(bPosition) : null;
        final boolean startExact = atA != null && sameAnchor(atA, a);
        final boolean endExact = atB != null && sameAnchor(atB, b);
        final List<ConfirmedPivot> inside = interior(tape, a.pivotIndex(), b.pivotIndex());

        final List<ScaleRelation.Predicate> anchorPredicates = new ArrayList<>();
        anchorPredicates.add(anchorPredicate("start-anchor", a, atA, startExact));
        final boolean tapeReachedEnd = !tape.isEmpty() && tape.get(tape.size() - 1).pivotIndex() >= b.pivotIndex();
        if (endExact || atB != null || tapeReachedEnd) {
            anchorPredicates.add(anchorPredicate("end-anchor", b, atB, endExact));
        } else {
            anchorPredicates.add(new ScaleRelation.Predicate("end-anchor", EvidenceState.PENDING,
                    "child tape ends at bar " + (tape.isEmpty() ? "none" : tape.get(tape.size() - 1).pivotIndex())
                            + " before parent end anchor " + describe(b)));
        }

        if ((atA != null && !startExact) || (atB != null && !endExact)) {
            out.add(context.edge(ScaleRelation.State.NOT_NESTED, null, inside, anchorPredicates, null, null, List.of(),
                    tape));
            return false;
        }
        final List<TopologyGrammar> grammars = policy.childGrammars().forLeg(candidate.grammar(), leg);
        if (grammars.isEmpty()) {
            if (!inside.isEmpty()) {
                final List<ScaleRelation.Predicate> predicates = new ArrayList<>(anchorPredicates);
                predicates.add(new ScaleRelation.Predicate("child-grammar", EvidenceState.NOT_APPLICABLE,
                        "no child grammar is declared for parent leg " + (leg + 1)));
                out.add(context.edge(ScaleRelation.State.CONTAINED_ONLY, null, inside, predicates, null, null,
                        List.of(), tape));
            }
            return false;
        }
        if (!startExact) {
            if (!inside.isEmpty()) {
                out.add(context.edge(ScaleRelation.State.CONTAINED_ONLY, grammars.get(0), inside, anchorPredicates,
                        null, null, List.of(), tape));
            }
            return false;
        }
        if (!endExact) {
            if (tapeReachedEnd) {
                // The child tape moved past the parent end without a pivot on
                // it: the fine scale does not share that anchor. Never snap.
                out.add(context.edge(ScaleRelation.State.NOT_NESTED, grammars.get(0), inside, anchorPredicates, null,
                        null, List.of(), tape));
                return false;
            }
            for (final TopologyGrammar grammar : grammars) {
                out.add(pending(context, grammar, a, inside, anchorPredicates, tape));
            }
            return false;
        }
        boolean truncated = false;
        for (final TopologyGrammar grammar : grammars) {
            final Decompositions found = decompositions(grammar, atA, inside, atB);
            truncated |= found.truncated();
            if (found.candidates().isEmpty() && found.truncated()) {
                // The bounded search stopped before it could examine every
                // sequence, so "no valid sequence" has not been established.
                final List<ScaleRelation.Predicate> predicates = new ArrayList<>(anchorPredicates);
                predicates.add(new ScaleRelation.Predicate("interior-shape", EvidenceState.UNAVAILABLE,
                        "bounded " + grammar + " search over " + inside.size()
                                + " interior pivots stopped at its budget before finding a sequence"));
                out.add(context.edge(ScaleRelation.State.CONTAINED_ONLY, grammar, inside, predicates, null, null,
                        List.of(), tape));
                continue;
            }
            if (found.candidates().isEmpty()) {
                final List<ScaleRelation.Predicate> predicates = new ArrayList<>(anchorPredicates);
                predicates.add(new ScaleRelation.Predicate("interior-shape", EvidenceState.FAIL,
                        "no " + policy.interior() + " ordered " + grammar + " sequence from " + describe(a) + " to "
                                + describe(b) + " among " + inside.size() + " interior pivots"));
                final ScaleRelation.State state = inside.isEmpty() ? ScaleRelation.State.NOT_NESTED
                        : ScaleRelation.State.CONFLICTING_EVIDENCE;
                out.add(context.edge(state, grammar, inside, predicates, null, null, List.of(), tape));
                continue;
            }
            for (final TopologyCandidate child : found.candidates()) {
                out.add(decomposed(context, grammar, child, anchorPredicates, series, tape));
            }
        }
        return truncated;
    }

    /**
     * The child pivots the anchor and absence predicates read: whatever sits on
     * either parent anchor, plus the first pivot past the parent end that proves a
     * missing end anchor.
     */
    private static List<ConfirmedPivot> anchorWitnesses(final List<ConfirmedPivot> tape, final ConfirmedPivot a,
            final ConfirmedPivot b) {
        final List<ConfirmedPivot> witnesses = new ArrayList<>(3);
        final int aPosition = positionOf(tape, a.pivotIndex());
        if (aPosition >= 0) {
            witnesses.add(tape.get(aPosition));
        }
        final int bPosition = positionOf(tape, b.pivotIndex());
        if (bPosition >= 0) {
            witnesses.add(tape.get(bPosition));
        } else {
            for (final ConfirmedPivot pivot : tape) {
                if (pivot.pivotIndex() > b.pivotIndex()) {
                    witnesses.add(pivot);
                    break;
                }
            }
        }
        return witnesses;
    }

    private ScaleRelation.Edge pending(final LegContext context, final TopologyGrammar grammar, final ConfirmedPivot a,
            final List<ConfirmedPivot> inside, final List<ScaleRelation.Predicate> anchorPredicates,
            final List<ConfirmedPivot> tape) {
        final List<ScaleRelation.Predicate> predicates = new ArrayList<>(anchorPredicates);
        final boolean contiguousPrefixBroken;
        if (policy.interior() == ScaleRelation.Interior.CONTIGUOUS && !inside.isEmpty()) {
            final List<ConfirmedPivot> prefix = new ArrayList<>(inside.size() + 1);
            prefix.add(a);
            prefix.addAll(inside);
            contiguousPrefixBroken = !anyDirectionMatchesPrefix(grammar, prefix);
        } else {
            contiguousPrefixBroken = false;
        }
        if (contiguousPrefixBroken) {
            predicates.add(new ScaleRelation.Predicate("interior-shape", EvidenceState.FAIL,
                    "interior pivots already contradict an ordered " + grammar + " prefix"));
            return context.edge(ScaleRelation.State.CONFLICTING_EVIDENCE, grammar, inside, predicates, null, null,
                    List.of(), tape);
        }
        predicates.add(new ScaleRelation.Predicate("interior-shape", EvidenceState.PENDING,
                inside.size() + " interior pivots confirmed; " + grammar + " is not yet decidable"));
        return context.edge(ScaleRelation.State.PENDING_CONFIRMATION, grammar, inside, predicates, null, null,
                List.of(), tape);
    }

    private ScaleRelation.Edge decomposed(final LegContext context, final TopologyGrammar grammar,
            final TopologyCandidate child, final List<ScaleRelation.Predicate> anchorPredicates, final BarSeries series,
            final List<ConfirmedPivot> tape) {
        final List<ScaleRelation.Predicate> predicates = new ArrayList<>(anchorPredicates);
        predicates.add(new ScaleRelation.Predicate("interior-shape", EvidenceState.PASS,
                grammar + " " + child.direction() + " over child bars " + child.startBarIndex() + "-"
                        + child.endBarIndex() + " with " + (child.pivots().size() - 2) + " interior pivots ("
                        + policy.interior() + ")"));
        final String childKey = identity.key(child);
        final List<RuleEvidence> evidence = evidenceCache.get(childKey);
        final List<RuleEvidence> evaluated = evidence != null ? evidence : evaluateRules(child, series);
        if (evidence == null) {
            evidenceCache.put(childKey, evaluated);
        }
        boolean fail = false;
        boolean pending = false;
        boolean unavailable = false;
        for (final RuleEvidence result : evaluated) {
            predicates
                    .add(new ScaleRelation.Predicate("rule:" + result.ruleId(), result.state(), result.explanation()));
            fail |= result.state() == EvidenceState.FAIL;
            pending |= result.state() == EvidenceState.PENDING;
            unavailable |= result.state() == EvidenceState.UNAVAILABLE;
        }
        final ScaleRelation.State state;
        if (fail) {
            state = ScaleRelation.State.CONFLICTING_EVIDENCE;
        } else if (pending) {
            state = ScaleRelation.State.PENDING_CONFIRMATION;
        } else if (unavailable) {
            state = ScaleRelation.State.CONTAINED_ONLY;
        } else {
            state = ScaleRelation.State.SUBDIVISION_SUPPORTED;
        }
        return context.edge(state, grammar, child.pivots().subList(1, child.pivots().size() - 1), predicates, childKey,
                identity.version(child, evaluated), child.pivots(), tape);
    }

    private List<RuleEvidence> evaluateRules(final TopologyCandidate child, final BarSeries series) {
        final List<RuleEvidence> evidence = new ArrayList<>(childRules.size());
        for (final RelationshipRule rule : childRules) {
            final RuleEvidence result = rule.evaluate(child, series);
            if (!rule.id().equals(result.ruleId())) {
                throw new IllegalArgumentException(
                        "rule evidence id mismatch: rule " + rule.id() + " returned evidence for " + result.ruleId());
            }
            evidence.add(result);
        }
        return List.copyOf(evidence);
    }

    private record Decompositions(List<TopologyCandidate> candidates, boolean truncated) {
    }

    private Decompositions decompositions(final TopologyGrammar grammar, final ConfirmedPivot a,
            final List<ConfirmedPivot> inside, final ConfirmedPivot b) {
        final int interiorCount = grammar.requiredPivots() - 2;
        final List<TopologyCandidate> found = new ArrayList<>();
        if (policy.interior() == ScaleRelation.Interior.CONTIGUOUS) {
            if (inside.size() == interiorCount) {
                final List<ConfirmedPivot> sequence = new ArrayList<>(grammar.requiredPivots());
                sequence.add(a);
                sequence.addAll(inside);
                sequence.add(b);
                final TopologyCandidate candidate = shaped(grammar, sequence);
                if (candidate != null) {
                    found.add(candidate);
                }
            }
            return new Decompositions(found, false);
        }
        if (inside.size() < interiorCount) {
            return new Decompositions(found, false);
        }
        final Search search = new Search(grammar, a, b, inside, interiorCount);
        search.run(0, new ArrayList<>());
        return new Decompositions(search.found, search.truncated);
    }

    /** Bounded ordered-subsequence search over the interior pivots. */
    private final class Search {
        private final TopologyGrammar grammar;
        private final ConfirmedPivot a;
        private final ConfirmedPivot b;
        private final List<ConfirmedPivot> inside;
        private final int interiorCount;
        private final List<TopologyCandidate> found = new ArrayList<>();
        private int nodes;
        private boolean truncated;

        Search(final TopologyGrammar grammar, final ConfirmedPivot a, final ConfirmedPivot b,
                final List<ConfirmedPivot> inside, final int interiorCount) {
            this.grammar = grammar;
            this.a = a;
            this.b = b;
            this.inside = inside;
            this.interiorCount = interiorCount;
        }

        void run(final int from, final List<ConfirmedPivot> chosen) {
            if (truncated) {
                return;
            }
            if (++nodes > policy.nodeBudgetPerLeg()) {
                truncated = true;
                return;
            }
            if (chosen.size() == interiorCount) {
                if (chosen.get(chosen.size() - 1).type() == b.type()) {
                    return;
                }
                final List<ConfirmedPivot> sequence = new ArrayList<>(grammar.requiredPivots());
                sequence.add(a);
                sequence.addAll(chosen);
                sequence.add(b);
                final TopologyCandidate candidate = shaped(grammar, sequence);
                if (candidate != null) {
                    if (found.size() == policy.maxDecompositionsPerLeg()) {
                        truncated = true;
                        return;
                    }
                    found.add(candidate);
                }
                return;
            }
            final ConfirmedPivot previous = chosen.isEmpty() ? a : chosen.get(chosen.size() - 1);
            final int remaining = interiorCount - chosen.size();
            for (int i = from; i + remaining <= inside.size(); i++) {
                final ConfirmedPivot next = inside.get(i);
                if (next.type() == previous.type()) {
                    continue;
                }
                chosen.add(next);
                run(i + 1, chosen);
                chosen.remove(chosen.size() - 1);
                if (truncated) {
                    return;
                }
            }
        }
    }

    private TopologyCandidate shaped(final TopologyGrammar grammar, final List<ConfirmedPivot> sequence) {
        for (final WaveDirection direction : WaveDirection.values()) {
            final TopologyCandidate candidate = analyzer.shapedCandidate(grammar, direction, sequence);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** Probes whether a pivot prefix is consistent with a grammar prefix. */
    private boolean anyDirectionMatchesPrefix(final TopologyGrammar grammar, final List<ConfirmedPivot> prefix) {
        for (final WaveDirection direction : WaveDirection.values()) {
            if (analyzer.matchesPartialShape(grammar, direction, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameAnchor(final ConfirmedPivot child, final ConfirmedPivot parent) {
        return child.pivotIndex() == parent.pivotIndex() && child.type() == parent.type()
                && child.price().compareTo(parent.price()) == 0;
    }

    private static ScaleRelation.Predicate anchorPredicate(final String id, final ConfirmedPivot parentAnchor,
            final ConfirmedPivot childPivot, final boolean exact) {
        if (exact) {
            return new ScaleRelation.Predicate(id, EvidenceState.PASS,
                    "child pivot matches parent anchor " + describe(parentAnchor));
        }
        if (childPivot == null) {
            return new ScaleRelation.Predicate(id, EvidenceState.FAIL,
                    "child tape has no pivot at parent anchor " + describe(parentAnchor));
        }
        return new ScaleRelation.Predicate(id, EvidenceState.FAIL,
                "child pivot " + describe(childPivot) + " differs from parent anchor " + describe(parentAnchor));
    }

    private static String describe(final ConfirmedPivot pivot) {
        return pivot.pivotIndex() + ":" + pivot.type() + ":" + pivot.price();
    }

    /** Position of the tape pivot at an exact bar index, or -1. */
    private static int positionOf(final List<ConfirmedPivot> tape, final int pivotIndex) {
        int low = 0;
        int high = tape.size() - 1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final int value = tape.get(mid).pivotIndex();
            if (value == pivotIndex) {
                return mid;
            }
            if (value < pivotIndex) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    /** Tape pivots strictly between two bar indices, via interval lookup. */
    private static List<ConfirmedPivot> interior(final List<ConfirmedPivot> tape, final int fromIndex,
            final int toIndex) {
        int low = 0;
        int high = tape.size();
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (tape.get(mid).pivotIndex() <= fromIndex) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        int end = low;
        while (end < tape.size() && tape.get(end).pivotIndex() < toIndex) {
            end++;
        }
        return tape.subList(low, end);
    }

    private final class LegContext {
        private final ScaleRelation.Scale parentScale;
        private final ScaleRelation.Scale childScale;
        private final Parent parent;
        private final int leg;
        private final ConfirmedPivot start;
        private final ConfirmedPivot end;
        private final List<ConfirmedPivot> witnesses;

        LegContext(final ScaleRelation.Scale parentScale, final ScaleRelation.Scale childScale, final Parent parent,
                final int leg, final ConfirmedPivot start, final ConfirmedPivot end,
                final List<ConfirmedPivot> witnesses) {
            this.parentScale = parentScale;
            this.childScale = childScale;
            this.parent = parent;
            this.leg = leg;
            this.start = start;
            this.end = end;
            this.witnesses = witnesses;
        }

        /**
         * Builds an edge.
         *
         * @param state        relation state
         * @param grammar      tested child grammar, or {@code null}
         * @param used         child pivots the edge relies on besides the anchor
         *                     witnesses
         * @param predicates   tested predicates
         * @param childKey     child candidate key, or {@code null}
         * @param childVersion child candidate version, or {@code null}
         * @param fullSequence complete child sequence when one exists, else empty
         * @param tape         child tape at the cursor (for availability only)
         */
        ScaleRelation.Edge edge(final ScaleRelation.State state, final TopologyGrammar grammar,
                final List<ConfirmedPivot> used, final List<ScaleRelation.Predicate> predicates, final String childKey,
                final String childVersion, final List<ConfirmedPivot> fullSequence, final List<ConfirmedPivot> tape) {
            final List<ConfirmedPivot> evidence;
            if (fullSequence.isEmpty()) {
                final TreeMap<Integer, ConfirmedPivot> byIndex = new TreeMap<>();
                for (final ConfirmedPivot pivot : used) {
                    byIndex.put(pivot.pivotIndex(), pivot);
                }
                for (final ConfirmedPivot pivot : witnesses) {
                    byIndex.putIfAbsent(pivot.pivotIndex(), pivot);
                }
                evidence = List.copyOf(byIndex.values());
            } else {
                evidence = fullSequence;
            }
            final int count = evidence.size();
            final List<ConfirmedPivot> stored = count > ScaleRelation.MAX_STORED_CHILD_PIVOTS
                    ? evidence.subList(0, ScaleRelation.MAX_STORED_CHILD_PIVOTS)
                    : evidence;
            int availableAt = 0;
            for (final ConfirmedPivot pivot : parent.candidate().pivots()) {
                availableAt = Math.max(availableAt, pivot.confirmationIndex());
            }
            for (final ConfirmedPivot pivot : evidence) {
                availableAt = Math.max(availableAt, pivot.confirmationIndex());
            }
            final StringBuilder identityText = new StringBuilder();
            identityText.append(parent.key())
                    .append('|')
                    .append(leg)
                    .append('|')
                    .append(parentScale.name())
                    .append('>')
                    .append(childScale.name())
                    .append('|')
                    .append(grammar == null ? "-" : grammar.name());
            for (final ConfirmedPivot pivot : fullSequence) {
                identityText.append('|').append(describe(pivot));
            }
            final String key = ScaleRelation.digest(identityText.toString());
            final StringBuilder versionText = new StringBuilder();
            versionText.append(state.label()).append('|').append(parent.version()).append('|').append(availableAt);
            for (final ScaleRelation.Predicate predicate : predicates) {
                versionText.append('|')
                        .append(predicate.id())
                        .append(':')
                        .append(predicate.state())
                        .append(':')
                        .append(predicate.detail());
            }
            final String version = key + "@" + ScaleRelation.digest(versionText.toString());
            return new ScaleRelation.Edge(key, version, parentScale.name(), childScale.name(), parent.key(),
                    parent.version(), parent.candidate().grammar(), parent.candidate().direction(), leg, start, end,
                    state, grammar, childKey, childVersion, stored, count, predicates, availableAt);
        }
    }
}
