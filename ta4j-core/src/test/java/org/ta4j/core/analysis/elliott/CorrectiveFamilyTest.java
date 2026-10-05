/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.pt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.ScaleRelationFixtures.Pt;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.DecimalNumFactory;

class CorrectiveFamilyTest {

    static final int BARS = 90;
    static final int PARENT_LAG = 1;
    static final int CHILD_LAG = 1;

    static final List<Pt> ZIGZAG_PARENT = List.of(pt(0, 200, 'H'), pt(10, 150, 'L'), pt(15, 170, 'H'),
            pt(30, 120, 'L'));
    static final List<Pt> ZIGZAG_CHILD = List.of(pt(0, 200, 'H'), pt(2, 185, 'L'), pt(3, 192, 'H'),
            pt(6, 160, 'L'), pt(7, 175, 'H'), pt(10, 150, 'L'), pt(12, 165, 'H'), pt(13, 157, 'L'), pt(15, 170, 'H'),
            pt(17, 155, 'L'), pt(19, 163, 'H'), pt(24, 135, 'L'), pt(26, 145, 'H'), pt(30, 120, 'L'));

    private static final List<Pt> REGULAR_PARENT = List.of(pt(0, 200, 'H'), pt(10, 150, 'L'), pt(15, 196, 'H'),
            pt(30, 148, 'L'));
    private static final List<Pt> REGULAR_CHILD = List.of(pt(0, 200, 'H'), pt(3, 170, 'L'), pt(5, 182, 'H'),
            pt(10, 150, 'L'), pt(12, 190, 'H'), pt(13, 170, 'L'), pt(15, 196, 'H'), pt(17, 178, 'L'),
            pt(19, 188, 'H'), pt(24, 160, 'L'), pt(26, 170, 'H'), pt(30, 148, 'L'));

    private static final List<Pt> EXPANDED_PARENT = List.of(pt(0, 200, 'H'), pt(10, 150, 'L'), pt(15, 205, 'H'),
            pt(30, 135, 'L'));
    private static final List<Pt> EXPANDED_CHILD = List.of(pt(0, 200, 'H'), pt(3, 170, 'L'), pt(5, 182, 'H'),
            pt(10, 150, 'L'), pt(12, 195, 'H'), pt(13, 175, 'L'), pt(15, 205, 'H'), pt(17, 180, 'L'),
            pt(19, 195, 'H'), pt(24, 155, 'L'), pt(26, 170, 'H'), pt(30, 135, 'L'));

    private static final List<Pt> TRIANGLE_PARENT = List.of(pt(0, 100, 'L'), pt(10, 140, 'H'), pt(20, 115, 'L'),
            pt(30, 132, 'H'), pt(40, 120, 'L'), pt(50, 123.5, 'H'));
    private static final List<Pt> TRIANGLE_CHILD = List.of(pt(0, 100, 'L'), pt(4, 125, 'H'), pt(6, 112, 'L'),
            pt(10, 140, 'H'), pt(13, 120, 'L'), pt(15, 130, 'H'), pt(20, 115, 'L'), pt(23, 128, 'H'),
            pt(25, 122, 'L'), pt(30, 132, 'H'), pt(33, 124, 'L'), pt(35, 129, 'H'), pt(40, 120, 'L'),
            pt(43, 123, 'H'), pt(45, 121, 'L'), pt(50, 123.5, 'H'));

    private static CorrectiveFamily.Spec spec(final CorrectiveFamily.Profile profile) {
        return CorrectiveFamily.Spec.defaults(profile);
    }

    /** Frames of one study run over scripted parent and child tapes. */
    private static List<CorrectiveFamilyStudy.Frame> frames(final List<Pt> parent, final int parentLag,
            final List<Pt> child, final int childLag, final ScaleRelation.Policy policy,
            final List<CorrectiveFamily.Spec> specs) {
        final CorrectiveFamilyStudy study = new CorrectiveFamilyStudy(
                List.of(ScaleRelationFixtures.input("parent", parent, parentLag),
                        ScaleRelationFixtures.input("child", child, childLag)),
                policy, ScaleRelationFixtures.passingRules(), ScaleRelationFixtures.identity(),
                ScaleRelationFixtures.PARTITIONS, specs, CorrectiveFamily.DEFAULT_MAX_COMPOSITIONS);
        final List<CorrectiveFamilyStudy.Frame> frames = new ArrayList<>();
        study.run(ScaleRelationFixtures.series(BARS), 0, BARS - 1, DetectorReplays.uncached(), frames::add);
        return frames;
    }

    private static List<CorrectiveFamilyStudy.Frame> frames(final List<Pt> parent, final List<Pt> child,
            final CorrectiveFamily.Profile... profiles) {
        return frames(parent, PARENT_LAG, child, CHILD_LAG, ScaleRelation.Policy.defaults(),
                java.util.Arrays.stream(profiles).map(CorrectiveFamilyTest::spec).toList());
    }

    /** Latest active verdict of each profile after the whole replay. */
    private static Map<String, CorrectiveFamily.Verdict> finalVerdicts(
            final List<CorrectiveFamilyStudy.Frame> frames) {
        final Map<String, CorrectiveFamily.Verdict> active = new TreeMap<>();
        for (final CorrectiveFamilyStudy.Frame frame : frames) {
            for (final CorrectiveFamilyStudy.Event event : frame.events()) {
                final String id = event.verdict().spec().profile().id();
                if (event.lifecycle() == ScaleRelation.Lifecycle.ACTIVE) {
                    active.put(id, event.verdict());
                } else {
                    active.remove(id);
                }
            }
        }
        return active;
    }

    private static CorrectiveFamily.Verdict verdict(final List<CorrectiveFamilyStudy.Frame> frames,
            final CorrectiveFamily.Profile profile) {
        final CorrectiveFamily.Verdict verdict = finalVerdicts(frames).get(profile.id());
        assertTrue(verdict != null, "no active verdict for " + profile.id());
        return verdict;
    }

    private static String predicate(final CorrectiveFamily.Verdict verdict, final String id) {
        return verdict.envelope()
                .stream()
                .filter(predicate -> predicate.id().equals(id))
                .findFirst()
                .orElseThrow()
                .state()
                .name();
    }

    @Test
    void zigzagIsVerifiedOnlyWhenEveryLegSubdividesFiveThreeFive() {
        final CorrectiveFamily.Verdict verdict = verdict(
                frames(ZIGZAG_PARENT, ZIGZAG_CHILD, CorrectiveFamily.Profile.ZIGZAG), CorrectiveFamily.Profile.ZIGZAG);

        assertEquals(CorrectiveFamily.Status.VERIFIED, verdict.status());
        assertEquals("subdivision-verified", verdict.reason());
        assertEquals("zigzag/1", verdict.spec().version());
        assertEquals(ScenarioType.CORRECTIVE_ZIGZAG, verdict.spec().profile().scenarioType());
        assertEquals("5-3-5", verdict.spec().profile().subdivision());
        assertEquals(List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.MOTIVE_5),
                verdict.legs().stream().map(CorrectiveFamily.Leg::expected).toList());
        assertTrue(verdict.legs().stream().allMatch(leg -> leg.state() == CorrectiveFamily.LegState.SUPPORTED));
        assertTrue(verdict.envelope().stream().allMatch(predicate -> predicate.state() == EvidenceState.PASS),
                verdict.envelope().toString());
        assertEquals(1, verdict.compositionCount());
        assertFalse(verdict.compositionsTruncated());
        assertTrue(verdict.evidenceComplete());
    }

    @Test
    void everyVerdictCarriesPerPredicateEvidenceAndAnAvailabilityNotAfterItsObservation() {
        final List<CorrectiveFamilyStudy.Frame> frames = frames(ZIGZAG_PARENT, ZIGZAG_CHILD,
                CorrectiveFamily.Profile.ZIGZAG);
        int events = 0;
        for (final CorrectiveFamilyStudy.Frame frame : frames) {
            for (final CorrectiveFamilyStudy.Event event : frame.events()) {
                events++;
                final CorrectiveFamily.Verdict verdict = event.verdict();
                assertTrue(verdict.availableAt() <= frame.asOfIndex(),
                        "verdict used evidence confirmed after its observation: " + verdict.availableAt() + " > "
                                + frame.asOfIndex());
                assertFalse(verdict.envelope().isEmpty());
                assertTrue(verdict.envelope().stream().allMatch(predicate -> !predicate.detail().isBlank()));
                for (final CorrectiveFamily.Leg leg : verdict.legs()) {
                    for (final CorrectiveFamily.ChildEdge edge : leg.edges()) {
                        assertTrue(edge.availableAt() <= frame.asOfIndex());
                        assertFalse(edge.predicates().isEmpty());
                    }
                }
            }
        }
        assertTrue(events > 0);
    }

    @Test
    void childSubdivisionConfirmedAfterTheParentLeavesTheVerdictShapeCompatibleUntilItArrives() {
        final List<CorrectiveFamilyStudy.Frame> frames = frames(ZIGZAG_PARENT, 1, ZIGZAG_CHILD, 9,
                ScaleRelation.Policy.defaults(), List.of(spec(CorrectiveFamily.Profile.ZIGZAG)));

        final List<CorrectiveFamilyStudy.Event> events = frames.stream()
                .flatMap(frame -> frame.events().stream())
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ACTIVE)
                .toList();
        assertTrue(events.size() >= 2, events.toString());
        final CorrectiveFamily.Verdict first = events.get(0).verdict();
        final CorrectiveFamily.Verdict last = events.get(events.size() - 1).verdict();
        assertEquals(CorrectiveFamilyStudy.Reason.OBSERVED, events.get(0).reason());
        assertEquals(CorrectiveFamily.Status.SHAPE_COMPATIBLE, first.status());
        assertEquals("child-subdivision-unproven", first.reason());
        assertTrue(first.legs().stream().anyMatch(leg -> leg.state() != CorrectiveFamily.LegState.SUPPORTED));
        assertEquals(CorrectiveFamilyStudy.Reason.REVISED, events.get(1).reason());
        assertEquals(CorrectiveFamily.Status.VERIFIED, last.status());
        assertEquals(first.key(), last.key(), "key names profile and parent, never the evidence");
        assertNotEquals(first.version(), last.version());
        assertTrue(last.availableAt() > first.availableAt());
    }

    @Test
    void flatProfilesAreSeparateHypothesesOverTheSameThreeThreeFiveEvidence() {
        final List<CorrectiveFamilyStudy.Frame> frames = frames(REGULAR_PARENT, REGULAR_CHILD,
                CorrectiveFamily.Profile.REGULAR_FLAT, CorrectiveFamily.Profile.EXPANDED_FLAT,
                CorrectiveFamily.Profile.ZIGZAG);
        final CorrectiveFamily.Verdict regular = verdict(frames, CorrectiveFamily.Profile.REGULAR_FLAT);
        final CorrectiveFamily.Verdict expanded = verdict(frames, CorrectiveFamily.Profile.EXPANDED_FLAT);
        final CorrectiveFamily.Verdict zigzag = verdict(frames, CorrectiveFamily.Profile.ZIGZAG);

        assertEquals(CorrectiveFamily.Status.VERIFIED, regular.status());
        assertEquals("regular-flat/1{minRetracement=0.9,maxOvershoot=0.1}", regular.spec().version());
        assertEquals("3-3-5", regular.spec().profile().subdivision());
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, expanded.status());
        assertEquals("envelope", expanded.reason());
        assertEquals("FAIL", predicate(expanded, "b-retracement"));
        assertEquals(ScenarioType.CORRECTIVE_FLAT, regular.spec().profile().scenarioType());
        assertEquals(ScenarioType.CORRECTIVE_FLAT, expanded.spec().profile().scenarioType());
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, zigzag.status(),
                "a 5-3-5 hypothesis is not rescued by 3-3-5 evidence");
    }

    @Test
    void expandedFlatNeedsBBeyondTheOriginAndIsNotAcceptedAsARegularFlat() {
        final List<CorrectiveFamilyStudy.Frame> frames = frames(EXPANDED_PARENT, EXPANDED_CHILD,
                CorrectiveFamily.Profile.REGULAR_FLAT, CorrectiveFamily.Profile.EXPANDED_FLAT);

        final CorrectiveFamily.Verdict expanded = verdict(frames, CorrectiveFamily.Profile.EXPANDED_FLAT);
        final CorrectiveFamily.Verdict regular = verdict(frames, CorrectiveFamily.Profile.REGULAR_FLAT);
        assertEquals(CorrectiveFamily.Status.VERIFIED, expanded.status());
        assertEquals("PASS", predicate(expanded, "b-retracement"));
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, regular.status());
        assertEquals("FAIL", predicate(regular, "b-retracement"));
        assertEquals("FAIL", predicate(regular, "c-overshoot"));
        assertTrue(regular.legs().stream().allMatch(leg -> leg.state() == CorrectiveFamily.LegState.SUPPORTED),
                "the envelope alone excludes the profile; subdivision evidence stays visible");
    }

    @Test
    void regularFlatBandsAreRecipeDeclaredAndPartOfTheVerdictIdentity() {
        final CorrectiveFamily.Spec strict = new CorrectiveFamily.Spec(CorrectiveFamily.Profile.REGULAR_FLAT,
                new BigDecimal("0.95"), new BigDecimal("0.02"));
        final List<CorrectiveFamilyStudy.Frame> frames = frames(REGULAR_PARENT, PARENT_LAG, REGULAR_CHILD, CHILD_LAG,
                ScaleRelation.Policy.defaults(), List.of(strict));

        final CorrectiveFamily.Verdict verdict = verdict(frames, CorrectiveFamily.Profile.REGULAR_FLAT);
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, verdict.status());
        assertEquals("regular-flat/1{minRetracement=0.95,maxOvershoot=0.02}", verdict.spec().version());
        assertEquals("FAIL", predicate(verdict, "c-overshoot"));
        final CorrectiveFamily.Verdict lenient = verdict(
                frames(REGULAR_PARENT, REGULAR_CHILD, CorrectiveFamily.Profile.REGULAR_FLAT),
                CorrectiveFamily.Profile.REGULAR_FLAT);
        assertNotEquals(lenient.key(), verdict.key());
    }

    @Test
    void contractingTriangleChecksTheEnvelopeAndFiveThreeWaveLegs() {
        final CorrectiveFamily.Verdict verdict = verdict(
                frames(TRIANGLE_PARENT, TRIANGLE_CHILD, CorrectiveFamily.Profile.CONTRACTING_TRIANGLE),
                CorrectiveFamily.Profile.CONTRACTING_TRIANGLE);

        assertEquals(CorrectiveFamily.Status.VERIFIED, verdict.status(), verdict.toString());
        assertEquals(5, verdict.legs().size());
        assertEquals("3-3-3-3-3", verdict.spec().profile().subdivision());
        assertEquals(ScenarioType.CORRECTIVE_TRIANGLE, verdict.spec().profile().scenarioType());
        assertEquals(List.of("amplitude", "upper-extrema-inward", "lower-extrema-inward", "b-inside-start-envelope",
                "boundaries-not-crossed", "envelope-contracting", "e-inside-envelope"),
                verdict.envelope().stream().map(ScaleRelation.Predicate::id).toList());
        assertTrue(verdict.envelope().stream().allMatch(predicate -> predicate.state() == EvidenceState.PASS));
    }

    @Test
    void expandingOrThrownOverTrianglesStayOutsideTheStrictProfile() {
        final List<Pt> expanding = List.of(pt(0, 100, 'L'), pt(10, 140, 'H'), pt(20, 115, 'L'), pt(30, 150, 'H'),
                pt(40, 110, 'L'), pt(50, 160, 'H'));
        final CorrectiveFamily.Verdict wide = verdict(
                frames(expanding, TRIANGLE_CHILD, CorrectiveFamily.Profile.CONTRACTING_TRIANGLE),
                CorrectiveFamily.Profile.CONTRACTING_TRIANGLE);
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, wide.status());
        assertEquals("envelope", wide.reason());
        assertEquals("FAIL", predicate(wide, "upper-extrema-inward"));

        final List<Pt> thrownOver = new ArrayList<>(TRIANGLE_PARENT);
        thrownOver.set(5, pt(50, 126, 'H'));
        final CorrectiveFamily.Verdict over = verdict(
                frames(thrownOver, TRIANGLE_CHILD, CorrectiveFamily.Profile.CONTRACTING_TRIANGLE),
                CorrectiveFamily.Profile.CONTRACTING_TRIANGLE);
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, over.status());
        assertEquals("FAIL", predicate(over, "e-inside-envelope"));
    }

    @Test
    void absentChildEvidenceIsShapeCompatibleNeverVerifiedOrExcluded() {
        final CorrectiveFamily.Verdict verdict = verdict(
                frames(ZIGZAG_PARENT, List.of(pt(0, 200, 'H'), pt(10, 150, 'L'), pt(15, 170, 'H'), pt(30, 120, 'L')),
                        CorrectiveFamily.Profile.ZIGZAG),
                CorrectiveFamily.Profile.ZIGZAG);

        assertEquals(CorrectiveFamily.Status.SHAPE_COMPATIBLE, verdict.status());
        assertEquals("child-subdivision-unproven", verdict.reason());
        assertTrue(verdict.legs().stream().noneMatch(leg -> leg.state() == CorrectiveFamily.LegState.SUPPORTED));
        assertEquals(0, verdict.compositionCount());
        assertTrue(verdict.compositions().isEmpty());
    }

    @Test
    void wrongSubdivisionOfALegIsNeverCountedAsSupport() {
        final List<Pt> threeThreeFive = REGULAR_CHILD;
        final CorrectiveFamily.Verdict verdict = verdict(frames(REGULAR_PARENT, threeThreeFive,
                CorrectiveFamily.Profile.ZIGZAG), CorrectiveFamily.Profile.ZIGZAG);

        assertNotEquals(CorrectiveFamily.Status.VERIFIED, verdict.status());
        assertNotEquals(CorrectiveFamily.LegState.SUPPORTED, verdict.legs().get(0).state(),
                "a 3-wave first leg cannot support the 5-wave leg a zigzag requires");
    }

    @Test
    void enablingAnotherProfileNeverChangesAProfilesEvidence() {
        final CorrectiveFamily.Verdict alone = verdict(
                frames(ZIGZAG_PARENT, ZIGZAG_CHILD, CorrectiveFamily.Profile.ZIGZAG), CorrectiveFamily.Profile.ZIGZAG);
        final CorrectiveFamily.Verdict together = verdict(
                frames(ZIGZAG_PARENT, ZIGZAG_CHILD, CorrectiveFamily.Profile.ZIGZAG,
                        CorrectiveFamily.Profile.REGULAR_FLAT, CorrectiveFamily.Profile.EXPANDED_FLAT,
                        CorrectiveFamily.Profile.CONTRACTING_TRIANGLE),
                CorrectiveFamily.Profile.ZIGZAG);
        assertEquals(alone.version(), together.version());

        final CorrectiveFamily.Verdict regularAlone = verdict(
                frames(REGULAR_PARENT, REGULAR_CHILD, CorrectiveFamily.Profile.REGULAR_FLAT),
                CorrectiveFamily.Profile.REGULAR_FLAT);
        final CorrectiveFamily.Verdict regularWithExpanded = verdict(
                frames(REGULAR_PARENT, REGULAR_CHILD, CorrectiveFamily.Profile.EXPANDED_FLAT,
                        CorrectiveFamily.Profile.REGULAR_FLAT),
                CorrectiveFamily.Profile.REGULAR_FLAT);
        assertEquals(regularAlone.version(), regularWithExpanded.version());
    }

    @Test
    void anEdgeCapThatHidesChildEvidenceMakesTheVerdictUnavailableNotShapeCompatible() {
        final ScaleRelation.Policy capped = ScaleRelation.Policy.defaults().withEdgeCap(1);
        final List<CorrectiveFamilyStudy.Frame> frames = frames(ZIGZAG_PARENT, PARENT_LAG, ZIGZAG_CHILD, CHILD_LAG,
                capped, List.of(spec(CorrectiveFamily.Profile.ZIGZAG)));

        final CorrectiveFamily.Verdict verdict = verdict(frames, CorrectiveFamily.Profile.ZIGZAG);
        assertEquals(CorrectiveFamily.Status.UNAVAILABLE, verdict.status());
        assertEquals("search-bound-hit", verdict.reason());
        assertFalse(verdict.evidenceComplete());
        assertTrue(frames.stream()
                .flatMap(frame -> frame.coverage().stream())
                .anyMatch(group -> group.coverage().truncated()));
    }

    @Test
    void parentThatStopsBeingReportedEndsItsVerdictWithoutInferringAReplacement() {
        final java.util.function.IntFunction<List<Pt>> superseded = asOf -> asOf < 40
                ? ScaleRelationFixtures.visibleAfter(ZIGZAG_PARENT, pivot -> PARENT_LAG).apply(asOf)
                : ScaleRelationFixtures.visibleAfter(ZIGZAG_PARENT.subList(0, 3), pivot -> PARENT_LAG).apply(asOf);
        final CorrectiveFamilyStudy study = new CorrectiveFamilyStudy(
                List.of(ScaleRelationFixtures.input("parent", ScaleRelationFixtures.scripted(superseded)),
                        ScaleRelationFixtures.input("child", ZIGZAG_CHILD, CHILD_LAG)),
                ScaleRelation.Policy.defaults(), ScaleRelationFixtures.passingRules(),
                ScaleRelationFixtures.identity(), ScaleRelationFixtures.PARTITIONS,
                List.of(spec(CorrectiveFamily.Profile.ZIGZAG)), CorrectiveFamily.DEFAULT_MAX_COMPOSITIONS);
        final List<CorrectiveFamilyStudy.Frame> frames = new ArrayList<>();
        study.run(ScaleRelationFixtures.series(BARS), 0, BARS - 1, DetectorReplays.uncached(), frames::add);

        final List<CorrectiveFamilyStudy.Event> ended = frames.stream()
                .flatMap(frame -> frame.events().stream())
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED)
                .toList();
        assertEquals(1, ended.size(), ended.toString());
        assertEquals(40, ended.get(0).asOfIndex());
        assertEquals(CorrectiveFamilyStudy.Reason.RETIRED, ended.get(0).reason());
        assertEquals(CorrectiveFamily.Status.VERIFIED, ended.get(0).verdict().status());
        assertTrue(finalVerdicts(frames).isEmpty(), "nothing replaces a parent that is no longer reported");
    }

    // ------------------------------------------------------- pure evaluation

    private static ConfirmedPivot pivot(final int index, final double price, final SwingPivotType type) {
        return new ConfirmedPivot(index, index + 1, DecimalNumFactory.getInstance().numOf(price), type);
    }

    private static ScaleRelationExtractor.Parent abcParent(final double a0, final double a1, final double b,
            final double c) {
        final TopologyCandidate candidate = new TopologyCandidate(TopologyGrammar.CORRECTIVE_3, WaveDirection.BEARISH,
                List.of(pivot(0, a0, SwingPivotType.HIGH), pivot(10, a1, SwingPivotType.LOW),
                        pivot(15, b, SwingPivotType.HIGH), pivot(30, c, SwingPivotType.LOW)));
        return new ScaleRelationExtractor.Parent(candidate, "parent-key", "parent-version");
    }

    private static ScaleRelation.Edge edge(final ScaleRelationExtractor.Parent parent, final int leg,
            final String key, final ScaleRelation.State state, final TopologyGrammar grammar) {
        final List<ConfirmedPivot> pivots = parent.candidate().pivots();
        return new ScaleRelation.Edge(key, key + "@v", "parent", "child", parent.key(), parent.version(),
                parent.candidate().grammar(), parent.candidate().direction(), leg, pivots.get(leg),
                pivots.get(leg + 1), state, grammar, "child-" + key, "child-version-" + key, List.of(),
                grammar.requiredPivots(), List.of(new ScaleRelation.Predicate("anchor", EvidenceState.PASS, "exact")),
                20 + leg);
    }

    private static List<ScaleRelation.Edge> supportedEdges(final ScaleRelationExtractor.Parent parent,
            final int first, final int second, final int third) {
        final List<TopologyGrammar> grammars = CorrectiveFamily.Profile.ZIGZAG.childGrammars();
        final int[] counts = { first, second, third };
        final List<ScaleRelation.Edge> edges = new ArrayList<>();
        for (int leg = 0; leg < counts.length; leg++) {
            for (int n = 0; n < counts[leg]; n++) {
                edges.add(edge(parent, leg, "e" + leg + "-" + n, ScaleRelation.State.SUBDIVISION_SUPPORTED,
                        grammars.get(leg)));
            }
        }
        return edges;
    }

    @Test
    void compositionsAreBoundedWithTheExactCountAndExplicitTruncation() {
        final ScaleRelationExtractor.Parent parent = abcParent(200, 150, 170, 120);
        final List<ScaleRelation.Edge> edges = supportedEdges(parent, 2, 2, 1);

        final CorrectiveFamily.Verdict bounded = CorrectiveFamily.evaluate(
                CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG), "parent", parent, edges, true, 3);
        assertEquals(CorrectiveFamily.Status.VERIFIED, bounded.status());
        assertEquals(4, bounded.compositionCount());
        assertEquals(3, bounded.compositions().size());
        assertTrue(bounded.compositionsTruncated());
        assertEquals(3, bounded.compositions().stream().map(CorrectiveFamily.Composition::key).distinct().count());

        final CorrectiveFamily.Verdict all = CorrectiveFamily.evaluate(
                CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG), "parent", parent, edges, true, 4);
        assertEquals(4, all.compositions().size());
        assertFalse(all.compositionsTruncated());
        assertEquals(bounded.compositions(), all.compositions().subList(0, 3));
        assertNotEquals(bounded.version(), all.version());
        assertEquals(bounded.key(), all.key());
    }

    @Test
    void evaluationIsDeterministicAndIndependentOfEdgeOrder() {
        final ScaleRelationExtractor.Parent parent = abcParent(200, 150, 170, 120);
        final List<ScaleRelation.Edge> edges = supportedEdges(parent, 2, 1, 2);
        final List<ScaleRelation.Edge> reversed = new ArrayList<>(edges);
        java.util.Collections.reverse(reversed);
        final CorrectiveFamily.Spec spec = CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG);

        assertEquals(CorrectiveFamily.evaluate(spec, "parent", parent, edges, true, 8),
                CorrectiveFamily.evaluate(spec, "parent", parent, reversed, true, 8));
    }

    @Test
    void edgesOfOtherParentsScalesOrGrammarsAreIgnored() {
        final ScaleRelationExtractor.Parent parent = abcParent(200, 150, 170, 120);
        final CorrectiveFamily.Spec spec = CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG);
        final List<ScaleRelation.Edge> edges = new ArrayList<>(supportedEdges(parent, 1, 1, 1));
        edges.add(edge(parent, 0, "wrong-grammar", ScaleRelation.State.CONFLICTING_EVIDENCE,
                TopologyGrammar.CORRECTIVE_3));

        final CorrectiveFamily.Verdict verdict = CorrectiveFamily.evaluate(spec, "parent", parent, edges, true, 8);
        assertEquals(CorrectiveFamily.Status.VERIFIED, verdict.status());
        assertEquals(1, verdict.legs().get(0).edges().size());
        assertEquals(CorrectiveFamily.Status.SHAPE_COMPATIBLE,
                CorrectiveFamily.evaluate(spec, "other-scale", parent, edges, true, 8).status());
    }

    @Test
    void conflictingPendingAndMissingLegsMapToDistinctLegStates() {
        final ScaleRelationExtractor.Parent parent = abcParent(200, 150, 170, 120);
        final CorrectiveFamily.Spec spec = CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG);
        final List<TopologyGrammar> grammars = CorrectiveFamily.Profile.ZIGZAG.childGrammars();

        final CorrectiveFamily.Verdict pending = CorrectiveFamily.evaluate(spec, "parent", parent,
                List.of(edge(parent, 0, "a", ScaleRelation.State.SUBDIVISION_SUPPORTED, grammars.get(0)),
                        edge(parent, 1, "b", ScaleRelation.State.PENDING_CONFIRMATION, grammars.get(1))),
                true, 8);
        assertEquals(List.of(CorrectiveFamily.LegState.SUPPORTED, CorrectiveFamily.LegState.PENDING,
                CorrectiveFamily.LegState.NO_EVIDENCE), pending.legs().stream().map(CorrectiveFamily.Leg::state).toList());
        assertEquals(CorrectiveFamily.Status.SHAPE_COMPATIBLE, pending.status());

        final CorrectiveFamily.Verdict conflicting = CorrectiveFamily.evaluate(spec, "parent", parent,
                List.of(edge(parent, 0, "a", ScaleRelation.State.CONFLICTING_EVIDENCE, grammars.get(0))), true, 8);
        assertEquals(CorrectiveFamily.Status.OUTSIDE_PROFILE, conflicting.status());
        assertEquals("child-subdivision", conflicting.reason());

        final CorrectiveFamily.Verdict incomplete = CorrectiveFamily.evaluate(spec, "parent", parent, List.of(),
                false, 8);
        assertEquals(CorrectiveFamily.Status.UNAVAILABLE, incomplete.status());
        assertEquals("search-bound-hit", incomplete.reason());
    }

    @Test
    void zeroAmplitudeOrMismatchedParentsAreNotJudgedByInventedRatios() {
        final CorrectiveFamily.Spec spec = CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG);
        final ScaleRelationExtractor.Parent flatA = abcParent(200, 200, 170, 120);
        final CorrectiveFamily.Verdict verdict = CorrectiveFamily.evaluate(spec, "parent", flatA, List.of(), true, 8);
        assertEquals(CorrectiveFamily.Status.UNAVAILABLE, verdict.status());
        assertEquals("envelope-unavailable", verdict.reason());

        final ScaleRelationExtractor.Parent motive = new ScaleRelationExtractor.Parent(
                new TopologyCandidate(TopologyGrammar.MOTIVE_5, WaveDirection.BULLISH,
                        List.of(pivot(0, 100, SwingPivotType.LOW), pivot(10, 120, SwingPivotType.HIGH),
                                pivot(15, 110, SwingPivotType.LOW), pivot(30, 150, SwingPivotType.HIGH),
                                pivot(36, 135, SwingPivotType.LOW), pivot(50, 170, SwingPivotType.HIGH))),
                "motive", "motive-version");
        assertThrows(IllegalArgumentException.class,
                () -> CorrectiveFamily.evaluate(spec, "parent", motive, List.of(), true, 8));
        assertThrows(IllegalArgumentException.class, () -> CorrectiveFamily.evaluate(spec, "parent",
                abcParent(200, 150, 170, 120), List.of(), true, 0));
    }

    // ------------------------------------------------------ declarations

    @Test
    void profileIdsAreStableAndUnsupportedVariantsStayUnsupported() {
        assertEquals(CorrectiveFamily.Profile.ZIGZAG, CorrectiveFamily.Profile.fromId("zigzag"));
        assertEquals(CorrectiveFamily.Profile.CONTRACTING_TRIANGLE,
                CorrectiveFamily.Profile.fromId("contracting-triangle"));
        for (final String unsupported : List.of("running-flat", "double-zigzag", "expanding-triangle", "diagonal",
                "Zigzag", "")) {
            final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> CorrectiveFamily.Profile.fromId(unsupported));
            assertTrue(error.getMessage().contains("expected one of"), error.getMessage());
        }
    }

    @Test
    void bandsExistOnlyWhereAProfileDefinesThem() {
        assertNull(CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.ZIGZAG).minRetracement());
        assertEquals(new BigDecimal("0.9"),
                CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.REGULAR_FLAT).minRetracement());
        assertThrows(IllegalArgumentException.class, () -> new CorrectiveFamily.Spec(CorrectiveFamily.Profile.ZIGZAG,
                new BigDecimal("0.5"), null));
        assertThrows(IllegalArgumentException.class, () -> new CorrectiveFamily.Spec(
                CorrectiveFamily.Profile.EXPANDED_FLAT, null, new BigDecimal("0.1")));
        for (final BigDecimal bad : List.of(BigDecimal.ZERO, new BigDecimal("1.01"), new BigDecimal("-0.1"))) {
            assertThrows(IllegalArgumentException.class, () -> new CorrectiveFamily.Spec(
                    CorrectiveFamily.Profile.REGULAR_FLAT, bad, new BigDecimal("0.1")));
        }
        assertThrows(IllegalArgumentException.class, () -> new CorrectiveFamily.Spec(
                CorrectiveFamily.Profile.REGULAR_FLAT, new BigDecimal("0.9"), BigDecimal.ZERO));
        assertEquals(CorrectiveFamily.Spec.defaults(CorrectiveFamily.Profile.REGULAR_FLAT),
                new CorrectiveFamily.Spec(CorrectiveFamily.Profile.REGULAR_FLAT, new BigDecimal("0.90"),
                        new BigDecimal("0.100")));
    }

    @Test
    void selectionsGroupBySubdivisionSignatureAndRejectDuplicatesOrNothing() {
        final List<CorrectiveFamilyStudy.Group> groups = CorrectiveFamilyStudy.groupSpecs(
                List.of(spec(CorrectiveFamily.Profile.EXPANDED_FLAT), spec(CorrectiveFamily.Profile.ZIGZAG),
                        spec(CorrectiveFamily.Profile.REGULAR_FLAT)));
        assertEquals(2, groups.size());
        assertEquals(List.of(CorrectiveFamily.Profile.EXPANDED_FLAT, CorrectiveFamily.Profile.REGULAR_FLAT),
                groups.stream()
                        .filter(group -> group.specs().size() == 2)
                        .findFirst()
                        .orElseThrow()
                        .specs()
                        .stream()
                        .map(CorrectiveFamily.Spec::profile)
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> CorrectiveFamilyStudy.groupSpecs(List.of()));
        assertThrows(IllegalArgumentException.class, () -> CorrectiveFamilyStudy
                .groupSpecs(List.of(spec(CorrectiveFamily.Profile.ZIGZAG), spec(CorrectiveFamily.Profile.ZIGZAG))));
    }
}
