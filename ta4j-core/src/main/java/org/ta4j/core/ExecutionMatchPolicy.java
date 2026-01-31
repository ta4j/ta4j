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

    FIFO, LIFO, AVG_COST, SPECIFIC_ID
}
