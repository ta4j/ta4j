/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

/**
 * Outcome of one planner attempt: either the planned operation or the typed
 * {@link PlanDecline} explaining why the range was not lowered.
 *
 * <p>
 * Public visibility bridges core domain planners and the acceleration runtime
 * across Java packages, like {@link PlannedOperation}. Providers never observe
 * attempts; their boundary remains the primitive-only
 * {@link AccelerationRuntime.KernelRequest}.
 *
 * @param operation planned operation, {@code null} when declined
 * @param decline   decline reason, {@code null} when planned
 * @since 0.25.1
 */
public record PlanAttempt(PlannedOperation operation, PlanDecline decline) {

    /**
     * Validates an attempt.
     *
     * @since 0.25.1
     */
    public PlanAttempt {
        if ((operation == null) == (decline == null)) {
            throw new IllegalArgumentException("exactly one of operation or decline must be present");
        }
    }

    /**
     * Creates a planned attempt.
     *
     * @param operation planned operation
     * @return planned attempt
     * @since 0.25.1
     */
    public static PlanAttempt planned(PlannedOperation operation) {
        return new PlanAttempt(Objects.requireNonNull(operation, "operation must not be null"), null);
    }

    /**
     * Creates a declined attempt.
     *
     * @param decline decline reason
     * @return declined attempt
     * @since 0.25.1
     */
    public static PlanAttempt declined(PlanDecline decline) {
        return new PlanAttempt(null, Objects.requireNonNull(decline, "decline must not be null"));
    }

    /**
     * Returns whether the range was lowered into a kernel request.
     *
     * @return {@code true} when an operation is present
     * @since 0.25.1
     */
    public boolean isPlanned() {
        return operation != null;
    }
}
