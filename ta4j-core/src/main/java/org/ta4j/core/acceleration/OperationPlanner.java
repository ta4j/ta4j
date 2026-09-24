/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NumFactory;

/**
 * Core-internal lowering of one supported calculation into a versioned
 * {@link AccelerationRuntime.KernelRequest}.
 *
 * <p>
 * Planners observe domain graphs so providers never have to. They snapshot
 * every input the kernel needs into primitives, state the numeric and
 * determinism contracts, estimate the scalar baseline and peak device memory,
 * and supply the core-owned decoder that reconstructs domain values from raw
 * kernel output. A planner answers with a {@link PlanAttempt}: an operation for
 * the range it claims, or a typed {@link PlanDecline}. Declining a range never
 * contacts a provider; unclaimed and permanently unsupported calculations fall
 * back to the scalar lane for the rest of the scope, while a transient decline
 * only defers the range until {@link PlanDecline#retryFromIndex()}.
 *
 * <p>
 * Core-internal extension point, not provider API: provider artifacts must
 * implement {@link AccelerationRuntime.Provider} instead.
 *
 * @since 0.25.1
 */
public interface OperationPlanner {

    /**
     * Plans acceleration for an indicator over {@code [fromInclusive,
     * toInclusive]}, or explains why the range is not lowered.
     *
     * @param indicator        indicator requesting acceleration
     * @param fromInclusive    first decision index
     * @param toInclusive      last decision index
     * @param factory          owning factory used for scalar baselines and decoding
     * @param memoryLimitBytes scope-captured memory ceiling, checked before
     *                         allocating snapshot or output buffers
     * @return planned operation or typed decline, never {@code null}
     * @since 0.25.1
     */
    PlanAttempt plan(Indicator<?> indicator, int fromInclusive, int toInclusive, NumFactory factory,
            long memoryLimitBytes);
}
