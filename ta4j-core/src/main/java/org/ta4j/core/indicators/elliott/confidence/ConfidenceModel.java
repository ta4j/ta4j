/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.List;

import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;

/**
 * Selects a confidence profile for scoring Elliott scenarios.
 *
 * @since 0.22.2
 */
public interface ConfidenceModel {

    /**
     * Scores a scenario with the appropriate profile.
     *
     * @param swings       swing sequence
     * @param phase        current phase
     * @param channel      Elliott channel (nullable)
     * @param scenarioType scenario type classification
     * @return confidence breakdown
     * @since 0.22.2
     */
    ElliottConfidenceBreakdown score(List<ElliottSwing> swings, ElliottPhase phase, ElliottChannel channel,
            ScenarioType scenarioType);
}
