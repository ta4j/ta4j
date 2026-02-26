/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;

import org.ta4j.core.indicators.elliott.ElliottSwing;

/**
 * Post-processes swing lists to remove noise or apply custom constraints.
 *
 * <p>
 * Implement this interface to plug additional filtering into
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner}.
 *
 * @since 0.22.2
 */
@FunctionalInterface
public interface SwingFilter {

    /**
     * Filters the supplied swing list.
     *
     * @param swings detected swings
     * @return filtered swings (immutable)
     * @since 0.22.2
     */
    List<ElliottSwing> filter(List<ElliottSwing> swings);
}
