/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.num.Num;

/**
 * Scores channel adherence.
 *
 * @since 0.22.2
 */
public final class ChannelAdherenceFactor implements ConfidenceFactor {

    private final ElliottConfidenceScorer scorer;

    /**
     * @param scorer confidence scorer
     * @since 0.22.2
     */
    public ChannelAdherenceFactor(final ElliottConfidenceScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public String name() {
        return "Channel adherence";
    }

    @Override
    public ConfidenceFactorCategory category() {
        return ConfidenceFactorCategory.CHANNEL;
    }

    @Override
    public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
        Num score = scorer.scoreChannel(context.swings(), context.channel());
        Map<String, Number> diagnostics = new LinkedHashMap<>();
        diagnostics.put("withinChannelRatio", score.doubleValue());
        return ConfidenceFactorResult.of(name(), category(), score, diagnostics, "Channel adherence");
    }
}
