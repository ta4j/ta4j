/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal contract for candidate-proposal engines.
 *
 * <p>
 * The pipeline drives each engine strictly in batch cycles: it requests a
 * proposal batch of at most the remaining budget, evaluates every proposal, and
 * reports each outcome back through {@link #observe(EvaluatedCandidate)} before
 * requesting the next batch. Engines terminate by setting a
 * {@link ParameterResearch.TerminationReason}; a {@code null} reason with an
 * empty batch is resolved by the pipeline.
 * </p>
 *
 * <p>
 * The base class also tracks every distinct canonical proposal of the run, so
 * engines can avoid proposing seen candidates and the pipeline can detect
 * search-space exhaustion even when an engine keeps proposing already-seen
 * candidates instead of returning an empty batch.
 * </p>
 */
abstract class SearchEngine {

    /**
     * Practical upper bound for any single cohort an engine materializes in one
     * proposal cycle (a grid batch, a generation, or a swarm): configurable cohort
     * sizes beyond this bound are rejected at plan-build time, and budget-driven
     * batches are capped to it, so no proposal can pre-allocate an unbounded list
     * before the first evaluation.
     */
    static final int MAX_COHORT_SIZE = 65536;

    private final List<DomainSpec> specs;
    private final long totalSpace;
    private final Set<String> proposedIds = new HashSet<>();
    private ParameterResearch.TerminationReason terminationReason;
    private int iterationsCompleted;

    SearchEngine(List<DomainSpec> specs) {
        this.specs = List.copyOf(specs);
        long space = 1L;
        for (DomainSpec spec : specs) {
            space = saturatedMultiply(space, spec.cardinality());
        }
        totalSpace = space;
    }

    /**
     * Proposes up to {@code maxNew} candidates not yet seen this run.
     *
     * @param maxNew remaining budget for unique evaluations
     * @return proposal batch, possibly empty when the engine has terminated
     */
    abstract List<ParameterResearch.ParameterSet> propose(int maxNew);

    /**
     * Reports one evaluated or cached outcome back to the engine.
     *
     * @param evaluated evaluated candidate
     */
    abstract void observe(ParameterResearch.EvaluatedCandidate evaluated);

    /**
     * Finalizes the engine's most recently observed batch, if one has not been
     * finalized yet. Engines normally finalize the previous batch at the start of
     * the next {@link #propose(int)} call, but the pipeline invokes this directly
     * when the run stops between batches so the final evaluations are still
     * reflected in iteration and stagnation bookkeeping. Implementations must be
     * idempotent: calling this when no batch is pending must be a no-op.
     */
    abstract void finalizeObserved();

    /**
     * @return termination reason, or {@code null} when the engine can still propose
     */
    final ParameterResearch.TerminationReason terminationReason() {
        return terminationReason;
    }

    /**
     * @return number of completed engine iterations (0 for grid search)
     */
    final int iterationsCompleted() {
        return iterationsCompleted;
    }

    /**
     * @return engine domain specs
     */
    final List<DomainSpec> specs() {
        return specs;
    }

    /**
     * @return total number of canonical candidates in the declared search space
     *         ({@link Long#MAX_VALUE} when saturated)
     */
    final long totalSpace() {
        return totalSpace;
    }

    /**
     * @return {@code true} when every canonical candidate of the declared space has
     *         been proposed at least once, so no further proposal can yield a new
     *         evaluation
     */
    final boolean exhausted() {
        return proposedIds.size() >= totalSpace;
    }

    /**
     * @param canonicalId canonical candidate id
     * @return {@code true} when the canonical candidate was already proposed in
     *         this run
     */
    final boolean proposed(String canonicalId) {
        return proposedIds.contains(canonicalId);
    }

    /**
     * Builds the canonical id for one value index per dimension, without
     * registering a proposal.
     *
     * @param indices one index per dimension
     * @return canonical id
     */
    final String canonicalId(int[] indices) {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                id.append('|');
            }
            id.append(ParameterResearch.canonicalToken(specs.get(i).name(), specs.get(i).valueAt(indices[i])));
        }
        return id.toString();
    }

    /**
     * Records a terminal reason. The first recorded reason wins.
     *
     * @param reason termination reason
     */
    final void terminate(ParameterResearch.TerminationReason reason) {
        if (terminationReason == null) {
            terminationReason = reason;
        }
    }

    /**
     * Records one completed iteration.
     */
    final void completeIteration() {
        iterationsCompleted++;
    }

    /**
     * @return {@code true} when this engine retains the canonical id of every
     *         proposal for deduplication and exhaustion accounting
     */
    protected boolean tracksProposals() {
        return true;
    }

    /**
     * Builds a raw proposal from one canonical value index per dimension and
     * registers it as proposed when the engine tracks proposals.
     *
     * @param indices one index per dimension
     * @return proposal
     */
    final ParameterResearch.ParameterSet parameterSet(int[] indices) {
        List<ParameterResearch.ParameterValue> values = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            DomainSpec spec = specs.get(i);
            values.add(new ParameterResearch.ParameterValue(spec.name(), spec.valueAt(indices[i]), false, ""));
        }
        ParameterResearch.ParameterSet set = new ParameterResearch.ParameterSet(values);
        if (tracksProposals()) {
            proposedIds.add(set.stableId());
        }
        return set;
    }

    private static long saturatedMultiply(long a, long b) {
        if (a != 0 && b > Long.MAX_VALUE / a) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
