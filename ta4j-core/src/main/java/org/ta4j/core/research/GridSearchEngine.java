/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Cartesian-product search.
 *
 * <p>
 * Iterates the declared domain space as an odometer: the first declared domain
 * is outermost (changes slowest) and the last declared domain is innermost
 * (changes fastest). The iteration order is therefore the same as the draft
 * research workflow's {@code collectCombinations} order. The engine reports
 * {@code SEARCH_SPACE_EXHAUSTED} only when every combination was proposed.
 * </p>
 */
final class GridSearchEngine extends SearchEngine {

    private final int[] indices;
    private long emitted;

    GridSearchEngine(List<DomainSpec> specs) {
        super(specs);
        indices = new int[specs.size()];
    }

    @Override
    List<ParameterResearch.ParameterSet> propose(int maxNew) {
        long totalSpace = totalSpace();
        if (emitted >= totalSpace) {
            terminate(ParameterResearch.TerminationReason.SEARCH_SPACE_EXHAUSTED);
            return List.of();
        }
        int batchSize = (int) Math.min(maxNew, totalSpace - emitted);
        List<ParameterResearch.ParameterSet> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            batch.add(parameterSet(indices));
            advance();
            emitted++;
        }
        if (emitted >= totalSpace) {
            terminate(ParameterResearch.TerminationReason.SEARCH_SPACE_EXHAUSTED);
        }
        return batch;
    }

    @Override
    void observe(ParameterResearch.EvaluatedCandidate evaluated) {
        // Grid search is stateless between batches.
    }

    @Override
    void finalizeObserved() {
        // Grid search keeps no between-batch iteration state.
    }

    private void advance() {
        for (int i = indices.length - 1; i >= 0; i--) {
            indices[i]++;
            if (indices[i] < specs().get(i).cardinality()) {
                return;
            }
            indices[i] = 0;
        }
    }
}
