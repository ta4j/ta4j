/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.frequency;

/**
 * Pair of indices describing a sampled interval.
 *
 * @param previousIndex the interval start index
 * @param currentIndex  the interval end index
 * @since 0.22.2
 */
public record IndexPair(int previousIndex, int currentIndex) {
}