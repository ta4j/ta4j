/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;

/**
 * Vocabulary of the experimental, causal parent/child scale relation research
 * surface.
 *
 * <p>
 * A relation states how the finer of two <em>explicitly declared</em> scales
 * relates to one leg of a candidate on the coarser scale, using only evidence
 * that was confirmed at the as-of cursor. Containment, subdivision support,
 * conflict, and non-nesting are distinct findings: a compatible direction, a
 * degree label, or a shared interval is never subdivision evidence. Records are
 * immutable; a changed relation is a new {@link Edge#version() version} and a
 * new {@link Event}, never a rewrite.
 *
 * <p>
 * Everything here is package-private research machinery; it is not part of the
 * released {@code org.ta4j.core.indicators.elliott} degree surface and does not
 * touch it.
 */
final class ScaleRelation {

    /** Maximum scales one study may relate. */
    static final int MAX_SCALES = 3;

    /** Default retained-edge cap per observation. */
    static final int DEFAULT_EDGE_CAP = 256;

    /** Default decompositions retained per parent leg and child grammar. */
    static final int DEFAULT_MAX_DECOMPOSITIONS_PER_LEG = 8;

    /** Default search-node budget per parent leg and child grammar. */
    static final int DEFAULT_NODE_BUDGET_PER_LEG = 20_000;

    /** Most child pivots one edge stores verbatim; the full count is kept. */
    static final int MAX_STORED_CHILD_PIVOTS = 64;

    private ScaleRelation() {
    }

    /** How a parent leg relates to the child scale at one as-of cursor. */
    enum State {
        /** Child pivots lie inside the leg but no declared subdivision was tested. */
        CONTAINED_ONLY("contained-only"),
        /** Anchors and prefix are consistent but the last child confirmation is due. */
        PENDING_CONFIRMATION("pending-confirmation"),
        /** An exact, contiguous, rule-satisfying child decomposition exists. */
        SUBDIVISION_SUPPORTED("subdivision-supported"),
        /** Child structure exists inside the leg and contradicts the declared grammar. */
        CONFLICTING_EVIDENCE("conflicting-evidence"),
        /** The child tape has no matching exact anchor or no interior structure. */
        NOT_NESTED("not-nested");

        private final String label;

        State(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static State fromLabel(final String label) {
            for (final State state : values()) {
                if (state.label.equals(label)) {
                    return state;
                }
            }
            throw new IllegalArgumentException("unknown relation state: " + label);
        }
    }

    /** Whether child interior anchors must be exactly the pivots between anchors. */
    enum Interior {
        /** The child tape between the anchors is exactly the grammar interior. */
        CONTIGUOUS,
        /** Extra child pivots between the anchors may be skipped (finer sub-waves). */
        ALLOW_SKIPPED
    }

    /** Whether an edge version became or stopped being the active view. */
    enum Lifecycle {
        ACTIVE, ENDED
    }

    /** Why an edge version appeared or stopped being active. */
    enum Reason {
        /** First observation of the edge. */
        OBSERVED,
        /** Same edge identity, new state or evidence. */
        REVISED,
        /** Replaced by a newer state of the same parent leg and child evidence. */
        SUPERSEDED,
        /** The parent candidate was replaced by a revised one at the same origin. */
        PARENT_REVISED,
        /** The parent topology was explicitly invalidated. */
        PARENT_INVALIDATED,
        /** The parent candidate is no longer reported without an invalidation. */
        PARENT_RETIRED,
        /** A child pivot the edge relied on was withdrawn or revised. */
        CHILD_WITHDRAWN,
        /** The edge was dropped by the retained-edge cap, not by evidence. */
        CAP_RETIRED
    }

    /** One rule-like test of a relation, with an explicit evidence state. */
    record Predicate(String id, EvidenceState state, String detail) {
        Predicate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** A declared scale; {@code rank} is its position in the declared order. */
    record Scale(String name, int rank) {
        Scale {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("scale name must not be blank");
            }
            if (rank < 0) {
                throw new IllegalArgumentException("scale rank must not be negative");
            }
        }
    }

    /** A declared parent to child link between two scales. */
    record Link(String parent, String child) {
        Link {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(child, "child");
        }
    }

    /** Supplies the child grammars tried for one parent grammar and leg. */
    @FunctionalInterface
    interface ChildGrammars {
        List<TopologyGrammar> forLeg(TopologyGrammar parent, int leg);
    }

    /**
     * Extraction policy.
     *
     * @param parentGrammar              grammar whose candidates act as parents
     * @param interior                   interior-anchor policy
     * @param edgeCap                    retained edges per observation
     * @param maxDecompositionsPerLeg    decompositions kept per leg and child
     *                                   grammar
     * @param nodeBudgetPerLeg           search nodes per leg and child grammar
     * @param childGrammars              child grammars declared per parent leg
     */
    record Policy(TopologyGrammar parentGrammar, Interior interior, int edgeCap, int maxDecompositionsPerLeg,
            int nodeBudgetPerLeg, ChildGrammars childGrammars) {
        Policy {
            Objects.requireNonNull(parentGrammar, "parentGrammar");
            Objects.requireNonNull(interior, "interior");
            Objects.requireNonNull(childGrammars, "childGrammars");
            if (edgeCap < 1) {
                throw new IllegalArgumentException("edgeCap must be positive");
            }
            if (maxDecompositionsPerLeg < 1) {
                throw new IllegalArgumentException("maxDecompositionsPerLeg must be positive");
            }
            if (nodeBudgetPerLeg < 1) {
                throw new IllegalArgumentException("nodeBudgetPerLeg must be positive");
            }
        }

        /**
         * Default declaration: impulse legs (1, 3, 5) are tried as five-wave
         * structures and counter legs (2, 4) as three-wave corrections.
         *
         * @return the default policy
         */
        static Policy defaults() {
            return new Policy(TopologyGrammar.MOTIVE_5, Interior.CONTIGUOUS, DEFAULT_EDGE_CAP,
                    DEFAULT_MAX_DECOMPOSITIONS_PER_LEG, DEFAULT_NODE_BUDGET_PER_LEG,
                    ScaleRelation::defaultChildGrammars);
        }

        Policy withInterior(final Interior newInterior) {
            return new Policy(parentGrammar, newInterior, edgeCap, maxDecompositionsPerLeg, nodeBudgetPerLeg,
                    childGrammars);
        }

        Policy withEdgeCap(final int newCap) {
            return new Policy(parentGrammar, interior, newCap, maxDecompositionsPerLeg, nodeBudgetPerLeg,
                    childGrammars);
        }

        Policy withChildGrammars(final ChildGrammars newChildGrammars) {
            return new Policy(parentGrammar, interior, edgeCap, maxDecompositionsPerLeg, nodeBudgetPerLeg,
                    newChildGrammars);
        }

        Policy withDecompositionBounds(final int maxDecompositions, final int nodeBudget) {
            return new Policy(parentGrammar, interior, edgeCap, maxDecompositions, nodeBudget, childGrammars);
        }
    }

    static List<TopologyGrammar> defaultChildGrammars(final TopologyGrammar parent, final int leg) {
        if (parent != TopologyGrammar.MOTIVE_5) {
            return List.of();
        }
        return switch (leg) {
        case 0, 2, 4 -> List.of(TopologyGrammar.MOTIVE_5);
        case 1, 3 -> List.of(TopologyGrammar.CORRECTIVE_3);
        default -> List.of();
        };
    }

    /**
     * One relation between a parent candidate leg and the child scale at an as-of
     * cursor.
     *
     * @param key                identity of parent leg, child scale and the child
     *                           evidence used; independent of state
     * @param version            {@code key@digest} of state, predicates, parent
     *                           version and availability
     * @param parentScale        parent scale name
     * @param childScale         child scale name
     * @param parentCandidateKey parent candidate identity
     * @param parentVersion      parent candidate version
     * @param parentGrammar      parent grammar
     * @param parentDirection    parent direction
     * @param parentLeg          zero-based parent leg
     * @param parentStart        exact parent leg start anchor
     * @param parentEnd          exact parent leg end anchor
     * @param state              relation state
     * @param childGrammar       tested child grammar, or {@code null}
     * @param childCandidateKey  child candidate identity when a complete child
     *                           decomposition exists, else {@code null}
     * @param childVersion       child candidate version, or {@code null}
     * @param childPivots        child pivots the edge relies on (anchors included
     *                           when exact); at most
     *                           {@link ScaleRelation#MAX_STORED_CHILD_PIVOTS}
     * @param childPivotCount    number of child pivots the edge relies on
     * @param predicates         tested predicates in declaration order
     * @param availableAt        maximum confirmation index of all participating
     *                           parent and child evidence
     */
    record Edge(String key, String version, String parentScale, String childScale, String parentCandidateKey,
            String parentVersion, TopologyGrammar parentGrammar, WaveDirection parentDirection, int parentLeg,
            ConfirmedPivot parentStart, ConfirmedPivot parentEnd, State state, TopologyGrammar childGrammar,
            String childCandidateKey, String childVersion, List<ConfirmedPivot> childPivots, int childPivotCount,
            List<Predicate> predicates, int availableAt) {
        Edge {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(parentScale, "parentScale");
            Objects.requireNonNull(childScale, "childScale");
            Objects.requireNonNull(parentCandidateKey, "parentCandidateKey");
            Objects.requireNonNull(parentVersion, "parentVersion");
            Objects.requireNonNull(parentGrammar, "parentGrammar");
            Objects.requireNonNull(parentDirection, "parentDirection");
            Objects.requireNonNull(parentStart, "parentStart");
            Objects.requireNonNull(parentEnd, "parentEnd");
            Objects.requireNonNull(state, "state");
            childPivots = List.copyOf(childPivots);
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * Exact bookkeeping of one observation's extraction.
     *
     * @param parentCandidates          parent candidates considered
     * @param legsChecked               parent legs examined
     * @param edgesGenerated            edges produced before the cap
     * @param edgesRetained             edges kept
     * @param edgesOmitted              edges dropped by the cap
     * @param edgeCap                   configured cap
     * @param decompositionLegsTruncated legs whose decomposition search hit the
     *                                  per-leg bound or node budget
     */
    record Coverage(int parentCandidates, int legsChecked, int edgesGenerated, int edgesRetained, int edgesOmitted,
            int edgeCap, int decompositionLegsTruncated) {
        Coverage {
            if (edgesRetained + edgesOmitted != edgesGenerated) {
                throw new IllegalArgumentException("retained + omitted must equal generated");
            }
        }

        boolean truncated() {
            return edgesOmitted > 0;
        }

        /** @return whether any bound kept the search from checking everything */
        boolean incomplete() {
            return edgesOmitted > 0 || decompositionLegsTruncated > 0;
        }
    }

    /** One immutable change in the active relation set. */
    record Event(int asOfIndex, Lifecycle lifecycle, Reason reason, Edge edge) {
        Event {
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(edge, "edge");
        }
    }

    /**
     * Validates a declared scale chain before any study runs.
     *
     * @param scales ordered scales, parent first
     * @return the same list
     * @throws IllegalArgumentException for fewer than two or more than three
     *                                  scales, or blank/duplicate names
     */
    static List<Scale> validateScales(final List<Scale> scales) {
        Objects.requireNonNull(scales, "scales");
        if (scales.size() < 2 || scales.size() > MAX_SCALES) {
            throw new IllegalArgumentException(
                    "hierarchy needs 2 to " + MAX_SCALES + " scales but got " + scales.size());
        }
        final Set<String> names = new HashSet<>();
        for (int i = 0; i < scales.size(); i++) {
            final Scale scale = scales.get(i);
            if (scale.rank() != i) {
                throw new IllegalArgumentException("scale " + scale.name() + " rank " + scale.rank()
                        + " does not match its declared position " + i);
            }
            if (!names.add(scale.name())) {
                throw new IllegalArgumentException("duplicate scale: " + scale.name());
            }
        }
        return List.copyOf(scales);
    }

    /**
     * Validates explicit links against a declared scale chain.
     *
     * <p>
     * Checks run in a fixed order so the same input always fails the same way:
     * self links, unknown scales, cycles, reversed links, then non-adjacent links.
     *
     * @param scales validated scale chain
     * @param links  links to check
     * @throws IllegalArgumentException describing the first violation
     */
    static void validateLinks(final List<Scale> scales, final List<Link> links) {
        final Map<String, Scale> byName = new HashMap<>();
        scales.forEach(scale -> byName.put(scale.name(), scale));
        for (final Link link : links) {
            if (link.parent().equals(link.child())) {
                throw new IllegalArgumentException("self link rejected: " + link.parent());
            }
            if (!byName.containsKey(link.parent()) || !byName.containsKey(link.child())) {
                throw new IllegalArgumentException(
                        "link references an undeclared scale: " + link.parent() + " -> " + link.child());
            }
        }
        final Map<String, List<String>> children = new HashMap<>();
        for (final Link link : links) {
            children.computeIfAbsent(link.parent(), key -> new ArrayList<>()).add(link.child());
        }
        final Set<String> done = new HashSet<>();
        for (final Scale scale : scales) {
            detectCycle(scale.name(), children, new HashSet<>(), done);
        }
        for (final Link link : links) {
            final int parentRank = byName.get(link.parent()).rank();
            final int childRank = byName.get(link.child()).rank();
            if (childRank < parentRank) {
                throw new IllegalArgumentException("reversed link rejected: " + link.parent() + " -> " + link.child()
                        + " runs from a finer to a coarser declared scale");
            }
            if (childRank != parentRank + 1) {
                throw new IllegalArgumentException("non-adjacent link rejected: " + link.parent() + " -> "
                        + link.child() + " (only adjacent declared scales are related)");
            }
        }
    }

    private static void detectCycle(final String node, final Map<String, List<String>> children,
            final Set<String> path, final Set<String> done) {
        if (done.contains(node)) {
            return;
        }
        if (!path.add(node)) {
            throw new IllegalArgumentException("cyclic link rejected at scale " + node);
        }
        for (final String child : children.getOrDefault(node, List.of())) {
            detectCycle(child, children, path, done);
        }
        path.remove(node);
        done.add(node);
    }

    /** @return the adjacent links implied by a declared chain */
    static List<Link> adjacentLinks(final List<Scale> scales) {
        final List<Link> links = new ArrayList<>();
        for (int i = 0; i + 1 < scales.size(); i++) {
            links.add(new Link(scales.get(i).name(), scales.get(i + 1).name()));
        }
        return links;
    }

    /** First 128 bits of the text's SHA-256, as lowercase hex. */
    static String digest(final String text) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
