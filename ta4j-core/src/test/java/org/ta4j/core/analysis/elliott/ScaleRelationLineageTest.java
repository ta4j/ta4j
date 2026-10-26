/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DecimalNumFactory;

class ScaleRelationLineageTest {

    private static ScaleRelation.Edge edge(final String key, final String version, final String parentKey,
            final String parentScale) {
        final ConfirmedPivot start = new ConfirmedPivot(0, 1, DecimalNumFactory.getInstance().numOf(100),
                SwingPivotType.LOW);
        final ConfirmedPivot end = new ConfirmedPivot(10, 11, DecimalNumFactory.getInstance().numOf(120),
                SwingPivotType.HIGH);
        return new ScaleRelation.Edge(key, version, parentScale, "fine", parentKey, "v1", TopologyGrammar.MOTIVE_5,
                WaveDirection.BULLISH, 0, start, end, ScaleRelation.State.SUBDIVISION_SUPPORTED,
                TopologyGrammar.MOTIVE_5, null, null, List.of(), 0, List.of(), 11);
    }

    private static String ref(final String scale, final String candidateKey) {
        return ScaleRelationLineage.parentRef(scale, candidateKey);
    }

    private static ScaleRelationLineage.Observation observation(final Map<String, String> slots,
            final Set<String> invalidated, final Set<String> omitted, final boolean childPresent) {
        return new ScaleRelationLineage.Observation(slots, invalidated, omitted, edge -> childPresent);
    }

    @Test
    void firstSightingIsObservedAndUnchangedEdgesStaySilent() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        final ScaleRelation.Edge e = edge("k1", "k1@a", "p1", "coarse");

        final List<ScaleRelation.Event> first = lineage.advance(10, List.of(e),
                observation(Map.of("p1", "slot"), Set.of(), Set.of(), true));
        final List<ScaleRelation.Event> second = lineage.advance(11, List.of(e),
                observation(Map.of("p1", "slot"), Set.of(), Set.of(), true));

        assertEquals(1, first.size());
        assertEquals(ScaleRelation.Reason.OBSERVED, first.get(0).reason());
        assertTrue(second.isEmpty());
        assertEquals(1, lineage.activeCount());
    }

    @Test
    void changedVersionEmitsRevisedWithoutEndingTheEdge() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        lineage.advance(10, List.of(edge("k1", "k1@a", "p1", "coarse")),
                observation(Map.of("p1", "slot"), Set.of(), Set.of(), true));

        final List<ScaleRelation.Event> events = lineage.advance(11, List.of(edge("k1", "k1@b", "p1", "coarse")),
                observation(Map.of("p1", "slot"), Set.of(), Set.of(), true));

        assertEquals(1, events.size());
        assertEquals(ScaleRelation.Lifecycle.ACTIVE, events.get(0).lifecycle());
        assertEquals(ScaleRelation.Reason.REVISED, events.get(0).reason());
        assertEquals("k1@b", events.get(0).edge().version());
    }

    @Test
    void endReasonsDistinguishCapParentAndChildCauses() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        final ScaleRelation.Edge capped = edge("k1", "k1@a", "p1", "coarse");
        final ScaleRelation.Edge superseded = edge("k2", "k2@a", "p1", "coarse");
        final ScaleRelation.Edge withdrawn = edge("k3", "k3@a", "p1", "coarse");
        lineage.advance(10, List.of(capped, superseded, withdrawn),
                observation(Map.of(ref("coarse", "p1"), "slot"), Set.of(), Set.of(), true));

        final List<ScaleRelation.Event> events = lineage.advance(11, List.of(), new ScaleRelationLineage.Observation(
                Map.of(ref("coarse", "p1"), "slot"), Set.of(), Set.of("k1"), edge -> !edge.key().equals("k3")));

        final Map<String, ScaleRelation.Reason> reasons = new java.util.TreeMap<>();
        events.forEach(event -> reasons.put(event.edge().key(), event.reason()));
        assertEquals(ScaleRelation.Reason.CAP_RETIRED, reasons.get("k1"));
        assertEquals(ScaleRelation.Reason.SUPERSEDED, reasons.get("k2"));
        assertEquals(ScaleRelation.Reason.CHILD_WITHDRAWN, reasons.get("k3"));
        assertTrue(events.stream().allMatch(event -> event.lifecycle() == ScaleRelation.Lifecycle.ENDED));
        assertEquals(0, lineage.activeCount());
    }

    @Test
    void absentParentIsInvalidatedRevisedOrRetired() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        lineage.advance(10,
                List.of(edge("a", "a@1", "p1", "coarse"), edge("b", "b@1", "p2", "other"),
                        edge("c", "c@1", "p3", "third")),
                observation(Map.of(ref("coarse", "p1"), "slot-a", ref("other", "p2"), "slot-b", ref("third", "p3"),
                        "slot-c"), Set.of(), Set.of(), true));

        final List<ScaleRelation.Event> events = lineage.advance(11, List.of(),
                observation(Map.of(ref("coarse", "p9"), "slot-a"), Set.of("other"), Set.of(), true));

        final Map<String, ScaleRelation.Reason> reasons = new java.util.TreeMap<>();
        events.forEach(event -> reasons.put(event.edge().key(), event.reason()));
        assertEquals(ScaleRelation.Reason.PARENT_REVISED, reasons.get("a"));
        assertEquals(ScaleRelation.Reason.PARENT_INVALIDATED, reasons.get("b"));
        assertEquals(ScaleRelation.Reason.PARENT_RETIRED, reasons.get("c"));
        assertEquals(List.of("a", "b", "c"), events.stream().map(event -> event.edge().key()).toList());
        assertEquals("a@1", events.get(0).edge().version());
    }

    @Test
    void sameCandidateKeyOnAnotherScaleDoesNotKeepAWithdrawnParentPresent() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        lineage.advance(10, List.of(edge("a", "a@1", "shared", "coarse"), edge("b", "b@1", "shared", "middle")),
                observation(Map.of(ref("coarse", "shared"), "coarse|slot", ref("middle", "shared"), "middle|slot"),
                        Set.of(), Set.of(), true));

        final List<ScaleRelation.Event> events = lineage.advance(11, List.of(edge("b", "b@1", "shared", "middle")),
                observation(Map.of(ref("middle", "shared"), "middle|slot"), Set.of(), Set.of(), true));

        assertEquals(1, events.size());
        assertEquals("a", events.get(0).edge().key());
        assertEquals(ScaleRelation.Reason.PARENT_RETIRED, events.get(0).reason());
    }

    @Test
    void revisedSlotIsNotBorrowedFromAnotherScale() {
        final ScaleRelationLineage lineage = new ScaleRelationLineage();
        lineage.advance(10, List.of(edge("a", "a@1", "p1", "coarse")),
                observation(Map.of(ref("coarse", "p1"), "coarse|slot", ref("middle", "p1"), "middle|slot"), Set.of(),
                        Set.of(), true));

        final List<ScaleRelation.Event> events = lineage.advance(11, List.of(),
                observation(Map.of(ref("middle", "p1"), "coarse|slot"), Set.of(), Set.of(), true));

        assertEquals(ScaleRelation.Reason.PARENT_REVISED, events.get(0).reason());
        final List<ScaleRelation.Event> other = new ScaleRelationLineage().advance(11, List.of(),
                observation(Map.of(ref("middle", "p1"), "middle|slot"), Set.of(), Set.of(), true));
        assertTrue(other.isEmpty());
    }
}
