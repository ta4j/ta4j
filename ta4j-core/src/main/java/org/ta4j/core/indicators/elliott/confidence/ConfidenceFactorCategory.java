/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

/**
 * Categories for confidence factors.
 *
 * <p>
 * Categories are used to group diagnostics and summarize which dimensions
 * contribute most to overall confidence.
 *
 * @since 0.22.2
 */
public enum ConfidenceFactorCategory {
    FIBONACCI, TIME, ALTERNATION, CHANNEL, COMPLETENESS, OTHER
}
