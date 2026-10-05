/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

/**
 * Explicit outcomes of experimental topology recognition.
 *
 * <p>
 * All states are first-class analytical results: the engine never forces a
 * count, and both {@code NO_MATCH} and {@code AMBIGUOUS} are valid answers.
 */
enum TopologyStatus {

    /** Too few confirmed pivots to attempt the requested grammar. */
    INSUFFICIENT_HISTORY,

    /** Enough history existed, but no candidate satisfied the grammar. */
    NO_MATCH,

    /** A partial prefix of the grammar is present but incomplete. */
    FORMING,

    /** Exactly one candidate satisfies the grammar under the tie policy. */
    COMPLETE,

    /** Bounded candidates remain materially tied under the tie policy. */
    AMBIGUOUS,

    /** A previously complete structure was violated by later confirmed pivots. */
    INVALIDATED
}
