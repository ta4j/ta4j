/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

/**
 * Directional orientation of a topology hypothesis.
 */
enum WaveDirection {
    BULLISH, BEARISH;

    WaveDirection mirror() {
        return this == BULLISH ? BEARISH : BULLISH;
    }
}
