/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

/**
 * Directional orientation of a topology hypothesis.
 *
 * <p>
 * Deliberately distinct from
 * {@code org.ta4j.core.indicators.elliott.ElliottTrendBias.Direction}: that
 * public enum carries {@code NEUTRAL} and {@code UNKNOWN} sentinels, which have
 * no leg-orientation meaning here — exhaustive direction iteration (for example
 * mirroring every hypothesis in the kernel) would iterate meaningless states
 * and silently corrupt match sets. A closed two-value type keeps the exhaustive
 * switch total and avoids coupling this experimental kernel to the public
 * indicator API surface.
 */
enum WaveDirection {
    BULLISH, BEARISH;

    WaveDirection mirror() {
        return this == BULLISH ? BEARISH : BULLISH;
    }
}
