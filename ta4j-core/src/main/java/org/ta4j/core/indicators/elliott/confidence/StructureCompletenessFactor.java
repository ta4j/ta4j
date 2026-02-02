/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.num.Num;

/**
 * Scores wave structure completeness.
 *
 * @since 0.22.2
 */
public final class StructureCompletenessFactor implements ConfidenceFactor {

    private final ElliottConfidenceScorer scorer;

    /**
     * @param scorer confidence scorer
     * @since 0.22.2
     */
    public StructureCompletenessFactor(final ElliottConfidenceScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public String name() {
        return "Structure completeness";
    }

    @Override
    public ConfidenceFactorCategory category() {
        return ConfidenceFactorCategory.COMPLETENESS;
    }

    @Override
    public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
        Num score = scorer.scoreCompleteness(context.swings(), context.phase());
        Map<String, Number> diagnostics = new LinkedHashMap<>();
        diagnostics.put("actualWaves", context.swings().size());
        diagnostics.put("expectedWaves", expectedWaves(context.phase()));
        return ConfidenceFactorResult.of(name(), category(), score, diagnostics, "Structure completeness");
    }

    private int expectedWaves(final ElliottPhase phase) {
        if (phase.isImpulse()) {
            return 5;
        }
        if (phase.isCorrective()) {
            return 3;
        }
        return 0;
    }
}
