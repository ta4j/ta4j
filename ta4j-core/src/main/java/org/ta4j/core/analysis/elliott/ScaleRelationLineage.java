/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Tracks the active relation edges across observations and emits an immutable
 * {@link ScaleRelation.Event} whenever an edge appears, changes version, or
 * stops being active.
 *
 * <p>
 * Nothing is rewritten: a revised edge keeps its key and gets a new version, and
 * an ended edge keeps its last version. The end reason distinguishes a parent
 * that was revised, invalidated, or retired from a child pivot that was
 * withdrawn and from a retained-edge cap that merely stopped listing the edge.
 * Disappearance of a parent never removes child evidence; the child scale's own
 * tape and candidates are untouched by this class.
 */
final class ScaleRelationLineage {

    private final Map<String, ScaleRelation.Edge> active = new TreeMap<>();
    private final Map<String, String> parentSlotByEdgeKey = new TreeMap<>();

    /**
     * What the extraction layer knows about the current observation.
     *
     * @param currentParentSlots    slot of every parent candidate present now,
     *                              keyed by parent candidate key
     * @param invalidatedScales     parent scales whose topology analysis reported
     *                              an explicit invalidation at this observation
     * @param omittedEdgeKeys       keys of edges generated but dropped by the cap
     * @param childEvidencePresent  whether an edge's stored child pivots are all
     *                              still confirmed, unchanged, on the child tape
     */
    record Observation(Map<String, String> currentParentSlots, Set<String> invalidatedScales,
            Set<String> omittedEdgeKeys, Predicate<ScaleRelation.Edge> childEvidencePresent) {
        Observation {
            Objects.requireNonNull(currentParentSlots, "currentParentSlots");
            Objects.requireNonNull(invalidatedScales, "invalidatedScales");
            Objects.requireNonNull(omittedEdgeKeys, "omittedEdgeKeys");
            Objects.requireNonNull(childEvidencePresent, "childEvidencePresent");
        }
    }

    /**
     * Folds one observation into the lineage.
     *
     * @param asOfIndex   observation bar index
     * @param retained    edges active now, after the cap
     * @param observation context used to explain disappearances
     * @return events in deterministic order: ended edges by key, then active edges
     *         by key
     */
    List<ScaleRelation.Event> advance(final int asOfIndex, final List<ScaleRelation.Edge> retained,
            final Observation observation) {
        final Map<String, ScaleRelation.Edge> next = new TreeMap<>();
        for (final ScaleRelation.Edge edge : retained) {
            next.put(edge.key(), edge);
        }
        final List<ScaleRelation.Event> events = new ArrayList<>();
        for (final Map.Entry<String, ScaleRelation.Edge> entry : active.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                final ScaleRelation.Edge ended = entry.getValue();
                events.add(new ScaleRelation.Event(asOfIndex, ScaleRelation.Lifecycle.ENDED,
                        endReason(ended, observation), ended));
            }
        }
        for (final Map.Entry<String, ScaleRelation.Edge> entry : next.entrySet()) {
            final ScaleRelation.Edge previous = active.get(entry.getKey());
            final ScaleRelation.Edge edge = entry.getValue();
            if (previous == null) {
                events.add(new ScaleRelation.Event(asOfIndex, ScaleRelation.Lifecycle.ACTIVE,
                        ScaleRelation.Reason.OBSERVED, edge));
            } else if (!previous.version().equals(edge.version())) {
                events.add(new ScaleRelation.Event(asOfIndex, ScaleRelation.Lifecycle.ACTIVE,
                        ScaleRelation.Reason.REVISED, edge));
            }
        }
        for (final ScaleRelation.Event event : events) {
            if (event.lifecycle() == ScaleRelation.Lifecycle.ENDED) {
                parentSlotByEdgeKey.remove(event.edge().key());
            }
        }
        for (final ScaleRelation.Edge edge : next.values()) {
            final String slot = observation.currentParentSlots().get(edge.parentCandidateKey());
            if (slot != null) {
                parentSlotByEdgeKey.put(edge.key(), slot);
            }
        }
        active.clear();
        active.putAll(next);
        return List.copyOf(events);
    }

    /** @return number of edges active after the last observation */
    int activeCount() {
        return active.size();
    }

    private ScaleRelation.Reason endReason(final ScaleRelation.Edge ended, final Observation observation) {
        if (observation.currentParentSlots().containsKey(ended.parentCandidateKey())) {
            if (observation.omittedEdgeKeys().contains(ended.key())) {
                return ScaleRelation.Reason.CAP_RETIRED;
            }
            return observation.childEvidencePresent().test(ended) ? ScaleRelation.Reason.SUPERSEDED
                    : ScaleRelation.Reason.CHILD_WITHDRAWN;
        }
        if (observation.invalidatedScales().contains(ended.parentScale())) {
            return ScaleRelation.Reason.PARENT_INVALIDATED;
        }
        final String slot = parentSlotByEdgeKey.get(ended.key());
        if (slot != null && observation.currentParentSlots().containsValue(slot)) {
            return ScaleRelation.Reason.PARENT_REVISED;
        }
        return ScaleRelation.Reason.PARENT_RETIRED;
    }
}
