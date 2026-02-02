/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.Num;

/**
 * Scores Fibonacci proximity relationships for impulse/corrective waves.
 *
 * @since 0.22.2
 */
public final class FibonacciRelationshipFactor implements ConfidenceFactor {

    @Override
    public String name() {
        return "Fibonacci relationships";
    }

    @Override
    public ConfidenceFactorCategory category() {
        return ConfidenceFactorCategory.FIBONACCI;
    }

    @Override
    public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
        List<ElliottSwing> swings = context.swings();
        ElliottPhase phase = context.phase();
        Map<String, Number> diagnostics = new LinkedHashMap<>();

        if (swings == null || swings.isEmpty() || phase == ElliottPhase.NONE) {
            return ConfidenceFactorResult.of(name(), category(), context.numFactory().zero(), diagnostics,
                    "Insufficient swings");
        }

        double total = 0.0;
        int count = 0;

        if (phase.isImpulse()) {
            if (swings.size() >= 2) {
                Num score = context.validator().waveTwoProximityScore(swings.get(0), swings.get(1));
                diagnostics.put("wave2", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
            if (swings.size() >= 3) {
                Num score = context.validator().waveThreeProximityScore(swings.get(0), swings.get(2));
                diagnostics.put("wave3", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
            if (swings.size() >= 4) {
                Num score = context.validator().waveFourProximityScore(swings.get(2), swings.get(3));
                diagnostics.put("wave4", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
            if (swings.size() >= 5) {
                Num score = context.validator().waveFiveProximityScore(swings.get(0), swings.get(4));
                diagnostics.put("wave5", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
        } else if (phase.isCorrective()) {
            if (swings.size() >= 2) {
                Num score = context.validator().waveBProximityScore(swings.get(0), swings.get(1));
                diagnostics.put("waveB", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
            if (swings.size() >= 3) {
                Num score = context.validator().waveCProximityScore(swings.get(0), swings.get(2));
                diagnostics.put("waveC", score.doubleValue());
                total += score.doubleValue();
                count++;
            }
        }

        Num average = count > 0 ? context.numFactory().numOf(total / count) : context.numFactory().zero();
        return ConfidenceFactorResult.of(name(), category(), average, diagnostics, "Fibonacci proximity");
    }
}
