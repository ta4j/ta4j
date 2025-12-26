/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.analysis.elliottwave;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.indicators.elliott.ElliottRatioIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.Num;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Domain model capturing all Elliott Wave analysis results currently logged via
 * {@link ElliottWaveAnalysis#logBaseCaseScenario(ElliottScenario)} and
 * {@link ElliottWaveAnalysis#logAlternativeScenarios(List)}.
 * <p>
 * This class provides structured access to analysis results including swing
 * snapshots, phase information, ratio and channel data, confluence scores,
 * scenario summaries, and detailed base case and alternative scenario
 * information.
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
 * @see ElliottWaveAnalysis
 * @since 0.22.0
 */
public record ElliottWaveAnalysisResult(ElliottDegree degree, int endIndex, SwingSnapshot swingSnapshot,
        LatestAnalysis latestAnalysis, ScenarioSummary scenarioSummary, BaseCaseScenario baseCase,
        List<AlternativeScenario> alternatives, String baseCaseChartImage, List<String> alternativeChartImages) {

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
    public static ElliottWaveAnalysisResult from(ElliottDegree degree, ElliottSwingMetadata swingMetadata,
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
        BaseCaseScenario baseCase = scenarioSet.base().map(BaseCaseScenario::from).orElse(null);
        List<AlternativeScenario> alternatives = scenarioSet.alternatives()
                .stream()
                .map(AlternativeScenario::from)
                .toList();

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        String baseCaseChartImage = baseCaseChartPlan.map(plan -> encodeChartAsBase64(chartWorkflow, plan))
                .orElse(null);
        List<String> alternativeChartImages = alternativeChartPlans.stream()
                .map(plan -> encodeChartAsBase64(chartWorkflow, plan))
                .toList();

        return new ElliottWaveAnalysisResult(degree, endIndex, snapshot, latest, summary, baseCase, alternatives,
                baseCaseChartImage, alternativeChartImages);
    }

    /**
     * Serializes this analysis result to JSON using Gson.
     *
     * @return JSON representation of the analysis result
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
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
     * Base case scenario details.
     *
     * @param currentPhase      the phase this scenario assigns to current price
     *                          action
     * @param type              pattern type classification
     * @param overallConfidence overall confidence percentage (0-100)
     * @param confidenceLevel   confidence level (HIGH, MEDIUM, or LOW)
     * @param fibonacciScore    Fibonacci proximity score as percentage (0-100)
     * @param timeScore         time proportion score as percentage (0-100)
     * @param alternationScore  alternation quality score as percentage (0-100)
     * @param channelScore      channel adherence score as percentage (0-100)
     * @param completenessScore structure completeness score as percentage (0-100)
     * @param primaryReason     human-readable description of dominant factor
     * @param weakestFactor     description of the weakest scoring factor
     * @param direction         direction (BULLISH or BEARISH)
     * @param invalidationPrice price level that would invalidate this count
     * @param primaryTarget     primary Fibonacci projection target
     * @param swings            swing sequence for building wave labels
     */
    public record BaseCaseScenario(ElliottPhase currentPhase, ScenarioType type, double overallConfidence,
            String confidenceLevel, double fibonacciScore, double timeScore, double alternationScore,
            double channelScore, double completenessScore, String primaryReason, String weakestFactor, String direction,
            double invalidationPrice, double primaryTarget, List<SwingData> swings) {
        static BaseCaseScenario from(ElliottScenario scenario) {
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

            return new BaseCaseScenario(scenario.currentPhase(), scenario.type(), overallConfidence, confidenceLevel,
                    fibonacciScore, timeScore, alternationScore, channelScore, completenessScore,
                    confidence.primaryReason(), confidence.weakestFactor(), direction, invalidationPrice, primaryTarget,
                    swings);
        }
    }

    /**
     * Alternative scenario details.
     *
     * @param currentPhase      the phase this scenario assigns to current price
     *                          action
     * @param type              pattern type classification
     * @param confidencePercent overall confidence percentage (0-100)
     * @param swings            swing sequence for building wave labels
     */
    public record AlternativeScenario(ElliottPhase currentPhase, ScenarioType type, double confidencePercent,
            List<SwingData> swings) {
        static AlternativeScenario from(ElliottScenario scenario) {
            List<SwingData> swings = scenario.swings().stream().map(SwingData::from).toList();
            return new AlternativeScenario(scenario.currentPhase(), scenario.type(),
                    scenario.confidence().asPercentage(), swings);
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
            // Log error but don't fail the entire result
            return null;
        }
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
}
