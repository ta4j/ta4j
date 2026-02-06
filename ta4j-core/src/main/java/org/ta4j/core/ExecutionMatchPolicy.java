/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

/**
 * Matching policy for pairing exits against open position lots.
 *
 * @since 0.22.2
 */
public enum ExecutionMatchPolicy {

    /**
     * First-in, first-out matching; exits close the earliest open lots.
     *
     * @since 0.22.2
     */
    FIFO,

    /**
     * Last-in, first-out matching; exits close the most recent open lots.
     *
     * @since 0.22.2
     */
    LIFO,

    /**
     * Average-cost matching; entries are merged into a single lot and exits use the
     * weighted average cost basis.
     *
     * @since 0.22.2
     */
    AVG_COST,

    /**
     * Match exits to a specific lot using correlationId or orderId; exit amounts
     * must not exceed the matched lot.
     *
     * @since 0.22.2
     */
    SPECIFIC_ID
}
