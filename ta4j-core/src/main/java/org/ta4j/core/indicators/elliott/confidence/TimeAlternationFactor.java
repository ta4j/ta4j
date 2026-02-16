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
 * Scores wave 2/4 alternation with time diagnostics.
 *
 * <p>
 * Use this factor to emphasize alternation quality between corrective waves. It
 * leverages alternation diagnostics from {@link ElliottConfidenceScorer} and
 * returns detailed timing notes in the diagnostics map.
 *
 * @since 0.22.2
 */
public final class TimeAlternationFactor implements ConfidenceFactor {

    private final ElliottConfidenceScorer scorer;

    /**
     * @param scorer confidence scorer
     * @since 0.22.2
     */
    public TimeAlternationFactor(final ElliottConfidenceScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public String name() {
        return "Time alternation";
    }

    @Override
    public ConfidenceFactorCategory category() {
        return ConfidenceFactorCategory.ALTERNATION;
    }

    @Override
    public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
        ElliottConfidenceScorer.AlternationDiagnostics diagnosticsData = scorer.alternationDiagnostics(context.swings(),
                context.phase());
        Num score = context.numFactory().numOf(diagnosticsData.score());
        Map<String, Number> diagnostics = new LinkedHashMap<>();
        diagnostics.put("barsWave2", diagnosticsData.barsWave2());
        diagnostics.put("barsWave4", diagnosticsData.barsWave4());
        diagnostics.put("durationRatio", diagnosticsData.durationRatio());
        diagnostics.put("depthDifference", diagnosticsData.depthDifference());
        diagnostics.put("timeDifference", diagnosticsData.timeDifference());
        return ConfidenceFactorResult.of(name(), category(), score, diagnostics, "Wave alternation");
    }
}
