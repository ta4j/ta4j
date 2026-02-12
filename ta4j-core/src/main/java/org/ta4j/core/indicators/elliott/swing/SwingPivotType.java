/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

/**
 * Pivot classification for swing points.
 *
 * <p>
 * Used by {@link SwingPivot} and swing detectors to label highs and lows.
 *
 * @since 0.22.2
 */
public enum SwingPivotType {
    HIGH, LOW;

    SwingPivotType opposite() {
        return this == HIGH ? LOW : HIGH;
    }
}
