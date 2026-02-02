/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.Num;

/**
 * Scores time proportion conformance.
 *
 * @since 0.22.2
 */
public final class TimeProportionFactor implements ConfidenceFactor {

    private final ElliottConfidenceScorer scorer;

    /**
     * Creates the factor using the provided scorer.
     *
     * @param scorer confidence scorer
     * @since 0.22.2
     */
    public TimeProportionFactor(final ElliottConfidenceScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public String name() {
        return "Time proportions";
    }

    @Override
    public ConfidenceFactorCategory category() {
        return ConfidenceFactorCategory.TIME;
    }

    @Override
    public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
        List<ElliottSwing> swings = context.swings();
        ElliottPhase phase = context.phase();
        Num score = scorer.scoreTimeProportions(swings, phase);

        Map<String, Number> diagnostics = new LinkedHashMap<>();
        if (swings.size() >= 1) {
            diagnostics.put("wave1Bars", swings.get(0).length());
        }
        if (swings.size() >= 3) {
            diagnostics.put("wave3Bars", swings.get(2).length());
        }
        if (swings.size() >= 5) {
            diagnostics.put("wave5Bars", swings.get(4).length());
        }

        return ConfidenceFactorResult.of(name(), category(), score, diagnostics, "Time proportions");
    }
}
