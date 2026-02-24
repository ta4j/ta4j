/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.util.Objects;

import org.ta4j.core.analysis.confluence.ConfluenceReport;

/**
 * Calculates support/resistance confidence using the Phase-1 weighted scoring
 * formula from the confluence PRD.
 *
 * <p>
 * The resulting confidence score is capped at {@code 95} to avoid presenting
 * false certainty from heuristic-only scoring.
 *
 * @since 0.22.3
 */
public final class LevelConfidenceCalculator {

    private static final double MAX_CONFIDENCE = 95.0d;
    private static final double MIN_ATR = 1.0e-9d;

    /**
     * Scores a structural level and returns the immutable level confidence model.
     *
     * @param sample level scoring input
     * @return level confidence object
     * @since 0.22.3
     */
    public ConfluenceReport.LevelConfidence score(LevelSample sample) {
        Objects.requireNonNull(sample, "sample cannot be null");

        double touches = clamp01(sample.touchCount() / 10.0d);
        double recency = recencyScore(sample.lastInteractionIndex(), sample.endIndex(), sample.lookbackBars());
        double volatilityContext = volatilityContext(sample.currentPrice(), sample.level(), sample.atr());

        double weighted = 100.0d * (0.40d * sample.structural() + 0.25d * touches + 0.15d * recency
                + 0.10d * sample.agreement() + 0.10d * volatilityContext);
        double confidence = Math.min(MAX_CONFIDENCE, clamp(weighted, 0.0d, 100.0d));
        double distanceToPricePct = ((sample.level() - sample.currentPrice()) / sample.currentPrice()) * 100.0d;

        return new ConfluenceReport.LevelConfidence(sample.type(), sample.name(), sample.level(), confidence,
                distanceToPricePct, sample.structural(), touches, recency, sample.agreement(), volatilityContext,
                sample.rationale());
    }

    private static double volatilityContext(double currentPrice, double level, double atr) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0.0d || !Double.isFinite(level)) {
            return 0.5d;
        }
        double normalizedAtr = Math.max(MIN_ATR, Math.abs(atr));
        double atrDistance = Math.abs(currentPrice - level) / normalizedAtr;
        return clamp01(1.0d - atrDistance / 6.0d);
    }

    private static double recencyScore(int lastInteractionIndex, int endIndex, int lookbackBars) {
        if (lastInteractionIndex < 0 || endIndex < 0 || lastInteractionIndex > endIndex) {
            return 0.20d;
        }
        int bars = Math.max(1, lookbackBars);
        double distance = (double) (endIndex - lastInteractionIndex) / (double) bars;
        return clamp01(1.0d - distance);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0d, 1.0d);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Immutable level scoring input.
     *
     * @param type                 support/resistance type
     * @param name                 level name
     * @param level                absolute level price
     * @param currentPrice         latest close price
     * @param atr                  latest ATR value
     * @param structural           normalized structural score in [0, 1]
     * @param touchCount           observed touch count
     * @param lastInteractionIndex most recent interaction bar index
     * @param endIndex             current end index
     * @param lookbackBars         lookback used for recency normalization
     * @param agreement            normalized agreement score in [0, 1]
     * @param rationale            rationale string
     * @since 0.22.3
     */
    public record LevelSample(ConfluenceReport.LevelType type, String name, double level, double currentPrice,
            double atr, double structural, int touchCount, int lastInteractionIndex, int endIndex, int lookbackBars,
            double agreement, String rationale) {
        public LevelSample {
            Objects.requireNonNull(type, "type cannot be null");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name cannot be null or blank");
            }
            if (!Double.isFinite(level) || level <= 0.0d) {
                throw new IllegalArgumentException("level must be finite and > 0");
            }
            if (!Double.isFinite(currentPrice) || currentPrice <= 0.0d) {
                throw new IllegalArgumentException("currentPrice must be finite and > 0");
            }
            if (!Double.isFinite(atr) || atr < 0.0d) {
                throw new IllegalArgumentException("atr must be finite and >= 0");
            }
            if (!Double.isFinite(structural) || structural < 0.0d || structural > 1.0d) {
                throw new IllegalArgumentException("structural must be in [0, 1]");
            }
            if (touchCount < 0) {
                throw new IllegalArgumentException("touchCount must be >= 0");
            }
            if (endIndex < 0) {
                throw new IllegalArgumentException("endIndex must be >= 0");
            }
            if (lookbackBars <= 0) {
                throw new IllegalArgumentException("lookbackBars must be > 0");
            }
            if (!Double.isFinite(agreement) || agreement < 0.0d || agreement > 1.0d) {
                throw new IllegalArgumentException("agreement must be in [0, 1]");
            }
            if (rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("rationale cannot be null or blank");
            }
        }
    }
}
