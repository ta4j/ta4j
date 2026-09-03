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
 * kernel output. A planner returns {@code null} for calculations it does not
 * claim; unclaimed indicators fall back to the scalar lane without contacting
 * any provider.
 *
 * <p>
 * Core-internal extension point, not provider API: provider artifacts must
 * implement {@link AccelerationRuntime.Provider} instead.
 *
 * @since 0.24.2
 */
public interface OperationPlanner {

    /**
     * Plans acceleration for an indicator over {@code [fromInclusive,
     * toInclusive]}, or returns {@code null} when the calculation is not claimed.
     *
     * @param indicator     indicator requesting acceleration
     * @param fromInclusive first decision index
     * @param toInclusive   last decision index
     * @param factory       owning factory used for scalar baselines and decoding
     * @return planned operation, or {@code null} when unclaimed
     * @since 0.24.2
     */
    PlannedOperation plan(Indicator<?> indicator, int fromInclusive, int toInclusive, NumFactory factory);
}
