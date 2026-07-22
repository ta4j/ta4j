/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

/**
 * Indicator value paired with its absolute bar index.
 *
 * @param index bar index
 * @param value indicator value
 * @param <T>   value type
 * @since 0.23.1
 */
public record IndexedIndicatorValue<T>(int index, T value) {
}
