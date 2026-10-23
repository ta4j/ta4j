/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.CHILD;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.CHILD_LAG;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.PARENT;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.PARENT_COMPLETE;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.PARENT_LAG;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.activeAt;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.events;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.input;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.pt;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.replacing;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.run;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.scripted;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.series;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.study;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.visibleAfter;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.with;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.without;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.ScaleRelationFixtures.Pt;

class ScaleRelationStudyTest {

    private static final ScaleRelation.Policy DEFAULTS = ScaleRelation.Policy.defaults();

    private static List<ScaleRelationStudy.ScaleInput> twoScales(final List<Pt> parent, final List<Pt> child) {
        return List.of(input("coarse", parent, PARENT_LAG), input("fine", child, CHILD_LAG));
    }

    private static ScaleRelation.Edge leg(final List<ScaleRelation.Edge> edges, final int leg) {
        final List<ScaleRelation.Edge> matching = edges.stream().filter(edge -> edge.parentLeg() == leg).toList();
        assertEquals(1, matching.size(), "edges for leg " + leg + ": " + matching);
        return matching.get(0);
    }

    @Test
    void nestedFixtureAppearsWhenAllEvidenceIsConfirmed() {
        final List<ScaleRelationStudy.Frame> frames = run(study(twoScales(PARENT, CHILD), DEFAULTS), 60, 59);

        assertEquals(PARENT_COMPLETE, frames.get(0).asOfIndex(), "no relation exists before the parent completes");
        final List<ScaleRelation.Edge> edges = activeAt(frames, 59);
        assertEquals(5, edges.size());
        for (int leg = 0; leg < 5; leg++) {
            final ScaleRelation.Edge edge = leg(edges, leg);
            assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, edge.state());
            assertEquals(PARENT_COMPLETE, edge.availableAt());
            assertEquals(leg % 2 == 0 ? TopologyGrammar.MOTIVE_5 : TopologyGrammar.CORRECTIVE_3, edge.childGrammar());
            assertEquals("coarse", edge.parentScale());
            assertEquals("fine", edge.childScale());
        }
        assertEquals(6, leg(edges, 0).childPivotCount());
        assertEquals(4, leg(edges, 1).childPivotCount());
        assertEquals(0, leg(edges, 0).parentStart().pivotIndex());
        assertEquals(10, leg(edges, 0).parentEnd().pivotIndex());
    }

    @Test
    void relationWaitsForSlowerChildEvidence() {
        // The child's final anchor is confirmed 6 bars late: the parent leg ending
        // there is only pending until bar 56 and never earlier counted as supported.
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(input("coarse", PARENT, PARENT_LAG),
                input("fine", scripted(visibleAfter(CHILD, pivot -> pivot.index() == 50 ? 6 : CHILD_LAG))));
        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS), 60, 59);

        final List<ScaleRelation.Edge> early = activeAt(frames, 55);
        assertEquals(ScaleRelation.State.PENDING_CONFIRMATION, leg(early, 4).state());
        assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, leg(early, 3).state());
        for (final ScaleRelationStudy.Frame frame : frames) {
            if (frame.asOfIndex() < 56) {
                assertTrue(frame.events()
                        .stream()
                        .noneMatch(event -> event.edge().parentLeg() == 4
                                && event.edge().state() == ScaleRelation.State.SUBDIVISION_SUPPORTED));
            }
        }
        final ScaleRelation.Edge settled = leg(activeAt(frames, 56), 4);
        assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, settled.state());
        assertEquals(56, settled.availableAt());
    }

    @Test
    void appendedFutureBarsDoNotChangeEarlierFrames() {
        final List<ScaleRelationStudy.ScaleInput> inputs = twoScales(PARENT, CHILD);
        final List<ScaleRelationStudy.Frame> prefix = run(study(inputs, DEFAULTS), 56, 55);
        final List<ScaleRelationStudy.Frame> appended = run(study(inputs, DEFAULTS), 60, 55);
        final List<ScaleRelationStudy.Frame> longer = run(study(inputs, DEFAULTS), 60, 59);

        assertEquals(json(prefix), json(appended));
        assertEquals(json(prefix), json(longer.stream().filter(frame -> frame.asOfIndex() <= 55).toList()));
    }

    @Test
    void missingChildAnchorIsNeverSnappedToANeighbour() {
        // The child has a pivot one bar before the parent's bar-10 anchor.
        final List<Pt> shifted = replacing(CHILD, 10, pt(9, 119, 'H'));
        final List<ScaleRelation.Edge> edges = activeAt(run(study(twoScales(PARENT, shifted), DEFAULTS), 60, 59), 59);

        assertEquals(ScaleRelation.State.NOT_NESTED, leg(edges, 0).state());
        assertEquals(ScaleRelation.State.CONTAINED_ONLY, leg(edges, 1).state());
        assertTrue(edges.stream()
                .filter(edge -> edge.parentLeg() <= 1)
                .allMatch(edge -> edge.childCandidateKey() == null));
        assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, leg(edges, 2).state());
    }

    @Test
    void wrongPriceAtAnAnchorIndexIsNotNested() {
        final List<Pt> repriced = replacing(CHILD, 30, pt(30, 149, 'H'));
        final List<ScaleRelation.Edge> edges = activeAt(run(study(twoScales(PARENT, repriced), DEFAULTS), 60, 59), 59);

        assertEquals(ScaleRelation.State.NOT_NESTED, leg(edges, 2).state());
        assertEquals(ScaleRelation.State.NOT_NESTED, leg(edges, 3).state());
        assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, leg(edges, 0).state());
    }

    @Test
    void everyValidDecompositionIsKeptWhenInteriorAnchorsMaySkip() {
        final List<Pt> crowded = with(CHILD, pt(4, 110, 'H'), pt(5, 105, 'L'));
        final ScaleRelation.Policy skipping = DEFAULTS.withInterior(ScaleRelation.Interior.ALLOW_SKIPPED);

        final List<ScaleRelation.Edge> edges = activeAt(run(study(twoScales(PARENT, crowded), skipping), 60, 59), 59);
        final List<ScaleRelation.Edge> firstLeg = edges.stream().filter(edge -> edge.parentLeg() == 0).toList();

        assertTrue(firstLeg.size() >= 2, "expected several decompositions but got " + firstLeg.size());
        assertEquals(firstLeg.size(), firstLeg.stream().map(ScaleRelation.Edge::key).distinct().count());
        assertEquals(firstLeg.size(), firstLeg.stream().map(ScaleRelation.Edge::childCandidateKey).distinct().count());
        assertTrue(firstLeg.stream().allMatch(edge -> edge.state() == ScaleRelation.State.SUBDIVISION_SUPPORTED));
        assertTrue(firstLeg.stream().allMatch(edge -> edge.parentStart().pivotIndex() == 0));
        assertTrue(firstLeg.stream().allMatch(edge -> edge.childPivots().get(0).pivotIndex() == 0));
        assertTrue(firstLeg.stream()
                .allMatch(edge -> edge.childPivots().get(edge.childPivots().size() - 1).pivotIndex() == 10));
    }

    @Test
    void contiguousInteriorRejectsExtraChildPivots() {
        final List<Pt> crowded = with(CHILD, pt(4, 110, 'H'), pt(5, 105, 'L'));

        final List<ScaleRelation.Edge> edges = activeAt(run(study(twoScales(PARENT, crowded), DEFAULTS), 60, 59), 59);

        assertEquals(ScaleRelation.State.CONFLICTING_EVIDENCE, leg(edges, 0).state());
    }

    @Test
    void decompositionSearchIsBoundedAndReportsTruncation() {
        final List<Pt> crowded = with(CHILD, pt(4, 110, 'H'), pt(5, 105, 'L'));
        final ScaleRelation.Policy bounded = DEFAULTS.withInterior(ScaleRelation.Interior.ALLOW_SKIPPED)
                .withDecompositionBounds(1, 20_000);

        final List<ScaleRelationStudy.Frame> frames = run(study(twoScales(PARENT, crowded), bounded), 60, 59);

        assertEquals(1, activeAt(frames, 59).stream().filter(edge -> edge.parentLeg() == 0).count());
        final ScaleRelation.Coverage coverage = frames.get(frames.size() - 1).coverage();
        assertTrue(coverage.decompositionLegsTruncated() > 0);
        assertTrue(coverage.incomplete());
    }

    @Test
    void truncatedSearchWithoutACandidateIsUnavailableNotNegative() {
        final List<Pt> crowded = with(CHILD, pt(4, 110, 'H'), pt(5, 105, 'L'));
        final ScaleRelation.Policy starved = DEFAULTS.withInterior(ScaleRelation.Interior.ALLOW_SKIPPED)
                .withDecompositionBounds(8, 1);

        final List<ScaleRelationStudy.Frame> frames = run(study(twoScales(PARENT, crowded), starved), 60, 59);

        final List<ScaleRelation.Edge> firstLeg = activeAt(frames, 59).stream()
                .filter(edge -> edge.parentLeg() == 0)
                .toList();
        assertFalse(firstLeg.isEmpty());
        for (final ScaleRelation.Edge edge : firstLeg) {
            assertEquals(ScaleRelation.State.CONTAINED_ONLY, edge.state());
            assertTrue(
                    edge.predicates()
                            .stream()
                            .anyMatch(p -> p.id().equals("interior-shape") && p.state() == EvidenceState.UNAVAILABLE),
                    edge.predicates().toString());
        }
        assertTrue(frames.get(frames.size() - 1).coverage().decompositionLegsTruncated() > 0);
    }

    @Test
    void anchorWitnessConfirmationBoundsWhenARelationBecomesAvailable() {
        // The child pivot on the parent's bar-30 anchor carries the wrong price and
        // is confirmed only at bar 55, after the parent completed at 52.
        final List<Pt> repriced = replacing(CHILD, 30, pt(30, 149, 'H'));
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(input("coarse", PARENT, PARENT_LAG),
                input("fine", scripted(visibleAfter(repriced, pivot -> pivot.index() == 30 ? 25 : CHILD_LAG))));

        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS), 60, 59);

        final ScaleRelation.Edge edge = leg(activeAt(frames, 59), 2);
        assertEquals(ScaleRelation.State.NOT_NESTED, edge.state());
        assertEquals(55, edge.availableAt());
        assertTrue(edge.childPivots().stream().anyMatch(pivot -> pivot.pivotIndex() == 30));
    }

    @Test
    void retiredParentOnOneScaleDoesNotEndRelationsOfAnotherScaleSharingItsPlacement() {
        // "coarse" and "middle" confirm the same pivots, so their parent candidates
        // share a key; only "coarse" later loses its final pivot.
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(input("coarse", scripted(
                asOf -> visibleAfter(asOf >= 58 ? without(PARENT, 50) : PARENT, pivot -> PARENT_LAG).apply(asOf))),
                input("middle", PARENT, PARENT_LAG), input("fine", CHILD, CHILD_LAG));

        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS.withEdgeCap(20)), 60, 59);

        final List<ScaleRelation.Event> ended = events(frames).stream()
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED)
                .toList();
        assertFalse(ended.isEmpty());
        assertTrue(ended.stream().allMatch(event -> event.edge().parentScale().equals("coarse")), ended.toString());
        assertTrue(ended.stream().allMatch(event -> event.reason() == ScaleRelation.Reason.PARENT_RETIRED),
                ended.toString());
        assertTrue(activeAt(frames, 59).stream().anyMatch(edge -> edge.parentScale().equals("middle")));
    }

    @Test
    void failingChildRuleIsConflictingEvidence() {
        final ScaleRelationStudy failing = new ScaleRelationStudy(twoScales(PARENT, CHILD), DEFAULTS,
                List.of(ScaleRelationFixtures.rule("synthetic-fail", false)), ScaleRelationFixtures.identity(),
                ScaleRelationFixtures.PARTITIONS);

        final List<ScaleRelation.Edge> edges = activeAt(run(failing, 60, 59), 59);

        assertEquals(5, edges.size());
        assertTrue(edges.stream().allMatch(edge -> edge.state() == ScaleRelation.State.CONFLICTING_EVIDENCE));
        assertTrue(edges.stream()
                .allMatch(edge -> edge.predicates().stream().anyMatch(p -> p.id().equals("rule:synthetic-fail"))));
    }

    @Test
    void invalidDeclarationsAreRejectedBeforeAnyRun() {
        final ScaleRelationStudy.ScaleInput coarse = input("coarse", PARENT, PARENT_LAG);
        final ScaleRelationStudy.ScaleInput fine = input("fine", CHILD, CHILD_LAG);

        assertThrows(IllegalArgumentException.class, () -> study(List.of(coarse), DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> study(List.of(coarse, coarse), DEFAULTS));
        assertThrows(IllegalArgumentException.class,
                () -> study(List.of(coarse, fine, input("finer", CHILD, 1), input("finest", CHILD, 1)), DEFAULTS));
    }

    @Test
    void absentParentKeepsChildEvidenceAndEndsEdgesAsRetired() {
        // The parent's trailing pivot disappears at bar 56 before any successor.
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(
                input("coarse",
                        scripted(asOf -> asOf >= 56 ? without(PARENT, 50)
                                : visibleAfter(PARENT, pivot -> PARENT_LAG).apply(asOf))),
                input("fine", CHILD, CHILD_LAG));
        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS), 60, 59);

        assertEquals(5, activeAt(frames, 55).size());
        assertTrue(activeAt(frames, 59).isEmpty());
        final List<ScaleRelation.Event> ended = events(frames).stream()
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED)
                .toList();
        assertEquals(5, ended.size());
        assertTrue(ended.stream().allMatch(event -> event.reason() == ScaleRelation.Reason.PARENT_RETIRED));
        assertTrue(ended.stream().allMatch(event -> event.asOfIndex() == 56));
        // The retired edge keeps the version and child evidence it was last active
        // with.
        final List<ScaleRelation.Edge> last = activeAt(frames, 55);
        for (final ScaleRelation.Event event : ended) {
            final ScaleRelation.Edge before = last.stream()
                    .filter(edge -> edge.key().equals(event.edge().key()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(before, event.edge());
        }
        // The fine scale's own tape is untouched by the parent's retirement.
        final ConfirmationTracker.CausalReplay child = new ConfirmationTracker(scripted(CHILD, CHILD_LAG))
                .observeReplay(series(60), 59);
        assertEquals(child.at(55), child.at(59));
    }

    @Test
    void absentParentScaleProducesNoRelationsAndNoFailure() {
        final List<ScaleRelationStudy.Frame> frames = run(study(twoScales(List.of(), CHILD), DEFAULTS), 60, 59);

        assertTrue(frames.isEmpty());
    }

    @Test
    void revisedParentEndsOldEdgesWithoutRewritingThem() {
        // The parent's last pivot is revised from (50, 170) to (53, 175) at bar 56.
        final List<Pt> revised = replacing(PARENT, 50, pt(53, 175, 'H'));
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(
                input("coarse",
                        scripted(asOf -> visibleAfter(asOf >= 56 ? revised : PARENT, pivot -> PARENT_LAG).apply(asOf))),
                input("fine", CHILD, CHILD_LAG));
        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS), 60, 59);

        final List<ScaleRelation.Edge> before = activeAt(frames, 55);
        assertEquals(5, before.size());
        final List<ScaleRelation.Event> ended = events(frames).stream()
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED)
                .toList();
        assertEquals(5, ended.size());
        assertTrue(ended.stream().allMatch(event -> event.reason() == ScaleRelation.Reason.PARENT_REVISED),
                ended.toString());
        assertEquals(before.stream().map(ScaleRelation.Edge::version).collect(Collectors.toSet()),
                ended.stream().map(event -> event.edge().version()).collect(Collectors.toSet()));
        final Set<String> revisedParents = activeAt(frames, 59).stream()
                .map(ScaleRelation.Edge::parentCandidateKey)
                .collect(Collectors.toSet());
        assertEquals(1, revisedParents.size());
        assertFalse(revisedParents.contains(before.get(0).parentCandidateKey()));
    }

    @Test
    void withdrawnChildPivotEndsOnlyTheEdgesThatReliedOnIt() {
        // The fine scale's trailing pivot at bar 50 is re-priced at bar 58.
        final List<Pt> repriced = replacing(CHILD, 50, pt(50, 168, 'H'));
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(input("coarse", PARENT, PARENT_LAG), input("fine",
                scripted(asOf -> visibleAfter(asOf >= 58 ? repriced : CHILD, pivot -> CHILD_LAG).apply(asOf))));
        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS), 60, 59);

        final List<ScaleRelation.Edge> before = activeAt(frames, 57);
        assertEquals(ScaleRelation.State.SUBDIVISION_SUPPORTED, leg(before, 4).state());
        final List<ScaleRelation.Event> ended = events(frames).stream()
                .filter(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED)
                .toList();
        assertEquals(1, ended.size());
        assertEquals(ScaleRelation.Reason.CHILD_WITHDRAWN, ended.get(0).reason());
        assertEquals(leg(before, 4), ended.get(0).edge());
        final List<ScaleRelation.Edge> after = activeAt(frames, 59);
        assertEquals(ScaleRelation.State.NOT_NESTED, leg(after, 4).state());
        for (int unaffected = 0; unaffected < 4; unaffected++) {
            assertEquals(leg(before, unaffected), leg(after, unaffected));
        }
    }

    @Test
    void edgeCapRetainsNewestEvidenceAndReportsExactCounts() {
        final ScaleRelation.Policy capped = DEFAULTS.withEdgeCap(3);
        final List<ScaleRelationStudy.Frame> frames = run(study(twoScales(PARENT, CHILD), capped), 60, 59);

        final ScaleRelation.Coverage coverage = frames.get(0).coverage();
        assertEquals(5, coverage.edgesGenerated());
        assertEquals(3, coverage.edgesRetained());
        assertEquals(2, coverage.edgesOmitted());
        assertEquals(3, coverage.edgeCap());
        assertTrue(coverage.truncated());
        assertEquals(3, activeAt(frames, 59).size());
        assertEquals(json(frames), json(run(study(twoScales(PARENT, CHILD), capped), 60, 59)));
    }

    @Test
    void threeScaleChainRelatesEachAdjacentPairWithinTheCap() {
        final List<Pt> middle = with(PARENT, pt(2, 108, 'H'), pt(3, 104, 'L'), pt(7, 116, 'H'), pt(8, 112, 'L'));
        final List<ScaleRelationStudy.ScaleInput> inputs = List.of(input("coarse", PARENT, PARENT_LAG),
                input("middle", middle, CHILD_LAG), input("fine", CHILD, CHILD_LAG));

        final List<ScaleRelationStudy.Frame> frames = run(study(inputs, DEFAULTS.withEdgeCap(4)), 60, 59);

        assertFalse(frames.isEmpty());
        for (final ScaleRelationStudy.Frame frame : frames) {
            assertTrue(frame.coverage().edgesRetained() <= 4);
            assertEquals(frame.coverage().edgesGenerated(),
                    frame.coverage().edgesRetained() + frame.coverage().edgesOmitted());
        }
        final Set<String> pairs = events(frames).stream()
                .map(event -> event.edge().parentScale() + ">" + event.edge().childScale())
                .collect(Collectors.toSet());
        assertTrue(pairs.contains("coarse>middle"), pairs.toString());
        assertTrue(pairs.contains("middle>fine"), pairs.toString());
        assertFalse(pairs.contains("coarse>fine"));
    }

    @Test
    void lineageVersionsAreStableAcrossIdenticalRuns() {
        final List<ScaleRelation.Edge> first = activeAt(run(study(twoScales(PARENT, CHILD), DEFAULTS), 60, 59), 59);
        final List<ScaleRelation.Edge> second = activeAt(run(study(twoScales(PARENT, CHILD), DEFAULTS), 60, 59), 59);
        final List<Pt> altered = replacing(CHILD, 7, pt(7, 117, 'H'));
        final List<ScaleRelation.Edge> changed = activeAt(run(study(twoScales(PARENT, altered), DEFAULTS), 60, 59), 59);

        assertEquals(first, second);
        assertNotEquals(leg(first, 0).version(), leg(changed, 0).version());
        assertEquals(leg(first, 1).version(), leg(changed, 1).version());
    }

    private static String json(final List<ScaleRelationStudy.Frame> frames) {
        return frames.stream()
                .map(frame -> ElliottResearchRelations.frameJson("dataset", frame).toString())
                .collect(Collectors.joining("\n"));
    }
}
