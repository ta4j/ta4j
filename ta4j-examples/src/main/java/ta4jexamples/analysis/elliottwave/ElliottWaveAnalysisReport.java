/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.Optional;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator;
import org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatioIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottRatio;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.num.Num;

import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.charting.builder.ChartPlan;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.ExclusionStrategy;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.Gson;

/**
 * Domain model capturing all Elliott Wave analysis results currently logged via
 * {@link ElliottWaveIndicatorSuiteDemo#logBaseCaseScenario(ElliottScenario)}
 * and {@link ElliottWaveIndicatorSuiteDemo#logAlternativeScenarios(List)}.
 * <p>
 * This class provides structured access to analysis results including swing
 * snapshots, phase information, ratio and channel data, confluence scores,
 * scenario summaries, trend bias, and detailed base case and alternative
 * scenario information.
 * <p>
 * The class contains pre-rendered chart images (PNG format, base64-encoded) for
 * all scenarios, allowing charts to be viewed without requiring external data
 * or chart generation libraries. Charts are embedded as base64-encoded PNG byte
 * arrays, making them directly viewable in browsers, reports, or any system
 * that supports PNG images.
 * <p>
 * The class is serializable to JSON using Gson, providing a structured
 * representation suitable for storage, transmission, or further processing.
 *
 * @see ElliottWaveIndicatorSuiteDemo
 * @since 0.22.4
 */
public record ElliottWaveAnalysisReport(ElliottDegree degree, int endIndex, SwingSnapshot swingSnapshot,
        LatestAnalysis latestAnalysis, ScenarioSummary scenarioSummary, ElliottTrendBias trendBias,
        ProbabilityCalibration probabilityCalibration, OutlookGate outlookGate, BaseCaseScenario baseCase,
        List<AlternativeScenario> alternatives, String baseCaseChartImage, List<String> alternativeChartImages) {
    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnalysisReport.class);
    private static final double SCENARIO_TYPE_OVERLAP_WEIGHT = 0.3;
    private static final double CONSENSUS_ADJUSTMENT_WEIGHT = 0.4;
    private static final double DIRECTION_OVERLAP_WEIGHT = 0.2;
    private static final double PHASE_OVERLAP_WEIGHT = 0.5;
    private static final double MIN_CONFIDENCE_CONTRAST_EXPONENT = 1.5;
    private static final double MAX_CONFIDENCE_CONTRAST_EXPONENT = 6.0;
    private static final double CONFIDENCE_SPREAD_TARGET = 0.25;
    private static final String CALIBRATION_METHOD = "centered_shrinkage_renormalized";
    private static final String CALIBRATION_PROFILE = "wf-baseline-minute-f2-h2l2-max25-sw0__k1-200-65-320";
    private static final double CALIBRATION_BASE_SHRINK_FACTOR = 0.72;
    private static final double CALIBRATION_STRONG_CONSENSUS_BONUS = 0.08;
    private static final double CALIBRATION_DIRECTIONAL_CONSENSUS_BONUS = 0.06;
    private static final double CALIBRATION_WEAK_TREND_PENALTY = 0.06;
    private static final double CALIBRATION_MIN_SHRINK_FACTOR = 0.45;
    private static final double CALIBRATION_MAX_SHRINK_FACTOR = 0.95;
    private static final double OUTLOOK_MIN_TOP_PROBABILITY = 0.30;
    private static final double OUTLOOK_MIN_TOP_TWO_SPREAD = 0.03;
    private static final double OUTLOOK_MIN_TOP_THREE_SPREAD = 0.08;
    private static final double OUTLOOK_MIN_TREND_STRENGTH = 0.15;
    private static final double CALIBRATION_EPSILON = 1.0e-12;

    private static final TypeAdapter<Double> NULLING_DOUBLE_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Double value) throws IOException {
            if (value == null || value.isNaN() || value.isInfinite()) {
                out.nullValue();
                return;
            }
            out.value(value);
        }

        @Override
        public Double read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return in.nextDouble();
        }
    };

    /**
     * Creates an analysis result from the current analysis state.
     *
     * @param degree                the Elliott wave degree used
     * @param swingMetadata         swing metadata snapshot
     * @param phaseIndicator        phase indicator (for latest phase and
     *                              confirmation flags)
     * @param ratioIndicator        ratio indicator (for latest ratio)
     * @param channelIndicator      channel indicator (for latest channel)
     * @param confluenceIndicator   confluence indicator (for latest confluence)
     * @param invalidationIndicator invalidation indicator (for latest invalidation)
     * @param scenarioSet           scenario set (for scenario summary and
     *                              scenarios)
     * @param endIndex              index at which to evaluate indicators
     * @param baseCaseChartPlan     chart plan for base case scenario (optional)
     * @param alternativeChartPlans chart plans for alternative scenarios
     * @return analysis result capturing all logged data and chart images
     */
    public static ElliottWaveAnalysisReport from(ElliottDegree degree, ElliottSwingMetadata swingMetadata,
            ElliottPhaseIndicator phaseIndicator, ElliottRatioIndicator ratioIndicator,
            ElliottChannelIndicator channelIndicator, ElliottConfluenceIndicator confluenceIndicator,
            ElliottInvalidationIndicator invalidationIndicator, ElliottScenarioSet scenarioSet, int endIndex,
            Optional<ChartPlan> baseCaseChartPlan, List<ChartPlan> alternativeChartPlans) {
        Objects.requireNonNull(degree, "degree");
        Objects.requireNonNull(swingMetadata, "swingMetadata");
        Objects.requireNonNull(phaseIndicator, "phaseIndicator");
        Objects.requireNonNull(ratioIndicator, "ratioIndicator");
        Objects.requireNonNull(channelIndicator, "channelIndicator");
        Objects.requireNonNull(confluenceIndicator, "confluenceIndicator");
        Objects.requireNonNull(invalidationIndicator, "invalidationIndicator");
        Objects.requireNonNull(scenarioSet, "scenarioSet");
        Objects.requireNonNull(baseCaseChartPlan, "baseCaseChartPlan");
        Objects.requireNonNull(alternativeChartPlans, "alternativeChartPlans");

        SwingSnapshot snapshot = SwingSnapshot.from(swingMetadata);
        LatestAnalysis latest = LatestAnalysis.from(phaseIndicator, ratioIndicator, channelIndicator,
                confluenceIndicator, invalidationIndicator, endIndex);
        ScenarioSummary summary = ScenarioSummary.from(scenarioSet);
        ElliottTrendBias trendBias = scenarioSet.trendBias();
        Map<String, Double> scenarioProbabilities = computeScenarioProbabilities(scenarioSet);
        CalibrationResult calibrationResult = calibrateScenarioProbabilities(scenarioProbabilities, summary, trendBias);
        Map<String, Double> calibratedProbabilities = calibrationResult.calibratedProbabilities();
        ProbabilityCalibration probabilityCalibration = calibrationResult.calibration();
        OutlookGate outlookGate = OutlookGate.from(scenarioProbabilities, calibratedProbabilities, summary, trendBias);
        BaseCaseScenario baseCase = scenarioSet.base()
                .map(scenario -> BaseCaseScenario.from(scenario, scenarioProbabilities.getOrDefault(scenario.id(), 0.0),
                        calibratedProbabilities.getOrDefault(scenario.id(), 0.0)))
                .orElse(null);
        List<AlternativeScenario> alternatives = scenarioSet.alternatives()
                .stream()
                .map(scenario -> AlternativeScenario.from(scenario,
                        scenarioProbabilities.getOrDefault(scenario.id(), 0.0),
                        calibratedProbabilities.getOrDefault(scenario.id(), 0.0)))
                .toList();

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        String baseCaseChartImage = baseCaseChartPlan.map(plan -> encodeChartAsBase64(chartWorkflow, plan))
                .orElse(null);
        List<String> alternativeChartImages = alternativeChartPlans.stream()
                .map(plan -> encodeChartAsBase64(chartWorkflow, plan))
                .toList();

        return new ElliottWaveAnalysisReport(degree, endIndex, snapshot, latest, summary, trendBias,
                probabilityCalibration, outlookGate, baseCase, alternatives, baseCaseChartImage,
                alternativeChartImages);
    }

    /**
     * Serializes this analysis result to JSON using Gson.
     * <p>
     * By default, chart image data is excluded to keep the JSON size manageable.
     * Use {@link #toJson(boolean)} with {@code true} to include chart images.
     *
     * @return JSON representation of the analysis result (without chart images)
     */
    public String toJson() {
        return toJson(false);
    }

    /**
     * Serializes this analysis result to JSON using Gson.
     *
     * @param includeChartData if {@code true}, includes base64-encoded PNG chart
     *                         images; if {@code false}, excludes chart images to
     *                         reduce JSON size
     * @return JSON representation of the analysis result
     */
    public String toJson(boolean includeChartData) {
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting()
                .serializeNulls()
                .registerTypeAdapter(Double.class, NULLING_DOUBLE_ADAPTER)
                .registerTypeAdapter(Double.TYPE, NULLING_DOUBLE_ADAPTER);
        if (!includeChartData) {
            builder.setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return "baseCaseChartImage".equals(f.getName()) || "alternativeChartImages".equals(f.getName());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            });
        }
        Gson gson = builder.create();
        return gson.toJson(this);
    }

    /**
     * Swing snapshot data capturing the current state of detected swings.
     *
     * @param valid  whether the snapshot contains valid prices for each swing
     * @param swings number of swings represented
     * @param high   highest price touched by any swing
     * @param low    lowest price touched by any swing
     */
    public record SwingSnapshot(boolean valid, int swings, double high, double low) {
        static SwingSnapshot from(ElliottSwingMetadata metadata) {
            return new SwingSnapshot(metadata.isValid(), metadata.size(), safeDoubleValue(metadata.highestPrice()),
                    safeDoubleValue(metadata.lowestPrice()));
        }
    }

    /**
     * Latest analysis results at the evaluation index.
     *
     * @param phase               current Elliott phase
     * @param impulseConfirmed    whether impulse waves are confirmed
     * @param correctiveConfirmed whether corrective waves are confirmed
     * @param ratioType           type of ratio relationship (retracement/extension)
     * @param ratioValue          measured ratio value
     * @param channel             channel data (null if invalid)
     * @param confluenceScore     confluence score
     * @param confluent           whether confluence threshold is met
     * @param invalidation        whether current price action invalidates the wave
     *                            count
     */
    public record LatestAnalysis(ElliottPhase phase, boolean impulseConfirmed, boolean correctiveConfirmed,
            RatioType ratioType, double ratioValue, ChannelData channel, double confluenceScore, boolean confluent,
            boolean invalidation) {
        static LatestAnalysis from(ElliottPhaseIndicator phaseIndicator, ElliottRatioIndicator ratioIndicator,
                ElliottChannelIndicator channelIndicator, ElliottConfluenceIndicator confluenceIndicator,
                ElliottInvalidationIndicator invalidationIndicator, int endIndex) {
            ElliottPhase phase = phaseIndicator.getValue(endIndex);
            boolean impulseConfirmed = phaseIndicator.isImpulseConfirmed(endIndex);
            boolean correctiveConfirmed = phaseIndicator.isCorrectiveConfirmed(endIndex);

            ElliottRatio ratio = ratioIndicator.getValue(endIndex);
            RatioType ratioType = ratio != null ? ratio.type() : RatioType.NONE;
            double ratioValue = ratio != null ? safeDoubleValue(ratio.value()) : Double.NaN;

            ElliottChannel channel = channelIndicator.getValue(endIndex);
            ChannelData channelData = channel != null && channel.isValid() ? ChannelData.from(channel) : null;

            Num confluenceNum = confluenceIndicator.getValue(endIndex);
            double confluenceScore = safeDoubleValue(confluenceNum);
            boolean confluent = confluenceIndicator.isConfluent(endIndex);

            boolean invalidation = invalidationIndicator.getValue(endIndex);

            return new LatestAnalysis(phase, impulseConfirmed, correctiveConfirmed, ratioType, ratioValue, channelData,
                    confluenceScore, confluent, invalidation);
        }
    }

    /**
     * Channel boundary data.
     *
     * @param valid  whether the channel is valid
     * @param upper  expected resistance boundary
     * @param lower  expected support boundary
     * @param median arithmetic midline between upper and lower bounds
     */
    public record ChannelData(boolean valid, double upper, double lower, double median) {
        static ChannelData from(ElliottChannel channel) {
            return new ChannelData(channel.isValid(), safeDoubleValue(channel.upper()),
                    safeDoubleValue(channel.lower()), safeDoubleValue(channel.median()));
        }
    }

    /**
     * Scenario summary across all scenarios.
     *
     * @param summary         human-readable summary describing scenario
     *                        distribution
     * @param strongConsensus whether there is strong consensus (single
     *                        high-confidence scenario or large spread)
     * @param consensusPhase  agreed-upon phase if all high-confidence scenarios
     *                        match, otherwise NONE
     */
    public record ScenarioSummary(String summary, boolean strongConsensus, ElliottPhase consensusPhase) {
        static ScenarioSummary from(ElliottScenarioSet scenarioSet) {
            return new ScenarioSummary(scenarioSet.summary(), scenarioSet.hasStrongConsensus(),
                    scenarioSet.consensus());
        }
    }

    /**
     * Metadata describing how probabilities were calibrated for this analysis run.
     *
     * @param profile      calibration profile id sourced from walk-forward tuning
     * @param method       calibration transform identifier
     * @param shrinkFactor shrink factor applied to centered scenario probabilities
     */
    public record ProbabilityCalibration(String profile, String method, double shrinkFactor) {
        static ProbabilityCalibration from(double shrinkFactor) {
            final String profile = CALIBRATION_PROFILE + "|cfg="
                    + ElliottWaveWalkForwardProfiles.baselineConfig().configHash();
            return new ProbabilityCalibration(profile, CALIBRATION_METHOD, shrinkFactor);
        }
    }

    /**
     * Directional outlook gate status.
     *
     * <p>
     * The gate blocks directional publication when scenario probabilities are too
     * crowded or consensus signals are weak.
     *
     * @param eligible                 whether directional outlook can be published
     * @param outlookLabel             directional label or {@code NEUTRAL}
     * @param reason                   concise gate decision explanation
     * @param topScenarioProbability   top raw scenario probability
     * @param calibratedTopProbability top calibrated scenario probability
     * @param topTwoSpread             spread between top-1 and top-2 raw
     *                                 probabilities
     * @param topThreeSpread           spread between top-1 and top-3 raw
     *                                 probabilities
     * @param strongConsensus          whether strong scenario consensus is present
     * @param directionalConsensus     whether directional consensus is present
     * @param trendStrength            trend bias strength (0.0-1.0)
     */
    public record OutlookGate(boolean eligible, String outlookLabel, String reason, double topScenarioProbability,
            double calibratedTopProbability, double topTwoSpread, double topThreeSpread, boolean strongConsensus,
            boolean directionalConsensus, double trendStrength) {
        static OutlookGate from(Map<String, Double> rawProbabilities, Map<String, Double> calibratedProbabilities,
                ScenarioSummary summary, ElliottTrendBias trendBias) {
            if (rawProbabilities == null || rawProbabilities.isEmpty()) {
                return new OutlookGate(false, "NEUTRAL", "No scenarios available", Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, false, false, Double.NaN);
            }

            final List<Double> rawSorted = rawProbabilities.values()
                    .stream()
                    .filter(Double::isFinite)
                    .sorted((a, b) -> Double.compare(b, a))
                    .toList();
            final List<Double> calibratedSorted = calibratedProbabilities.values()
                    .stream()
                    .filter(Double::isFinite)
                    .sorted((a, b) -> Double.compare(b, a))
                    .toList();
            if (rawSorted.isEmpty()) {
                return new OutlookGate(false, "NEUTRAL", "Scenario probabilities were not finite", Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, false, false, Double.NaN);
            }

            final double top = rawSorted.get(0);
            final double topTwoSpread = rawSorted.size() > 1 ? top - rawSorted.get(1) : top;
            final double topThreeSpread = rawSorted.size() > 2 ? top - rawSorted.get(2) : topTwoSpread;
            final double calibratedTop = calibratedSorted.isEmpty() ? Double.NaN : calibratedSorted.get(0);

            final boolean strongConsensus = summary != null && summary.strongConsensus();
            final boolean directionalConsensus = trendBias != null && trendBias.consensus();
            final boolean knownTrend = trendBias != null && !trendBias.isUnknown() && !trendBias.isNeutral();
            final double trendStrength = trendBias == null ? Double.NaN : trendBias.strength();
            final boolean strongTrend = Double.isFinite(trendStrength) && trendStrength >= OUTLOOK_MIN_TREND_STRENGTH;
            final boolean topProbabilityPass = top >= OUTLOOK_MIN_TOP_PROBABILITY;
            final boolean spreadPass = topTwoSpread >= OUTLOOK_MIN_TOP_TWO_SPREAD
                    && topThreeSpread >= OUTLOOK_MIN_TOP_THREE_SPREAD;

            final boolean eligible = strongConsensus && directionalConsensus && knownTrend && strongTrend
                    && topProbabilityPass && spreadPass;
            final String label = eligible && trendBias != null ? trendBias.direction().name() : "NEUTRAL";
            final String reason;
            if (eligible) {
                reason = "Directional outlook passed consensus and spread gates";
            } else if (!strongConsensus) {
                reason = "Strong scenario consensus not established";
            } else if (!directionalConsensus || !knownTrend) {
                reason = "Directional consensus is weak or trend is neutral";
            } else if (!strongTrend) {
                reason = "Trend strength is below baseline threshold";
            } else if (!topProbabilityPass) {
                reason = "Top scenario probability is below publication threshold";
            } else {
                reason = "Top scenarios are too close; low-conviction outlook";
            }

            return new OutlookGate(eligible, label, reason, top, calibratedTop, topTwoSpread, topThreeSpread,
                    strongConsensus, directionalConsensus, trendStrength);
        }
    }

    /**
     * Internal result container for calibrated probabilities and calibration
     * metadata.
     */
    private record CalibrationResult(Map<String, Double> calibratedProbabilities, ProbabilityCalibration calibration) {
    }

    /**
     * Base case scenario details.
     *
     * @param currentPhase          the phase this scenario assigns to current price
     *                              action
     * @param type                  pattern type classification
     * @param overallConfidence     overall confidence percentage (0-100)
     * @param scenarioProbability   raw scenario probability ratio (0.0-1.0)
     * @param calibratedProbability walk-forward calibrated scenario probability
     *                              ratio (0.0-1.0)
     * @param confidenceLevel       confidence level (HIGH, MEDIUM, or LOW)
     * @param fibonacciScore        Fibonacci proximity score as percentage (0-100)
     * @param timeScore             time proportion score as percentage (0-100)
     * @param alternationScore      alternation quality score as percentage (0-100)
     * @param channelScore          channel adherence score as percentage (0-100)
     * @param completenessScore     structure completeness score as percentage
     *                              (0-100)
     * @param primaryReason         human-readable description of dominant factor
     * @param weakestFactor         description of the weakest scoring factor
     * @param direction             direction (BULLISH or BEARISH)
     * @param invalidationPrice     price level that would invalidate this count
     * @param primaryTarget         primary Fibonacci projection target
     * @param swings                swing sequence for building wave labels
     */
    public record BaseCaseScenario(ElliottPhase currentPhase, ScenarioType type, double overallConfidence,
            @JsonAdapter(ScenarioProbabilityAdapter.class) double scenarioProbability,
            @JsonAdapter(ScenarioProbabilityAdapter.class) double calibratedProbability, String confidenceLevel,
            double fibonacciScore, double timeScore, double alternationScore, double channelScore,
            double completenessScore, String primaryReason, String weakestFactor, String direction,
            double invalidationPrice, double primaryTarget, List<SwingData> swings) {
        static BaseCaseScenario from(ElliottScenario scenario, double scenarioProbability,
                double calibratedProbability) {
            ElliottConfidence confidence = scenario.confidence();
            double overallConfidence = confidence.asPercentage();
            String confidenceLevel = confidence.isHighConfidence() ? "HIGH"
                    : confidence.isLowConfidence() ? "LOW" : "MEDIUM";

            double fibonacciScore = safeDoubleValue(confidence.fibonacciScore()) * 100.0;
            double timeScore = safeDoubleValue(confidence.timeProportionScore()) * 100.0;
            double alternationScore = safeDoubleValue(confidence.alternationScore()) * 100.0;
            double channelScore = safeDoubleValue(confidence.channelScore()) * 100.0;
            double completenessScore = safeDoubleValue(confidence.completenessScore()) * 100.0;

            String direction = scenario.isBullish() ? "BULLISH" : "BEARISH";
            double invalidationPrice = safeDoubleValue(scenario.invalidationPrice());
            double primaryTarget = safeDoubleValue(scenario.primaryTarget());

            List<SwingData> swings = scenario.swings().stream().map(SwingData::from).toList();

            return new BaseCaseScenario(scenario.currentPhase(), scenario.type(), overallConfidence,
                    scenarioProbability, calibratedProbability, confidenceLevel, fibonacciScore, timeScore,
                    alternationScore, channelScore, completenessScore, confidence.primaryReason(),
                    confidence.weakestFactor(), direction, invalidationPrice, primaryTarget, swings);
        }
    }

    /**
     * Alternative scenario details.
     *
     * @param currentPhase          the phase this scenario assigns to current price
     *                              action
     * @param type                  pattern type classification
     * @param confidencePercent     overall confidence percentage (0-100)
     * @param scenarioProbability   raw scenario probability ratio (0.0-1.0)
     * @param calibratedProbability walk-forward calibrated scenario probability
     *                              ratio (0.0-1.0)
     * @param swings                swing sequence for building wave labels
     */
    public record AlternativeScenario(ElliottPhase currentPhase, ScenarioType type, double confidencePercent,
            @JsonAdapter(ScenarioProbabilityAdapter.class) double scenarioProbability,
            @JsonAdapter(ScenarioProbabilityAdapter.class) double calibratedProbability, List<SwingData> swings) {
        static AlternativeScenario from(ElliottScenario scenario, double scenarioProbability,
                double calibratedProbability) {
            List<SwingData> swings = scenario.swings().stream().map(SwingData::from).toList();
            return new AlternativeScenario(scenario.currentPhase(), scenario.type(),
                    scenario.confidence().asPercentage(), scenarioProbability, calibratedProbability, swings);
        }
    }

    /**
     * Swing data for building wave labels.
     *
     * @param fromIndex starting bar index
     * @param toIndex   ending bar index
     * @param fromPrice starting price
     * @param toPrice   ending price
     * @param isRising  whether the swing is rising
     */
    public record SwingData(int fromIndex, int toIndex, double fromPrice, double toPrice, boolean isRising) {
        static SwingData from(ElliottSwing swing) {
            return new SwingData(swing.fromIndex(), swing.toIndex(), safeDoubleValue(swing.fromPrice()),
                    safeDoubleValue(swing.toPrice()), swing.isRising());
        }
    }

    /**
     * Computes scenario probabilities by applying adaptive contrast to confidence
     * scores, then tilting toward overlapping consensus factors (phase, type, and
     * direction).
     *
     * @param scenarioSet scenario set to evaluate
     * @return scenario probability ratios keyed by scenario id
     */
    static Map<String, Double> computeScenarioProbabilities(ElliottScenarioSet scenarioSet) {
        Objects.requireNonNull(scenarioSet, "scenarioSet");
        List<ElliottScenario> scenarios = scenarioSet.all();
        if (scenarios.isEmpty()) {
            return Map.of();
        }

        EnumMap<ElliottPhase, Integer> phaseCounts = new EnumMap<>(ElliottPhase.class);
        EnumMap<ScenarioType, Integer> typeCounts = new EnumMap<>(ScenarioType.class);
        double minConfidence = Double.POSITIVE_INFINITY;
        double maxConfidence = Double.NEGATIVE_INFINITY;
        int scenarioCount = scenarios.size();
        double[] confidenceScores = new double[scenarioCount];
        int knownPhaseCount = 0;
        int knownTypeCount = 0;
        int bullishCount = 0;
        int bearishCount = 0;
        int knownDirectionCount = 0;

        for (int i = 0; i < scenarioCount; i++) {
            ElliottScenario scenario = scenarios.get(i);
            ElliottPhase phase = scenario.currentPhase();
            if (phase != ElliottPhase.NONE) {
                phaseCounts.merge(phase, 1, Integer::sum);
                knownPhaseCount++;
            }

            ScenarioType type = scenario.type();
            if (type != ScenarioType.UNKNOWN) {
                typeCounts.merge(type, 1, Integer::sum);
                knownTypeCount++;
            }

            if (scenario.hasKnownDirection()) {
                knownDirectionCount++;
                if (scenario.isBullish()) {
                    bullishCount++;
                } else {
                    bearishCount++;
                }
            }

            double confidence = safeScoreValue(scenario.confidenceScore());
            confidenceScores[i] = confidence;
            minConfidence = Math.min(minConfidence, confidence);
            maxConfidence = Math.max(maxConfidence, confidence);
        }

        double[] baseWeights = new double[scenarioCount];
        double totalConfidence = 0.0;
        double contrastExponent = confidenceContrastExponent(minConfidence, maxConfidence, scenarioCount);
        for (int i = 0; i < scenarioCount; i++) {
            double confidence = confidenceScores[i];
            double contrasted = applyConfidenceContrast(confidence, contrastExponent);
            baseWeights[i] = contrasted;
            totalConfidence += contrasted;
        }
        if (totalConfidence > 0.0) {
            for (int i = 0; i < scenarioCount; i++) {
                baseWeights[i] /= totalConfidence;
            }
        } else {
            double equalWeight = 1.0 / scenarioCount;
            for (int i = 0; i < scenarioCount; i++) {
                baseWeights[i] = equalWeight;
            }
        }

        double[] overlapScores = new double[scenarioCount];
        double overlapTotal = 0.0;
        int overlapCount = 0;
        for (int i = 0; i < scenarioCount; i++) {
            ElliottScenario scenario = scenarios.get(i);
            double overlapScore = overlapScoreForScenario(scenario, phaseCounts, knownPhaseCount, typeCounts,
                    knownTypeCount, bullishCount, bearishCount, knownDirectionCount);
            overlapScores[i] = overlapScore;
            if (overlapScore > 0.0) {
                overlapTotal += overlapScore;
                overlapCount++;
            }
        }
        double averageOverlap = overlapCount > 0 ? overlapTotal / overlapCount : 0.0;

        double[] adjustedWeights = new double[scenarioCount];
        double adjustedTotal = 0.0;
        for (int i = 0; i < scenarioCount; i++) {
            double overlapScore = overlapScores[i];
            double multiplier = overlapScore > 0.0
                    ? 1.0 + (CONSENSUS_ADJUSTMENT_WEIGHT * (overlapScore - averageOverlap))
                    : 1.0;
            double adjustedWeight = baseWeights[i] * multiplier;
            adjustedWeights[i] = adjustedWeight;
            adjustedTotal += adjustedWeight;
        }

        Map<String, Double> probabilities = new HashMap<>();
        if (adjustedTotal <= 0.0) {
            double fallback = 1.0 / scenarioCount;
            for (ElliottScenario scenario : scenarios) {
                probabilities.put(scenario.id(), fallback);
            }
            return Map.copyOf(probabilities);
        }

        for (int i = 0; i < scenarioCount; i++) {
            ElliottScenario scenario = scenarios.get(i);
            double probability = adjustedWeights[i] / adjustedTotal;
            probabilities.put(scenario.id(), probability);
        }
        return Map.copyOf(probabilities);
    }

    /**
     * Applies walk-forward-informed probability calibration using centered
     * shrinkage followed by renormalization.
     *
     * <p>
     * The transform shrinks probabilities toward the uniform prior to reduce
     * over-confident tails while preserving ordering signals from the raw scenario
     * model.
     */
    private static CalibrationResult calibrateScenarioProbabilities(Map<String, Double> rawProbabilities,
            ScenarioSummary summary, ElliottTrendBias trendBias) {
        if (rawProbabilities == null || rawProbabilities.isEmpty()) {
            ProbabilityCalibration calibration = ProbabilityCalibration.from(CALIBRATION_BASE_SHRINK_FACTOR);
            return new CalibrationResult(Map.of(), calibration);
        }

        Map<String, Double> normalizedRaw = normalizeProbabilityMap(rawProbabilities);
        if (normalizedRaw.isEmpty()) {
            ProbabilityCalibration calibration = ProbabilityCalibration.from(CALIBRATION_BASE_SHRINK_FACTOR);
            return new CalibrationResult(Map.of(), calibration);
        }

        double shrinkFactor = CALIBRATION_BASE_SHRINK_FACTOR;
        if (summary != null && summary.strongConsensus()) {
            shrinkFactor += CALIBRATION_STRONG_CONSENSUS_BONUS;
        }
        if (trendBias != null && trendBias.consensus()) {
            shrinkFactor += CALIBRATION_DIRECTIONAL_CONSENSUS_BONUS;
        }
        if (trendBias == null || trendBias.isUnknown() || trendBias.isNeutral()
                || !Double.isFinite(trendBias.strength()) || trendBias.strength() < OUTLOOK_MIN_TREND_STRENGTH) {
            shrinkFactor -= CALIBRATION_WEAK_TREND_PENALTY;
        }
        shrinkFactor = clamp(shrinkFactor, CALIBRATION_MIN_SHRINK_FACTOR, CALIBRATION_MAX_SHRINK_FACTOR);

        int scenarioCount = normalizedRaw.size();
        double uniformPrior = 1.0 / scenarioCount;
        Map<String, Double> centered = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : normalizedRaw.entrySet()) {
            double raw = entry.getValue();
            double calibrated = uniformPrior + (shrinkFactor * (raw - uniformPrior));
            centered.put(entry.getKey(), Math.max(CALIBRATION_EPSILON, calibrated));
        }

        Map<String, Double> calibratedProbabilities = normalizeProbabilityMap(centered);
        ProbabilityCalibration calibration = ProbabilityCalibration.from(shrinkFactor);
        return new CalibrationResult(calibratedProbabilities, calibration);
    }

    private static double overlapScoreForScenario(ElliottScenario scenario, EnumMap<ElliottPhase, Integer> phaseCounts,
            int knownPhaseCount, EnumMap<ScenarioType, Integer> typeCounts, int knownTypeCount, int bullishCount,
            int bearishCount, int knownDirectionCount) {
        double weightedSum = 0.0;
        double weightTotal = 0.0;

        ElliottPhase phase = scenario.currentPhase();
        if (phase != ElliottPhase.NONE && knownPhaseCount > 0) {
            weightedSum += PHASE_OVERLAP_WEIGHT * (phaseCounts.getOrDefault(phase, 0) / (double) knownPhaseCount);
            weightTotal += PHASE_OVERLAP_WEIGHT;
        }

        ScenarioType type = scenario.type();
        if (type != ScenarioType.UNKNOWN && knownTypeCount > 0) {
            weightedSum += SCENARIO_TYPE_OVERLAP_WEIGHT * (typeCounts.getOrDefault(type, 0) / (double) knownTypeCount);
            weightTotal += SCENARIO_TYPE_OVERLAP_WEIGHT;
        }

        if (scenario.hasKnownDirection() && knownDirectionCount > 0) {
            double directionOverlap = scenario.isBullish() ? (double) bullishCount / knownDirectionCount
                    : (double) bearishCount / knownDirectionCount;
            weightedSum += DIRECTION_OVERLAP_WEIGHT * directionOverlap;
            weightTotal += DIRECTION_OVERLAP_WEIGHT;
        }

        if (weightTotal <= 0.0) {
            return 0.0;
        }

        return weightedSum / weightTotal;
    }

    private static double confidenceContrastExponent(double minConfidence, double maxConfidence, int scenarioCount) {
        if (scenarioCount <= 1) {
            return 1.0;
        }
        double spread = maxConfidence - minConfidence;
        if (spread <= 0.0) {
            return MAX_CONFIDENCE_CONTRAST_EXPONENT;
        }
        double normalizedSpread = Math.min(1.0, spread / CONFIDENCE_SPREAD_TARGET);
        return MIN_CONFIDENCE_CONTRAST_EXPONENT
                + (MAX_CONFIDENCE_CONTRAST_EXPONENT - MIN_CONFIDENCE_CONTRAST_EXPONENT) * (1.0 - normalizedSpread);
    }

    private static double applyConfidenceContrast(double confidence, double exponent) {
        if (confidence <= 0.0) {
            return 0.0;
        }
        if (exponent <= 1.0) {
            return confidence;
        }
        return Math.pow(confidence, exponent);
    }

    private static Map<String, Double> normalizeProbabilityMap(Map<String, Double> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> finitePositive = new LinkedHashMap<>();
        double total = 0.0;
        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Double raw = entry.getValue();
            if (raw == null || !Double.isFinite(raw)) {
                continue;
            }
            double sanitized = Math.max(0.0, raw.doubleValue());
            if (sanitized <= 0.0) {
                continue;
            }
            finitePositive.put(entry.getKey(), sanitized);
            total += sanitized;
        }
        if (finitePositive.isEmpty() || total <= CALIBRATION_EPSILON) {
            return Map.of();
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : finitePositive.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / total);
        }
        return Map.copyOf(normalized);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Encodes a chart plan as a base64-encoded PNG image string.
     *
     * @param chartWorkflow the chart workflow for rendering
     * @param chartPlan     the chart plan to encode
     * @return base64-encoded PNG image string, or null if encoding fails
     */
    private static String encodeChartAsBase64(ChartWorkflow chartWorkflow, ChartPlan chartPlan) {
        try {
            byte[] pngBytes = chartWorkflow.getChartAsByteArray(chartWorkflow.render(chartPlan));
            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception ex) {
            LOG.warn("Chart encoding failed for chart plan {}: {}", chartPlan, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Safely converts a Num to a confidence score, treating invalid values as zero.
     *
     * @param num the numeric value to convert
     * @return double value, or 0.0 if null or invalid
     */
    private static double safeScoreValue(Num num) {
        if (num == null || !Num.isValid(num)) {
            return 0.0;
        }
        return num.doubleValue();
    }

    private static double roundScenarioProbability(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Safely converts a Num to double, handling null and NaN cases.
     *
     * @param num the numeric value to convert
     * @return double value, or NaN if null or invalid
     */
    private static double safeDoubleValue(Num num) {
        if (num == null || !Num.isValid(num)) {
            return Double.NaN;
        }
        return num.doubleValue();
    }

    private static final class ScenarioProbabilityAdapter extends TypeAdapter<Double> {
        @Override
        public void write(JsonWriter out, Double value) throws IOException {
            if (value == null || value.isNaN() || value.isInfinite()) {
                out.nullValue();
                return;
            }
            out.value(roundScenarioProbability(value));
        }

        @Override
        public Double read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return in.nextDouble();
        }
    }
}
