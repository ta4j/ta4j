/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.confluence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable confluence analysis report carrying snapshot context, pillar
 * scores, level confidence, calibrated horizon probabilities, and confidence
 * decomposition.
 *
 * @param snapshot             the market snapshot metadata and aggregate scores
 * @param pillarScores         per-pillar scoring details
 * @param levelConfidences     scored support/resistance levels
 * @param horizonProbabilities per-horizon directional/range probabilities
 * @param confidenceBreakdown  decomposed confidence components
 * @param validationMetadata   validation and calibration diagnostics metadata
 * @param outlookNarrative     human-readable outlook bullets
 * @param extensions           optional extension metadata for adapters/plugins
 * @since 0.22.3
 */
public record ConfluenceReport(Snapshot snapshot, List<PillarScore> pillarScores,
        List<LevelConfidence> levelConfidences, List<HorizonProbability> horizonProbabilities,
        ConfidenceBreakdown confidenceBreakdown, ValidationMetadata validationMetadata, List<String> outlookNarrative,
        Map<String, String> extensions) {

    private static final double EPSILON = 1.0e-6d;

    /**
     * Canonical constructor that validates and snapshots collection inputs.
     */
    public ConfluenceReport {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(pillarScores, "pillarScores cannot be null");
        Objects.requireNonNull(levelConfidences, "levelConfidences cannot be null");
        Objects.requireNonNull(horizonProbabilities, "horizonProbabilities cannot be null");
        Objects.requireNonNull(confidenceBreakdown, "confidenceBreakdown cannot be null");
        Objects.requireNonNull(validationMetadata, "validationMetadata cannot be null");
        Objects.requireNonNull(outlookNarrative, "outlookNarrative cannot be null");
        Objects.requireNonNull(extensions, "extensions cannot be null");

        pillarScores = List.copyOf(pillarScores);
        levelConfidences = List.copyOf(levelConfidences);
        horizonProbabilities = List.copyOf(horizonProbabilities);
        outlookNarrative = List.copyOf(outlookNarrative);
        extensions = Map.copyOf(extensions);
    }

    /**
     * Returns the top-N support levels sorted by descending confidence.
     *
     * @param limit maximum number of levels to return
     * @return immutable list of support levels
     * @since 0.22.3
     */
    public List<LevelConfidence> topSupports(int limit) {
        return topLevels(LevelType.SUPPORT, limit);
    }

    /**
     * Returns the top-N resistance levels sorted by descending confidence.
     *
     * @param limit maximum number of levels to return
     * @return immutable list of resistance levels
     * @since 0.22.3
     */
    public List<LevelConfidence> topResistances(int limit) {
        return topLevels(LevelType.RESISTANCE, limit);
    }

    /**
     * Returns the top-N levels for the requested type sorted by descending
     * confidence.
     *
     * @param levelType the level type to filter
     * @param limit     maximum number of levels to return
     * @return immutable list of matching levels
     * @since 0.22.3
     */
    public List<LevelConfidence> topLevels(LevelType levelType, int limit) {
        Objects.requireNonNull(levelType, "levelType cannot be null");
        if (limit <= 0) {
            return List.of();
        }
        return levelConfidences.stream()
                .filter(level -> level.type() == levelType)
                .sorted(Comparator.comparingDouble(LevelConfidence::confidence).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Direction label used across pillar, feature, and level evaluation.
     *
     * @since 0.22.3
     */
    public enum Direction {
        BULLISH, BEARISH, NEUTRAL
    }

    /**
     * Confluence pillar namespace.
     *
     * @since 0.22.3
     */
    public enum Pillar {
        STRUCTURE, TREND, MOMENTUM, VOLATILITY, PARTICIPATION, MACRO_INTERMARKET
    }

    /**
     * Supported report horizons.
     *
     * @since 0.22.3
     */
    public enum Horizon {
        ONE_MONTH, THREE_MONTH
    }

    /**
     * Type of structural level.
     *
     * @since 0.22.3
     */
    public enum LevelType {
        SUPPORT, RESISTANCE
    }

    /**
     * Per-feature attribution inside a pillar.
     *
     * @param featureName     feature identifier
     * @param normalizedScore normalized contribution score in [0, 100]
     * @param weight          feature weight contribution in [0, 1]
     * @param direction       directional interpretation
     * @param rationale       short explanation
     * @since 0.22.3
     */
    public record FeatureContribution(String featureName, double normalizedScore, double weight, Direction direction,
            String rationale) {
        public FeatureContribution {
            requireNonBlank(featureName, "featureName");
            requirePercentage(normalizedScore, "normalizedScore");
            requireUnitInterval(weight, "weight");
            Objects.requireNonNull(direction, "direction cannot be null");
            requireNonBlank(rationale, "rationale");
        }
    }

    /**
     * Aggregated score and evidence for a single pillar.
     *
     * @param pillar               pillar namespace
     * @param family               family id used by decorrelation/capping engines
     * @param score                pillar score in [0, 100]
     * @param weight               configured pillar weight (non-negative)
     * @param direction            directional interpretation
     * @param featureContributions per-feature attributions
     * @param explanations         human-readable explanations
     * @since 0.22.3
     */
    public record PillarScore(Pillar pillar, String family, double score, double weight, Direction direction,
            List<FeatureContribution> featureContributions, List<String> explanations) {
        public PillarScore {
            Objects.requireNonNull(pillar, "pillar cannot be null");
            family = normalizeFamily(family, pillar);
            requirePercentage(score, "score");
            requireNonNegative(weight, "weight");
            Objects.requireNonNull(direction, "direction cannot be null");
            Objects.requireNonNull(featureContributions, "featureContributions cannot be null");
            Objects.requireNonNull(explanations, "explanations cannot be null");
            featureContributions = List.copyOf(featureContributions);
            explanations = List.copyOf(explanations);
        }
    }

    /**
     * Confidence-qualified support/resistance level.
     *
     * @param type               support/resistance classification
     * @param name               level identifier
     * @param level              price level
     * @param confidence         level confidence in [0, 100]
     * @param distanceToPricePct signed distance from current price in percent
     * @param structural         normalized structural component in [0, 1]
     * @param touches            normalized touch component in [0, 1]
     * @param recency            normalized recency component in [0, 1]
     * @param agreement          normalized agreement component in [0, 1]
     * @param volatilityContext  normalized volatility-context component in [0, 1]
     * @param rationale          level rationale
     * @since 0.22.3
     */
    public record LevelConfidence(LevelType type, String name, double level, double confidence,
            double distanceToPricePct, double structural, double touches, double recency, double agreement,
            double volatilityContext, String rationale) {
        public LevelConfidence {
            Objects.requireNonNull(type, "type cannot be null");
            requireNonBlank(name, "name");
            requireFinite(level, "level");
            requirePercentage(confidence, "confidence");
            requireFinite(distanceToPricePct, "distanceToPricePct");
            requireUnitInterval(structural, "structural");
            requireUnitInterval(touches, "touches");
            requireUnitInterval(recency, "recency");
            requireUnitInterval(agreement, "agreement");
            requireUnitInterval(volatilityContext, "volatilityContext");
            requireNonBlank(rationale, "rationale");
        }
    }

    /**
     * Horizon probability vector.
     *
     * @param horizon           horizon key
     * @param upProbability     probability of directional up move
     * @param downProbability   probability of directional down move
     * @param rangeProbability  probability of range regime
     * @param calibrated        whether probabilities are calibrated
     * @param calibrationMethod calibration method label
     * @since 0.22.3
     */
    public record HorizonProbability(Horizon horizon, double upProbability, double downProbability,
            double rangeProbability, boolean calibrated, String calibrationMethod) {
        public HorizonProbability {
            Objects.requireNonNull(horizon, "horizon cannot be null");
            requireUnitInterval(upProbability, "upProbability");
            requireUnitInterval(downProbability, "downProbability");
            requireUnitInterval(rangeProbability, "rangeProbability");
            requireNonBlank(calibrationMethod, "calibrationMethod");
            double sum = upProbability + downProbability + rangeProbability;
            if (Math.abs(sum - 1.0d) > EPSILON) {
                throw new IllegalArgumentException("horizon probabilities must sum to 1.0, got " + sum);
            }
        }
    }

    /**
     * Confidence decomposition payload.
     *
     * @param modelConfidence       model confidence in [0, 100]
     * @param calibrationConfidence calibration confidence in [0, 100]
     * @param regimeConfidence      regime confidence in [0, 100]
     * @param dataConfidence        data confidence in [0, 100]
     * @param finalConfidence       blended final confidence in [0, 100]
     * @param notes                 decomposition notes
     * @since 0.22.3
     */
    public record ConfidenceBreakdown(double modelConfidence, double calibrationConfidence, double regimeConfidence,
            double dataConfidence, double finalConfidence, List<String> notes) {
        public ConfidenceBreakdown {
            requirePercentage(modelConfidence, "modelConfidence");
            requirePercentage(calibrationConfidence, "calibrationConfidence");
            requirePercentage(regimeConfidence, "regimeConfidence");
            requirePercentage(dataConfidence, "dataConfidence");
            requirePercentage(finalConfidence, "finalConfidence");
            Objects.requireNonNull(notes, "notes cannot be null");
            notes = List.copyOf(notes);
        }
    }

    /**
     * Validation and calibration metadata.
     *
     * @param calibrationMethod        calibration method label
     * @param trainingWindow           training window descriptor
     * @param lastCalibrationDate      last calibration date (ISO-8601 preferred)
     * @param brierScore               optional brier score
     * @param expectedCalibrationError optional ECE value
     * @param logLoss                  optional log-loss value
     * @param reliabilityArtifactPath  optional reliability artifact path
     * @param warnings                 validation warnings
     * @since 0.22.3
     */
    public record ValidationMetadata(String calibrationMethod, String trainingWindow, String lastCalibrationDate,
            Double brierScore, Double expectedCalibrationError, Double logLoss, String reliabilityArtifactPath,
            List<String> warnings) {
        public ValidationMetadata {
            requireNonBlank(calibrationMethod, "calibrationMethod");
            requireNonBlank(trainingWindow, "trainingWindow");
            requireNonBlank(lastCalibrationDate, "lastCalibrationDate");
            requireOptionalNonNegative(brierScore, "brierScore");
            requireOptionalNonNegative(expectedCalibrationError, "expectedCalibrationError");
            requireOptionalNonNegative(logLoss, "logLoss");
            if (reliabilityArtifactPath != null && reliabilityArtifactPath.isBlank()) {
                throw new IllegalArgumentException("reliabilityArtifactPath cannot be blank when provided");
            }
            Objects.requireNonNull(warnings, "warnings cannot be null");
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Snapshot metadata plus aggregate confluence score fields.
     *
     * @param ticker                      instrument symbol
     * @param timeframe                   timeframe label
     * @param barEndTime                  bar timestamp
     * @param barCount                    loaded bar count
     * @param closePrice                  close price at snapshot
     * @param rawConfluenceScore          raw confluence score in [0, 100]
     * @param decorrelatedConfluenceScore decorrelated score in [0, 100]
     * @param correlationPenalty          penalty in score points (non-negative)
     * @since 0.22.3
     */
    public record Snapshot(String ticker, String timeframe, Instant barEndTime, int barCount, double closePrice,
            double rawConfluenceScore, double decorrelatedConfluenceScore, double correlationPenalty) {
        public Snapshot {
            requireNonBlank(ticker, "ticker");
            requireNonBlank(timeframe, "timeframe");
            Objects.requireNonNull(barEndTime, "barEndTime cannot be null");
            if (barCount <= 0) {
                throw new IllegalArgumentException("barCount must be greater than zero");
            }
            requireFinite(closePrice, "closePrice");
            requirePercentage(rawConfluenceScore, "rawConfluenceScore");
            requirePercentage(decorrelatedConfluenceScore, "decorrelatedConfluenceScore");
            requireNonNegative(correlationPenalty, "correlationPenalty");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static void requireNonNegative(double value, String field) {
        requireFinite(value, field);
        if (value < 0.0d) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requirePercentage(double value, String field) {
        requireFinite(value, field);
        if (value < 0.0d || value > 100.0d) {
            throw new IllegalArgumentException(field + " must be in [0, 100]");
        }
    }

    private static void requireUnitInterval(double value, String field) {
        requireFinite(value, field);
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be in [0, 1]");
        }
    }

    private static void requireOptionalNonNegative(Double value, String field) {
        if (value == null) {
            return;
        }
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(field + " must be finite and non-negative when provided");
        }
    }

    private static String normalizeFamily(String family, Pillar pillar) {
        if (family == null || family.isBlank()) {
            return pillar.name();
        }
        return family.trim();
    }
}
