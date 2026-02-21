/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

/**
 * Enumerates the structural and volume events referenced in Wyckoff analysis.
 *
 * @since 0.22.3
 */
public enum WyckoffEvent {

    /** Preliminary support. */
    PRELIMINARY_SUPPORT,
    /** Preliminary supply. */
    PRELIMINARY_SUPPLY,
    /** Selling climax. */
    SELLING_CLIMAX,
    /** Buying climax. */
    BUYING_CLIMAX,
    /** Automatic rally. */
    AUTOMATIC_RALLY,
    /** Automatic reaction. */
    AUTOMATIC_REACTION,
    /** Secondary test within the trading range. */
    SECONDARY_TEST,
    /** Sign of strength (SOS). */
    SIGN_OF_STRENGTH,
    /** Upthrust. */
    UPTHRUST,
    /** Upthrust after distribution (UTAD). */
    UPTHRUST_AFTER_DISTRIBUTION,
    /** Spring. */
    SPRING,
    /** Last point of support. */
    LAST_POINT_OF_SUPPORT,
    /** Last point of supply. */
    LAST_POINT_OF_SUPPLY,
    /** Range breakout. */
    RANGE_BREAKOUT,
    /** Range breakdown. */
    RANGE_BREAKDOWN
}
