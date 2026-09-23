/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import java.util.Objects;

/**
 * Typed reason a planner did not lower a request range.
 *
 * <p>
 * A decline is either permanent — the calculation is never accelerated, for
 * example a custom Monte Carlo method or a non-{@code DoubleNum} factory — or
 * transient: the range is ineligible right now, for example a warm-up prefix or
 * a resource limit a shorter range could satisfy. The runtime keeps transient
 * declines out of its permanent scalar-fallback set and retries at
 * {@link #retryFromIndex()}, so a warm-up read cannot disable acceleration for
 * the rest of the scope.
 *
 * @param permanent      {@code true} when the calculation is never accelerated
 * @param retryFromIndex first decision index that may be eligible again, or
 *                       {@code -1} for permanent declines
 * @param detail         concise operator-facing reason
 * @since 0.25.1
 */
public record PlanDecline(boolean permanent, int retryFromIndex, String detail) {

    private static final PlanDecline UNCLAIMED = new PlanDecline(true, -1,
            "calculation is not recognized by this planner");

    /**
     * Validates a decline.
     *
     * @since 0.25.1
     */
    public PlanDecline {
        Objects.requireNonNull(detail, "detail must not be null");
        if (permanent && retryFromIndex != -1) {
            throw new IllegalArgumentException("a permanent decline carries no retry index");
        }
        if (!permanent && retryFromIndex < 0) {
            throw new IllegalArgumentException("a transient decline requires a retry index");
        }
    }

    /**
     * Creates a permanent decline for a calculation the planner never lowers.
     *
     * @param detail concise operator-facing reason
     * @return permanent decline
     * @since 0.25.1
     */
    public static PlanDecline unsupported(String detail) {
        return new PlanDecline(true, -1, detail);
    }

    /**
     * Returns the permanent decline for a calculation the planner does not
     * recognize at all. The runtime never reports it as the reason a recognized
     * calculation stayed scalar; a planner that recognizes a calculation but cannot
     * lower it should decline with {@link #unsupported(String)} and a specific,
     * actionable reason instead.
     *
     * @return shared unclaimed decline
     * @since 0.25.1
     */
    public static PlanDecline unclaimed() {
        return UNCLAIMED;
    }

    /**
     * Creates a transient decline for a range that is not eligible yet.
     *
     * @param retryFromIndex first decision index that may be eligible again
     * @param detail         concise operator-facing reason
     * @return transient decline
     * @since 0.25.1
     */
    public static PlanDecline ineligible(int retryFromIndex, String detail) {
        return new PlanDecline(false, retryFromIndex, detail);
    }
}
