/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Experimental corrective-family profiles judged over explicit parent/child
 * subdivision evidence.
 *
 * <p>
 * A profile names one strict research hypothesis (zigzag 5-3-5, regular or
 * expanded flat 3-3-5, contracting triangle 3-3-3-3-3), never a general
 * Elliott law. A {@link Verdict} combines two independent kinds of evidence for
 * one parent candidate: the <em>envelope</em> predicates over the parent's
 * normalized prices, and the <em>subdivision</em> evidence of each parent leg
 * taken from the causal {@link ScaleRelation.Edge relations} of the child
 * scale. A parent whose outer shape fits the envelope but whose child
 * subdivision is not proven is only {@link Status#SHAPE_COMPATIBLE}; a shape
 * excluded by a profile is {@link Status#OUTSIDE_PROFILE}, which is a statement
 * about that profile and nothing else. Evaluation is a pure function of its
 * inputs: it never searches, skips pivots, substitutes another family, or
 * reads anything beyond the supplied parent pivots and edges.
 *
 * <p>
 * Package-private research machinery; it reuses the released
 * {@link ScenarioType} identities without changing their meaning.
 *
 * @since 0.26.1
 */
final class CorrectiveFamily {

    /** Version of the profile definitions; bump when a predicate changes. */
    static final int PROFILE_REVISION = 1;

    /** Default most child compositions one verdict lists. */
    static final int DEFAULT_MAX_COMPOSITIONS = 8;

    private static final String REGULAR_FLAT_MIN_RETRACEMENT = "0.9";
    private static final String REGULAR_FLAT_MAX_OVERSHOOT = "0.1";

    private CorrectiveFamily() {
    }

    /**
     * A bounded, independently selectable corrective-family profile.
     *
     * @since 0.26.1
     */
    enum Profile {
        /** A/B/C with 5-3-5 children; B retraces part of A, C passes A's end. */
        ZIGZAG("zigzag", ScenarioType.CORRECTIVE_ZIGZAG, TopologyGrammar.CORRECTIVE_3,
                List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.MOTIVE_5)),
        /** A/B/C with 3-3-5 children; B returns near A's origin, C barely passes A. */
        REGULAR_FLAT("regular-flat", ScenarioType.CORRECTIVE_FLAT, TopologyGrammar.CORRECTIVE_3,
                List.of(TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.MOTIVE_5)),
        /** A/B/C with 3-3-5 children; B passes A's origin and C passes A's end. */
        EXPANDED_FLAT("expanded-flat", ScenarioType.CORRECTIVE_FLAT, TopologyGrammar.CORRECTIVE_3,
                List.of(TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.MOTIVE_5)),
        /**
         * A/B/C/D/E with 3-3-3-3-3 children inside a strictly contracting
         * envelope; the five legs are carried by a six-pivot container.
         */
        CONTRACTING_TRIANGLE("contracting-triangle", ScenarioType.CORRECTIVE_TRIANGLE, TopologyGrammar.MOTIVE_5,
                List.of(TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CORRECTIVE_3,
                        TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CORRECTIVE_3));

        private final String id;
        private final ScenarioType scenarioType;
        private final TopologyGrammar parentGrammar;
        private final List<TopologyGrammar> childGrammars;

        Profile(final String id, final ScenarioType scenarioType, final TopologyGrammar parentGrammar,
                final List<TopologyGrammar> childGrammars) {
            this.id = id;
            this.scenarioType = scenarioType;
            this.parentGrammar = parentGrammar;
            this.childGrammars = childGrammars;
        }

        /** @return stable profile identifier used in recipes and artifacts */
        String id() {
            return id;
        }

        /** @return the released taxonomy identity this profile narrows */
        ScenarioType scenarioType() {
            return scenarioType;
        }

        /**
         * @return grammar whose candidates act as parents; the triangle uses a
         *         six-pivot container because its shape is checked by the envelope
         *         predicates, not by the container grammar
         */
        TopologyGrammar parentGrammar() {
            return parentGrammar;
        }

        /** @return the child grammar each parent leg must subdivide into */
        List<TopologyGrammar> childGrammars() {
            return childGrammars;
        }

        /** @return the child grammars as a compact label such as {@code 5-3-5} */
        String subdivision() {
            final List<String> counts = new ArrayList<>();
            for (final TopologyGrammar grammar : childGrammars) {
                counts.add(Integer.toString(grammar.legCount()));
            }
            return String.join("-", counts);
        }

        /** @return whether the profile carries recipe-declared tolerance bands */
        boolean hasTolerances() {
            return this == REGULAR_FLAT;
        }

        static Profile fromId(final String id) {
            for (final Profile profile : values()) {
                if (profile.id.equals(id)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("unknown corrective-family profile '" + id + "'; expected one of "
                    + java.util.Arrays.stream(values()).map(Profile::id).toList());
        }
    }

    /**
     * One selected profile with its declared experimental bands.
     *
     * <p>
     * Only the regular flat has bands: {@code minRetracement} is the smallest B
     * retracement of A and {@code maxOvershoot} the largest C overshoot of A's
     * end, both as fractions of A's amplitude. The defaults are declared
     * experimental settings, not fitted optima.
     *
     * @param profile         selected profile
     * @param minRetracement  regular flat only, in {@code (0, 1]}; {@code null}
     *                        otherwise
     * @param maxOvershoot    regular flat only, positive; {@code null} otherwise
     * @since 0.26.1
     */
    record Spec(Profile profile, BigDecimal minRetracement, BigDecimal maxOvershoot) {

        Spec {
            Objects.requireNonNull(profile, "profile");
            if (profile.hasTolerances()) {
                Objects.requireNonNull(minRetracement, "minRetracement");
                Objects.requireNonNull(maxOvershoot, "maxOvershoot");
                if (minRetracement.signum() <= 0 || minRetracement.compareTo(BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException(
                            "minRetracement must lie in (0, 1] but was " + minRetracement.toPlainString());
                }
                if (maxOvershoot.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "maxOvershoot must be positive but was " + maxOvershoot.toPlainString());
                }
                minRetracement = minRetracement.stripTrailingZeros();
                maxOvershoot = maxOvershoot.stripTrailingZeros();
            } else if (minRetracement != null || maxOvershoot != null) {
                throw new IllegalArgumentException(
                        "profile " + profile.id() + " has no tolerance bands; remove minRetracement/maxOvershoot");
            }
        }

        /**
         * @param profile selected profile
         * @return the profile with its declared default bands
         */
        static Spec defaults(final Profile profile) {
            return profile.hasTolerances()
                    ? new Spec(profile, new BigDecimal(REGULAR_FLAT_MIN_RETRACEMENT),
                            new BigDecimal(REGULAR_FLAT_MAX_OVERSHOOT))
                    : new Spec(profile, null, null);
        }

        /**
         * @return identity of the profile definition and its effective bands, e.g.
         *         {@code regular-flat/1{minRetracement=0.9,maxOvershoot=0.1}}
         */
        String version() {
            final StringBuilder text = new StringBuilder(profile.id()).append('/').append(PROFILE_REVISION);
            if (profile.hasTolerances()) {
                text.append("{minRetracement=")
                        .append(minRetracement.toPlainString())
                        .append(",maxOvershoot=")
                        .append(maxOvershoot.toPlainString())
                        .append('}');
            }
            return text.toString();
        }
    }

    /** Overall outcome of one profile against one parent candidate. */
    enum Status {
        /** Envelope passes and every parent leg has proven child subdivision. */
        VERIFIED,
        /** Envelope passes; child subdivision is pending or has no evidence. */
        SHAPE_COMPATIBLE,
        /** The envelope or a child subdivision contradicts the profile. */
        OUTSIDE_PROFILE,
        /** Evidence is missing or a bound kept it from being examined. */
        UNAVAILABLE;

        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** Subdivision state of one parent leg. */
    enum LegState {
        SUPPORTED, CONFLICTING, PENDING, NO_EVIDENCE;

        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * A child relation consulted for one parent leg.
     *
     * @param key             edge key
     * @param version         edge version
     * @param state           relation state
     * @param childCandidateKey child candidate identity, or {@code null}
     * @param childPivotCount   child pivots the edge relies on
     * @param availableAt     confirmation index of the edge's evidence
     * @param predicates      the edge's tested predicates
     */
    record ChildEdge(String key, String version, ScaleRelation.State state, String childCandidateKey,
            int childPivotCount, int availableAt, List<ScaleRelation.Predicate> predicates) {
        ChildEdge {
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * Subdivision evidence of one parent leg.
     *
     * @param leg      zero-based parent leg
     * @param expected child grammar the profile requires
     * @param state    leg state
     * @param edges    child relations consulted, ordered by key
     */
    record Leg(int leg, TopologyGrammar expected, LegState state, List<ChildEdge> edges) {
        Leg {
            edges = List.copyOf(edges);
        }
    }

    /**
     * One way the verified parent legs combine from child decompositions.
     *
     * @param key      composition identity
     * @param edgeKeys supported edge key of each leg, in leg order
     */
    record Composition(String key, List<String> edgeKeys) {
        Composition {
            edgeKeys = List.copyOf(edgeKeys);
        }
    }

    /**
     * The immutable judgement of one profile over one parent candidate.
     *
     * @param key                   identity of profile, parent scale and parent
     *                              candidate; independent of evidence
     * @param version               {@code key@digest} of the full judgement
     * @param spec                  profile and effective bands
     * @param parentScale           parent scale name
     * @param parentCandidateKey    parent candidate identity
     * @param parentVersion         parent candidate version
     * @param parentDirection       parent candidate direction
     * @param parentPivots          parent pivots the envelope was computed from
     * @param status                outcome
     * @param reason                short machine-readable cause of the status
     * @param envelope              envelope predicates with actual values
     * @param legs                  per-leg subdivision evidence
     * @param compositions          at most {@code maxCompositions} compositions
     * @param compositionCount      exact number of compositions (saturating)
     * @param compositionsTruncated whether {@code compositions} omits some
     * @param evidenceComplete      whether the child relation search was complete
     * @param availableAt           maximum confirmation index of all evidence used
     */
    record Verdict(String key, String version, Spec spec, String parentScale, String parentCandidateKey,
            String parentVersion, WaveDirection parentDirection, List<ConfirmedPivot> parentPivots, Status status,
            String reason, List<ScaleRelation.Predicate> envelope, List<Leg> legs, List<Composition> compositions,
            long compositionCount, boolean compositionsTruncated, boolean evidenceComplete, int availableAt) {
        Verdict {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(status, "status");
            parentPivots = List.copyOf(parentPivots);
            envelope = List.copyOf(envelope);
            legs = List.copyOf(legs);
            compositions = List.copyOf(compositions);
        }
    }

    /**
     * Judges one parent candidate against one profile.
     *
     * @param spec             selected profile and bands
     * @param parentScale      parent scale name
     * @param parent           parent candidate with identity
     * @param edges            relations of this parent (any leg, any state)
     * @param evidenceComplete whether the child relation search of this
     *                         observation examined everything it was asked to
     * @param maxCompositions  most compositions listed
     * @return the verdict
     */
    static Verdict evaluate(final Spec spec, final String parentScale, final ScaleRelationExtractor.Parent parent,
            final List<ScaleRelation.Edge> edges, final boolean evidenceComplete, final int maxCompositions) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(parentScale, "parentScale");
        Objects.requireNonNull(parent, "parent");
        if (maxCompositions < 1) {
            throw new IllegalArgumentException("maxCompositions must be positive");
        }
        final Profile profile = spec.profile();
        final TopologyCandidate candidate = parent.candidate();
        if (candidate.grammar() != profile.parentGrammar()) {
            throw new IllegalArgumentException("profile " + profile.id() + " judges " + profile.parentGrammar()
                    + " parents, not " + candidate.grammar());
        }
        final List<ConfirmedPivot> pivots = candidate.pivots();
        final List<ScaleRelation.Predicate> envelope = profile == Profile.CONTRACTING_TRIANGLE
                ? triangleEnvelope(pivots)
                : abcEnvelope(spec, pivots);
        final List<Leg> legs = legs(profile, parentScale, parent, edges);
        int availableAt = 0;
        for (final ConfirmedPivot pivot : pivots) {
            availableAt = Math.max(availableAt, pivot.confirmationIndex());
        }
        for (final Leg leg : legs) {
            for (final ChildEdge edge : leg.edges()) {
                availableAt = Math.max(availableAt, edge.availableAt());
            }
        }
        final boolean envelopeUnavailable = envelope.stream()
                .anyMatch(predicate -> predicate.state() == EvidenceState.UNAVAILABLE);
        final boolean envelopeFailed = envelope.stream().anyMatch(predicate -> predicate.state() == EvidenceState.FAIL);
        final boolean allSupported = legs.stream().allMatch(leg -> leg.state() == LegState.SUPPORTED);
        final boolean anyConflict = legs.stream().anyMatch(leg -> leg.state() == LegState.CONFLICTING);
        final Status status;
        final String reason;
        if (envelopeUnavailable) {
            status = Status.UNAVAILABLE;
            reason = "envelope-unavailable";
        } else if (envelopeFailed) {
            status = Status.OUTSIDE_PROFILE;
            reason = "envelope";
        } else if (allSupported) {
            status = Status.VERIFIED;
            reason = "subdivision-verified";
        } else if (!evidenceComplete) {
            status = Status.UNAVAILABLE;
            reason = "search-bound-hit";
        } else if (anyConflict) {
            status = Status.OUTSIDE_PROFILE;
            reason = "child-subdivision";
        } else {
            status = Status.SHAPE_COMPATIBLE;
            reason = "child-subdivision-unproven";
        }
        final String key = ScaleRelation
                .digest("family|" + spec.version() + "|" + parentScale + "|" + parent.key());
        final List<Composition> compositions = new ArrayList<>();
        long total = 0;
        boolean truncated = false;
        if (status == Status.VERIFIED) {
            final List<List<ChildEdge>> supported = new ArrayList<>();
            for (final Leg leg : legs) {
                supported.add(leg.edges().stream().filter(CorrectiveFamily::isSupported).toList());
            }
            total = 1;
            for (final List<ChildEdge> perLeg : supported) {
                total = saturatingMultiply(total, perLeg.size());
            }
            final int[] cursor = new int[supported.size()];
            final long listed = Math.min(total, maxCompositions);
            for (long n = 0; n < listed; n++) {
                final List<String> edgeKeys = new ArrayList<>(cursor.length);
                for (int leg = 0; leg < cursor.length; leg++) {
                    edgeKeys.add(supported.get(leg).get(cursor[leg]).key());
                }
                compositions.add(new Composition(ScaleRelation.digest(key + "|" + String.join(",", edgeKeys)),
                        edgeKeys));
                for (int leg = cursor.length - 1; leg >= 0; leg--) {
                    if (++cursor[leg] < supported.get(leg).size()) {
                        break;
                    }
                    cursor[leg] = 0;
                }
            }
            truncated = total > listed;
        }
        final StringBuilder identity = new StringBuilder(spec.version()).append('|')
                .append(parent.version())
                .append('|')
                .append(status.label())
                .append('|')
                .append(reason)
                .append('|')
                .append(evidenceComplete)
                .append('|')
                .append(availableAt);
        envelope.forEach(predicate -> identity.append('|')
                .append(predicate.id())
                .append(':')
                .append(predicate.state())
                .append(':')
                .append(predicate.detail()));
        for (final Leg leg : legs) {
            identity.append("|leg").append(leg.leg()).append(':').append(leg.state());
            leg.edges().forEach(edge -> identity.append(',').append(edge.version()));
        }
        compositions.forEach(composition -> identity.append('|').append(composition.key()));
        identity.append('|').append(total).append(truncated);
        return new Verdict(key, key + "@" + ScaleRelation.digest(identity.toString()), spec, parentScale,
                parent.key(), parent.version(), candidate.direction(), pivots, status, reason, envelope, legs,
                compositions, total, truncated, evidenceComplete, availableAt);
    }

    private static boolean isSupported(final ChildEdge edge) {
        return edge.state() == ScaleRelation.State.SUBDIVISION_SUPPORTED;
    }

    private static long saturatingMultiply(final long left, final long right) {
        final long high = Math.multiplyHigh(left, right);
        final long low = left * right;
        return high != 0 || low < 0 ? Long.MAX_VALUE : low;
    }

    private static List<Leg> legs(final Profile profile, final String parentScale,
            final ScaleRelationExtractor.Parent parent, final List<ScaleRelation.Edge> edges) {
        final List<Leg> legs = new ArrayList<>();
        final List<TopologyGrammar> expected = profile.childGrammars();
        for (int leg = 0; leg < expected.size(); leg++) {
            final int index = leg;
            final List<ChildEdge> forLeg = edges.stream()
                    .filter(edge -> edge.parentLeg() == index && edge.parentScale().equals(parentScale)
                            && edge.parentCandidateKey().equals(parent.key())
                            && edge.childGrammar() == expected.get(index))
                    .sorted(Comparator.comparing(ScaleRelation.Edge::key))
                    .map(edge -> new ChildEdge(edge.key(), edge.version(), edge.state(), edge.childCandidateKey(),
                            edge.childPivotCount(), edge.availableAt(), edge.predicates()))
                    .toList();
            final LegState state;
            if (forLeg.stream().anyMatch(edge -> edge.state() == ScaleRelation.State.SUBDIVISION_SUPPORTED)) {
                state = LegState.SUPPORTED;
            } else if (forLeg.stream().anyMatch(edge -> edge.state() == ScaleRelation.State.CONFLICTING_EVIDENCE)) {
                state = LegState.CONFLICTING;
            } else if (forLeg.stream().anyMatch(edge -> edge.state() == ScaleRelation.State.PENDING_CONFIRMATION)) {
                state = LegState.PENDING;
            } else {
                state = LegState.NO_EVIDENCE;
            }
            legs.add(new Leg(leg, expected.get(leg), state, forLeg));
        }
        return legs;
    }

    // ------------------------------------------------------------- envelope

    private static List<ScaleRelation.Predicate> abcEnvelope(final Spec spec,
            final List<ConfirmedPivot> pivots) {
        final Num origin = pivots.get(0).price();
        final NumFactory factory = origin.getNumFactory();
        final Num first = pivots.get(1).price().minus(origin);
        final List<ScaleRelation.Predicate> predicates = new ArrayList<>();
        if (first.isZero()) {
            predicates.add(new ScaleRelation.Predicate("amplitude", EvidenceState.UNAVAILABLE,
                    "A amplitude is zero; normalized ratios are undefined"));
            return predicates;
        }
        final Num sign = first.isPositive() ? factory.one() : factory.one().negate();
        final Num u1 = sign.multipliedBy(first);
        final Num u2 = sign.multipliedBy(pivots.get(2).price().minus(origin));
        final Num u3 = sign.multipliedBy(pivots.get(3).price().minus(origin));
        predicates.add(new ScaleRelation.Predicate("amplitude", EvidenceState.PASS, "a=" + u1));
        predicates.add(state("b-below-a-end", u2.isLessThan(u1), "u2=" + u2 + " u1=" + u1));
        predicates.add(state("c-above-b-end", u3.isGreaterThan(u2), "u3=" + u3 + " u2=" + u2));
        final Num retracement = u1.minus(u2);
        final Num overshoot = u3.minus(u1);
        final String ratios = "rB=" + retracement.dividedBy(u1) + " oC=" + overshoot.dividedBy(u1);
        switch (spec.profile()) {
        case ZIGZAG -> {
            predicates.add(state("b-retracement", retracement.isPositive() && u2.isPositive(),
                    ratios + " required 0<rB<1"));
            predicates.add(state("c-overshoot", overshoot.isPositive(), ratios + " required oC>0"));
        }
        case REGULAR_FLAT -> {
            final Num min = factory.numOf(spec.minRetracement().toPlainString());
            final Num max = factory.numOf(spec.maxOvershoot().toPlainString());
            predicates.add(state("b-retracement",
                    retracement.isGreaterThanOrEqual(min.multipliedBy(u1)) && !u2.isNegative(),
                    ratios + " required " + spec.minRetracement().toPlainString() + "<=rB<=1"));
            predicates.add(state("c-overshoot",
                    overshoot.isPositive() && overshoot.isLessThanOrEqual(max.multipliedBy(u1)),
                    ratios + " required 0<oC<=" + spec.maxOvershoot().toPlainString()));
        }
        case EXPANDED_FLAT -> {
            predicates.add(state("b-retracement", u2.isNegative(), ratios + " required rB>1"));
            predicates.add(state("c-overshoot", overshoot.isPositive(), ratios + " required oC>0"));
        }
        default -> throw new IllegalStateException("not an A/B/C profile: " + spec.profile());
        }
        return predicates;
    }

    private static List<ScaleRelation.Predicate> triangleEnvelope(final List<ConfirmedPivot> pivots) {
        final Num origin = pivots.get(0).price();
        final NumFactory factory = origin.getNumFactory();
        final Num first = pivots.get(1).price().minus(origin);
        final List<ScaleRelation.Predicate> predicates = new ArrayList<>();
        if (first.isZero()) {
            predicates.add(new ScaleRelation.Predicate("amplitude", EvidenceState.UNAVAILABLE,
                    "A amplitude is zero; the envelope is undefined"));
            return predicates;
        }
        final Num sign = first.isPositive() ? factory.one() : factory.one().negate();
        final Num[] u = new Num[6];
        for (int i = 0; i < u.length; i++) {
            u[i] = sign.multipliedBy(pivots.get(i).price().minus(origin));
        }
        final int i1 = pivots.get(1).pivotIndex();
        final int i2 = pivots.get(2).pivotIndex();
        final int i3 = pivots.get(3).pivotIndex();
        final int i4 = pivots.get(4).pivotIndex();
        final int i5 = pivots.get(5).pivotIndex();
        predicates.add(new ScaleRelation.Predicate("amplitude", EvidenceState.PASS, "a=" + u[1]));
        predicates.add(state("upper-extrema-inward", u[1].isGreaterThan(u[3]) && u[3].isGreaterThan(u[5]),
                "A=" + u[1] + " C=" + u[3] + " E=" + u[5] + " required A>C>E"));
        predicates.add(state("lower-extrema-inward", u[2].isLessThan(u[4]),
                "B=" + u[2] + " D=" + u[4] + " required B<D"));
        predicates.add(state("b-inside-start-envelope", u[2].isPositive() && u[2].isLessThan(u[1]),
                "B=" + u[2] + " required 0<B<A=" + u[1]));
        final Num upperAtStart = u[1];
        final Num upperAtEnd = line(factory, i1, u[1], i3, u[3], i5);
        final Num lowerAtStart = line(factory, i2, u[2], i4, u[4], i1);
        final Num lowerAtEnd = line(factory, i2, u[2], i4, u[4], i5);
        final Num widthStart = upperAtStart.minus(lowerAtStart);
        final Num widthEnd = upperAtEnd.minus(lowerAtEnd);
        predicates.add(state("boundaries-not-crossed", widthStart.isPositive() && widthEnd.isPositive(),
                "width@" + i1 + "=" + widthStart + " width@" + i5 + "=" + widthEnd + " required both >0"));
        predicates.add(state("envelope-contracting", widthStart.isGreaterThan(widthEnd),
                "width@" + i1 + "=" + widthStart + " width@" + i5 + "=" + widthEnd + " required start>end"));
        predicates.add(state("e-inside-envelope", u[5].isLessThanOrEqual(upperAtEnd) && u[5].isGreaterThanOrEqual(lowerAtEnd),
                "E=" + u[5] + " upper@" + i5 + "=" + upperAtEnd + " lower@" + i5 + "=" + lowerAtEnd
                        + " required lower<=E<=upper (no throw-over)"));
        return predicates;
    }

    /** Value at {@code at} of the line through two (index, value) points. */
    private static Num line(final NumFactory factory, final int indexA, final Num valueA, final int indexB,
            final Num valueB, final int at) {
        final Num run = factory.numOf(indexB - indexA);
        return valueA.plus(valueB.minus(valueA).multipliedBy(factory.numOf(at - indexA)).dividedBy(run));
    }

    private static ScaleRelation.Predicate state(final String id, final boolean pass, final String detail) {
        return new ScaleRelation.Predicate(id, pass ? EvidenceState.PASS : EvidenceState.FAIL, detail);
    }
}
