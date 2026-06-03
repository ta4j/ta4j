/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.ta4j.core.num.NaN.NaN;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottLogicProfile;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Generic macro-cycle demo facade for any instrument and timespan.
 *
 * <p>
 * Callers can either provide the exact {@link BarSeries} window plus an
 * {@link ElliottWaveAnchorCalibrationHarness.AnchorRegistry} that describes the
 * cycle anchors they want to validate, or let the demo infer broad macro turns
 * directly from the series. Both paths reuse the same historical study, chart
 * rendering, and JSON reporting flow that the BTC wrapper now delegates to.
 *
 * <p>
 * This class is intentionally the controller and rendering surface only. The
 * canonical profile sweep, truth-target scoring, and current-cycle profile
 * selection live behind the internal {@link CanonicalEngine} so the example API
 * can stay stable without leaking more surface area into ta4j-core.
 *
 * @since 0.22.4
 */
public final class ElliottWaveMacroCycleDemo {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMacroCycleDemo.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CANONICAL_STRUCTURE_SOURCE = "canonical-structure";
    private static final String HISTORICAL_STRUCTURE_FIT_PASSED = "historical structure fit passed";
    private static final String HISTORICAL_STRUCTURE_STILL_PARTIAL = "historical structure still partial";
    private static final String HISTORICAL_TRUTH_TARGET_FIT_PASSED = "historical truth-target fit passed";
    private static final String HISTORICAL_TRUTH_TARGET_STILL_PARTIAL = "historical truth-target fit still partial";
    private static final List<MacroLogicProfile> CANONICAL_LOGIC_PROFILES = List.of(
            new MacroLogicProfile("calibrated-baseline", "H0", "Calibrated baseline", 0,
                    ElliottLogicProfile.ORTHODOX_CLASSICAL, ElliottDegree.MINUTE, 2, 2),
            new MacroLogicProfile("h1-hierarchical-swing", "H1", "Hierarchical swing extraction", 1,
                    ElliottLogicProfile.HIERARCHICAL_SWING, ElliottDegree.MINUTE, null, null),
            new MacroLogicProfile("h2-pattern-aware-impulse", "H2", "Pattern-aware impulse emphasis", 2,
                    ElliottLogicProfile.BTC_RELAXED_IMPULSE, ElliottDegree.MINUTE, null, null),
            new MacroLogicProfile("h3-pattern-aware-corrective", "H3", "Pattern-aware corrective breadth", 3,
                    ElliottLogicProfile.BTC_RELAXED_CORRECTIVE, ElliottDegree.MINUTE, null, null),
            new MacroLogicProfile("h4-span-aware-hybrid", "H4", "Span-aware hybrid scoring", 4,
                    ElliottLogicProfile.ANCHOR_FIRST_HYBRID, ElliottDegree.MINUTE, null, null));
    private static final Comparator<MacroProfileEvaluation> PROFILE_EVALUATION_COMPARATOR = Comparator
            .comparing((MacroProfileEvaluation evaluation) -> evaluation.truthTargetCoverage().hasExpectations(),
                    Comparator.reverseOrder())
            .thenComparing(Comparator.comparingInt(MacroProfileEvaluation::matchedExpectedCycles).reversed())
            .thenComparingInt(MacroProfileEvaluation::missingExpectedCycles)
            .thenComparingInt(MacroProfileEvaluation::unexpectedCycles)
            .thenComparing(Comparator.comparingInt(MacroProfileEvaluation::acceptedCycles).reversed())
            .thenComparing(Comparator.comparingDouble(MacroProfileEvaluation::truthTargetScore).reversed())
            .thenComparing(Comparator.comparingDouble(MacroProfileEvaluation::aggregateScore).reversed())
            .thenComparing(Comparator.comparingInt(MacroProfileEvaluation::acceptedSegments).reversed())
            .thenComparing(Comparator.comparingLong(MacroProfileEvaluation::acceptedCycleSpanMillis).reversed())
            .thenComparing(MacroProfileEvaluation::earliestAcceptedStartTime)
            .thenComparing(Comparator.comparingInt((MacroProfileEvaluation evaluation) -> evaluation.cycleFits().size())
                    .reversed())
            .thenComparingInt(evaluation -> evaluation.profile().orthodoxyRank());
    private static final Comparator<MacroProfileEvaluation> RUNTIME_PROFILE_EVALUATION_COMPARATOR = Comparator
            .comparingInt((MacroProfileEvaluation evaluation) -> evaluation.cycleFits().size())
            .reversed()
            .thenComparing(Comparator.comparingLong(MacroProfileEvaluation::totalCycleSpanMillis).reversed())
            .thenComparing(MacroProfileEvaluation::earliestCycleStartTime)
            .thenComparing(Comparator.comparingInt(MacroProfileEvaluation::acceptedCycles).reversed())
            .thenComparing(Comparator.comparingDouble(MacroProfileEvaluation::aggregateScore).reversed())
            .thenComparing(Comparator.comparingInt(MacroProfileEvaluation::acceptedSegments).reversed())
            .thenComparingInt(evaluation -> evaluation.profile().orthodoxyRank());
    private static final CanonicalEngine CANONICAL_ENGINE = new CanonicalEngine(CANONICAL_LOGIC_PROFILES);

    private ElliottWaveMacroCycleDemo() {
    }

    /**
     * Generic historical macro-study report backed by the canonical analysis
     * surface.
     *
     * @param registryVersion          comparison registry version
     * @param datasetResource          source dataset resource identifier
     * @param baselineProfileId        canonical baseline profile id
     * @param selectedProfileId        selected profile id
     * @param selectedHypothesisId     selected hypothesis id
     * @param historicalFitPassed      whether completed-cycle truth matched within
     *                                 tolerance
     * @param harnessDecisionRationale summary selection rationale
     * @param chartPath                rendered chart artifact path
     * @param summaryPath              rendered summary artifact path
     * @param structureSource          provenance marker for the structure surface
     * @param profileScores            ordered profile score summaries
     * @param cycles                   completed historical cycles
     * @param hypotheses               ranked historical hypotheses
     * @param currentCycle             attached current-cycle view
     */
    record DemoReport(String registryVersion, String datasetResource, String baselineProfileId,
            String selectedProfileId, String selectedHypothesisId, boolean historicalFitPassed,
            String harnessDecisionRationale, String chartPath, String summaryPath, String structureSource,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleSummary currentCycle) {

        DemoReport {
            Objects.requireNonNull(registryVersion, "registryVersion");
            Objects.requireNonNull(datasetResource, "datasetResource");
            Objects.requireNonNull(baselineProfileId, "baselineProfileId");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(selectedHypothesisId, "selectedHypothesisId");
            Objects.requireNonNull(harnessDecisionRationale, "harnessDecisionRationale");
            Objects.requireNonNull(chartPath, "chartPath");
            Objects.requireNonNull(summaryPath, "summaryPath");
            Objects.requireNonNull(structureSource, "structureSource");
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    /**
     * Generic live preset report backed by the canonical analysis surface.
     *
     * @param seriesName           analyzed series name
     * @param startTimeUtc         first bar time in UTC
     * @param latestTimeUtc        latest bar time in UTC
     * @param selectedProfileId    selected profile id
     * @param selectedHypothesisId selected hypothesis id
     * @param chartPath            rendered chart artifact path
     * @param summaryPath          rendered summary artifact path
     * @param structureSource      provenance marker for the structure surface
     * @param currentCycle         attached current-cycle view
     */
    record LivePresetReport(String seriesName, String startTimeUtc, String latestTimeUtc, String selectedProfileId,
            String selectedHypothesisId, String chartPath, String summaryPath, String structureSource,
            CurrentCycleSummary currentCycle) {

        LivePresetReport {
            Objects.requireNonNull(seriesName, "seriesName");
            Objects.requireNonNull(startTimeUtc, "startTimeUtc");
            Objects.requireNonNull(latestTimeUtc, "latestTimeUtc");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(selectedHypothesisId, "selectedHypothesisId");
            Objects.requireNonNull(chartPath, "chartPath");
            Objects.requireNonNull(summaryPath, "summaryPath");
            Objects.requireNonNull(structureSource, "structureSource");
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    record ProfileScoreSummary(String profileId, String hypothesisId, String title, double aggregateScore,
            double truthTargetScore, int acceptedCycles, int acceptedSegments, int matchedExpectedCycles,
            int missingExpectedCycles, int unexpectedCycles, boolean historicalFitPassed) {

        static ProfileScoreSummary from(final MacroProfileEvaluation evaluation) {
            return new ProfileScoreSummary(evaluation.profile().id(), evaluation.profile().hypothesisId(),
                    evaluation.profile().title(), evaluation.aggregateScore(), evaluation.truthTargetScore(),
                    evaluation.acceptedCycles(), evaluation.acceptedSegments(), evaluation.matchedExpectedCycles(),
                    evaluation.missingExpectedCycles(), evaluation.unexpectedCycles(),
                    evaluation.historicalFitPassed());
        }
    }

    record DirectionalCycleSummary(String partition, String cycleId, String impulseLabel, String peakLabel,
            String correctionLabel, String lowLabel, String startTimeUtc, String peakTimeUtc, String lowTimeUtc,
            double bullishScore, double bearishScore, boolean bullishAccepted, boolean bearishAccepted,
            boolean accepted, String status) {

        static DirectionalCycleSummary from(final CycleFit cycleFit) {
            final MacroCycle cycle = cycleFit.cycle();
            final String status;
            if (cycleFit.accepted()) {
                status = "accepted historical fit";
            } else if (cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted()) {
                status = "bullish fit only";
            } else if (cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted()) {
                status = "bearish fit only";
            } else {
                status = "fallback or miss";
            }
            return new DirectionalCycleSummary(cycle.partition(), cycle.id(), "Bullish 1-2-3-4-5", "Bullish WAVE5 top",
                    "Bearish A-B-C", "Bearish CORRECTIVE_C low", cycle.start().at().toString(),
                    cycle.peak().at().toString(), cycle.low().at().toString(),
                    cycleFit.bullishFit() == null ? 0.0 : cycleFit.bullishFit().fitScore(),
                    cycleFit.bearishFit() == null ? 0.0 : cycleFit.bearishFit().fitScore(),
                    cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted(),
                    cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted(), cycleFit.accepted(), status);
        }
    }

    record HypothesisResult(String id, String title, boolean supported, String summary, Map<String, String> evidence) {

        HypothesisResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            evidence = evidence == null ? Map.of() : new TreeMap<>(evidence);
        }
    }

    /**
     * User-facing summary of the right-edge macro-cycle view.
     *
     * @param startTimeUtc                inferred cycle start time in UTC
     * @param latestTimeUtc               latest analyzed bar time in UTC
     * @param winningProfileId            profile that produced the selected live
     *                                    view
     * @param historicalStatus            compact relationship to the historical
     *                                    study
     * @param primaryCount                primary live Elliott count label
     * @param alternateCount              alternate live Elliott count label
     * @param currentWave                 inferred current wave/phase label
     * @param invalidationPrice           price that invalidates the primary count
     * @param structuralInvalidationPrice broader structural invalidation level
     * @param orthodoxWaveFiveTargetRange formatted orthodox target range
     * @param primaryScore                normalized score for the primary count
     * @param alternateScore              normalized score for the alternate count
     * @param chartPath                   persisted chart artifact path
     */
    record CurrentCycleSummary(String startTimeUtc, String latestTimeUtc, String winningProfileId,
            String historicalStatus, String primaryCount, String alternateCount, String currentWave,
            String invalidationPrice, String structuralInvalidationPrice, String orthodoxWaveFiveTargetRange,
            double primaryScore, double alternateScore, String chartPath) {

        CurrentCycleSummary withChartPath(final String newChartPath) {
            return new CurrentCycleSummary(startTimeUtc, latestTimeUtc, winningProfileId, historicalStatus,
                    primaryCount, alternateCount, currentWave, invalidationPrice, structuralInvalidationPrice,
                    orthodoxWaveFiveTargetRange, primaryScore, alternateScore, newChartPath);
        }
    }

    /**
     * Canonical live-cycle analysis plus the retained ranked candidates that feed
     * charts and JSON reports.
     *
     * @param summary           user-facing summary for the selected live view
     * @param primaryFit        selected primary current-cycle fit
     * @param alternateFit      optional alternate current-cycle fit
     * @param candidates        all ranked current-cycle candidates
     * @param displayCandidates candidates retained for legacy chart persistence
     */
    record CurrentCycleAnalysis(CurrentCycleSummary summary,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit,
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates,
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> displayCandidates) {

        CurrentCycleAnalysis {
            Objects.requireNonNull(summary, "summary");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            displayCandidates = displayCandidates == null ? List.of() : List.copyOf(displayCandidates);
        }

        CurrentCycleAnalysis withSummary(final CurrentCycleSummary updatedSummary) {
            return new CurrentCycleAnalysis(updatedSummary, primaryFit, alternateFit, candidates, displayCandidates);
        }
    }

    /**
     * Historical macro-study assembled from the canonical structure search.
     *
     * @param selectedProfile      winning historical profile evaluation
     * @param evaluations          all ordered profile evaluations
     * @param profileScores        compact score table for reporting
     * @param cycles               accepted completed macro cycles
     * @param hypotheses           ranked historical hypotheses for the report
     * @param currentCycleAnalysis attached current-cycle analysis derived from the
     *                             same canonical engine
     */
    record MacroStudy(MacroProfileEvaluation selectedProfile, List<MacroProfileEvaluation> evaluations,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleAnalysis currentCycleAnalysis) {

        MacroStudy {
            Objects.requireNonNull(selectedProfile, "selectedProfile");
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycleAnalysis, "currentCycleAnalysis");
        }

        CurrentCycleSummary currentCycle() {
            return currentCycleAnalysis.summary();
        }

        ElliottWaveAnalysisResult.CurrentPhaseAssessment currentPrimaryFit() {
            return currentCycleAnalysis.primaryFit();
        }

        ElliottWaveAnalysisResult.CurrentPhaseAssessment currentAlternateFit() {
            return currentCycleAnalysis.alternateFit();
        }
    }

    /**
     * Truth-target coverage summary used to rank historical candidates against the
     * committed validation cycles.
     *
     * @param expectedCycleCount      number of expected cycles in the truth target
     * @param matchedExpectedCycles   number of expected cycles recovered in-order
     * @param missingExpectedCycles   number of expected cycles not recovered
     * @param unexpectedCycles        number of extra unmatched accepted cycles
     * @param missingExpectedCycleIds ids of missing expected cycles
     * @param unexpectedCycleIds      ids of extra unexpected cycles
     */
    record TruthTargetCoverage(int expectedCycleCount, int matchedExpectedCycles, int missingExpectedCycles,
            int unexpectedCycles, List<String> missingExpectedCycleIds, List<String> unexpectedCycleIds) {

        TruthTargetCoverage {
            missingExpectedCycleIds = missingExpectedCycleIds == null ? List.of()
                    : List.copyOf(missingExpectedCycleIds);
            unexpectedCycleIds = unexpectedCycleIds == null ? List.of() : List.copyOf(unexpectedCycleIds);
        }

        static TruthTargetCoverage none() {
            return new TruthTargetCoverage(0, 0, 0, 0, List.of(), List.of());
        }

        boolean hasExpectations() {
            return expectedCycleCount > 0;
        }

        boolean complete() {
            return hasExpectations() && missingExpectedCycles == 0 && unexpectedCycles == 0;
        }

        double score() {
            if (!hasExpectations()) {
                return 0.0;
            }
            final double expected = Math.max(1.0, expectedCycleCount);
            final double matchedRatio = matchedExpectedCycles / expected;
            final double missingPenalty = missingExpectedCycles / expected;
            final double unexpectedPenalty = unexpectedCycles / expected;
            return Math.max(0.0, Math.min(1.0, matchedRatio - (0.45 * missingPenalty) - (0.25 * unexpectedPenalty)));
        }
    }

    /**
     * Historical evaluation of one canonical logic profile.
     *
     * @param profile             evaluated logic profile
     * @param aggregateScore      aggregate internal score across accepted segments
     * @param acceptedCycles      number of accepted completed cycles
     * @param acceptedSegments    number of accepted leg segments
     * @param historicalFitPassed whether the profile cleared the historical
     *                            acceptance bar
     * @param truthTargetCoverage committed-cycle coverage summary
     * @param cycleFits           accepted completed cycle fits
     * @param chartSegments       ranked segment fits used for rendering
     */
    record MacroProfileEvaluation(MacroLogicProfile profile, double aggregateScore, int acceptedCycles,
            int acceptedSegments, boolean historicalFitPassed, TruthTargetCoverage truthTargetCoverage,
            List<CycleFit> cycleFits, List<SegmentScenarioFit> chartSegments) {

        MacroProfileEvaluation {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(truthTargetCoverage, "truthTargetCoverage");
            cycleFits = cycleFits == null ? List.of() : List.copyOf(cycleFits);
            chartSegments = chartSegments == null ? List.of() : List.copyOf(chartSegments);
        }

        long acceptedCycleSpanMillis() {
            return cycleFits.stream()
                    .filter(CycleFit::accepted)
                    .mapToLong(cycleFit -> Duration.between(cycleFit.cycle().start().at(), cycleFit.cycle().low().at())
                            .toMillis())
                    .sum();
        }

        long totalCycleSpanMillis() {
            return cycleFits.stream()
                    .mapToLong(cycleFit -> Duration.between(cycleFit.cycle().start().at(), cycleFit.cycle().low().at())
                            .toMillis())
                    .sum();
        }

        Instant earliestCycleStartTime() {
            return cycleFits.stream()
                    .map(cycleFit -> cycleFit.cycle().start().at())
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.MAX);
        }

        Instant earliestAcceptedStartTime() {
            return cycleFits.stream()
                    .filter(CycleFit::accepted)
                    .map(cycleFit -> cycleFit.cycle().start().at())
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.MAX);
        }

        double truthTargetScore() {
            if (!truthTargetCoverage.hasExpectations()) {
                return aggregateScore;
            }
            return truthTargetCoverage.score();
        }

        int matchedExpectedCycles() {
            return truthTargetCoverage.matchedExpectedCycles();
        }

        int missingExpectedCycles() {
            return truthTargetCoverage.missingExpectedCycles();
        }

        int unexpectedCycles() {
            return truthTargetCoverage.unexpectedCycles();
        }
    }

    /**
     * Paired historical evaluations derived from one canonical structure search.
     *
     * @param runtimeEvaluation     series-native runtime evaluation
     * @param truthTargetEvaluation registry-scored truth-target evaluation
     */
    record HistoricalProfileEvaluations(MacroProfileEvaluation runtimeEvaluation,
            MacroProfileEvaluation truthTargetEvaluation) {

        HistoricalProfileEvaluations {
            Objects.requireNonNull(runtimeEvaluation, "runtimeEvaluation");
            Objects.requireNonNull(truthTargetEvaluation, "truthTargetEvaluation");
        }
    }

    /**
     * Internal canonical profile descriptor used by the demo controller and
     * calibration harness.
     *
     * @param id                    stable profile id
     * @param hypothesisId          short hypothesis label for reports
     * @param title                 human-readable profile title
     * @param orthodoxyRank         deterministic tie-break rank
     * @param coreLogicProfile      ta4j-core logic profile to execute
     * @param runnerDegree          primary runner degree
     * @param higherDegreesOverride optional higher-degree override for the runner
     * @param lowerDegreesOverride  optional lower-degree override for the runner
     */
    record MacroLogicProfile(String id, String hypothesisId, String title, int orthodoxyRank,
            ElliottLogicProfile coreLogicProfile, ElliottDegree runnerDegree, Integer higherDegreesOverride,
            Integer lowerDegreesOverride) {

        MacroLogicProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hypothesisId, "hypothesisId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(coreLogicProfile, "coreLogicProfile");
            Objects.requireNonNull(runnerDegree, "runnerDegree");
        }

        int runnerHigherDegrees() {
            return higherDegreesOverride == null ? coreLogicProfile.higherDegrees() : higherDegreesOverride.intValue();
        }

        int runnerLowerDegrees() {
            return lowerDegreesOverride == null ? coreLogicProfile.lowerDegrees() : lowerDegreesOverride.intValue();
        }

        int runnerMaxScenarios() {
            return coreLogicProfile.maxScenarios();
        }

        int runnerScenarioSwingWindow() {
            return coreLogicProfile.scenarioSwingWindow();
        }

        double acceptanceThreshold() {
            return coreLogicProfile.acceptanceThreshold();
        }
    }

    record MacroCycle(String id, String partition, ElliottWaveAnchorCalibrationHarness.Anchor start,
            ElliottWaveAnchorCalibrationHarness.Anchor peak, ElliottWaveAnchorCalibrationHarness.Anchor low,
            LegSegment bullishLeg, LegSegment bearishLeg) {
    }

    record CycleFit(MacroCycle cycle, SegmentScenarioFit bullishFit, SegmentScenarioFit bearishFit,
            double aggregateScore, boolean accepted) {

        static CycleFit create(final MacroCycle cycle, final SegmentScenarioFit bullishFit,
                final SegmentScenarioFit bearishFit) {
            final double bullishScore = bullishFit == null ? 0.0 : bullishFit.fitScore();
            final double bearishScore = bearishFit == null ? 0.0 : bearishFit.fitScore();
            final double aggregate = (bullishScore + bearishScore) / 2.0;
            final boolean accepted = bullishFit != null && bullishFit.accepted() && bearishFit != null
                    && bearishFit.accepted();
            return new CycleFit(cycle, bullishFit, bearishFit, aggregate, accepted);
        }
    }

    record LegSegment(ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor,
            ElliottWaveAnchorCalibrationHarness.Anchor toAnchor, boolean bullish) {

        LegSegment {
            Objects.requireNonNull(fromAnchor, "fromAnchor");
            Objects.requireNonNull(toAnchor, "toAnchor");
        }
    }

    record SegmentScenarioFit(LegSegment segment, ElliottScenario scenario, double fitScore, double structureScore,
            double ruleScore, double spacingScore, double strengthScore, boolean bullish, boolean accepted,
            String rationale) implements Comparable<SegmentScenarioFit> {

        @Override
        public int compareTo(final SegmentScenarioFit other) {
            final int acceptedComparison = Boolean.compare(other.accepted, accepted);
            if (acceptedComparison != 0) {
                return acceptedComparison;
            }
            final int scoreComparison = Double.compare(other.fitScore, fitScore);
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return scenario.id().compareTo(other.scenario.id());
        }

        boolean eyeballPass() {
            return accepted;
        }
    }

    record CanonicalReportPair(CanonicalStructure structure, DemoReport historicalReport, LivePresetReport liveReport) {

        CanonicalReportPair {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(historicalReport, "historicalReport");
            Objects.requireNonNull(liveReport, "liveReport");
        }
    }

    /**
     * Runs the historical macro-cycle report using anchors inferred directly from
     * the supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static DemoReport generateHistoricalReport(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final CanonicalStructure structure = analyzeCanonicalStructure(series);
        return buildHistoricalReport(series, structure, chartDirectory, "series-native-canonical-history",
                series.getName(),
                "Series-native canonical historical structure selected directly from core-ranked completed cycles");
    }

    /**
     * Runs the historical macro-cycle report for the supplied series and anchor
     * registry.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static DemoReport generateHistoricalReport(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final CanonicalStructure structure = analyzeCanonicalStructure(series, registry);
        return buildHistoricalReport(series, structure, chartDirectory, registry.version(), registry.datasetResource(),
                "Macro-cycle decomposition scored against the supplied truth-target registry while current-cycle inference remains series-native");
    }

    /**
     * Runs a live current-cycle macro report directly on the supplied series using
     * generic instrument-aware output names.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered charts and JSON summary
     * @since 0.22.4
     */
    public static void runLivePreset(final BarSeries series, final Path chartDirectory) {
        final String seriesToken = scenarioSeriesName(series);
        runLivePreset(series, chartDirectory, liveCurrentCycleChartFileName(seriesToken),
                liveSummaryFileName(seriesToken), seriesToken,
                "Series-native current-cycle inference using the default orthodox macro profile");
    }

    /**
     * Generates the live current-cycle report directly on the supplied series using
     * generic instrument-aware output names.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated live current-cycle report
     * @since 0.22.4
     */
    public static LivePresetReport generateLivePresetReport(final BarSeries series, final Path chartDirectory) {
        final String seriesToken = scenarioSeriesName(series);
        return generateLivePresetReport(series, chartDirectory, liveCurrentCycleChartFileName(seriesToken),
                liveSummaryFileName(seriesToken),
                "Series-native current-cycle inference using the default orthodox macro profile");
    }

    static CanonicalReportPair generateCanonicalReportPair(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        final CanonicalStructure structure = analyzeCanonicalStructure(series);
        final DemoReport historicalReport = buildHistoricalReport(series, structure, chartDirectory,
                "series-native-canonical-history", series.getName(),
                "Series-native canonical historical structure selected directly from core-ranked completed cycles");
        final String seriesToken = scenarioSeriesName(series);
        final LivePresetExecution liveExecution = analyzeLivePreset(series, structure, chartDirectory,
                liveCurrentCycleChartFileName(seriesToken), liveSummaryFileName(seriesToken),
                "Series-native current-cycle inference using the default orthodox macro profile");
        return new CanonicalReportPair(structure, historicalReport, liveExecution.report());
    }

    static void runLivePreset(final BarSeries series, final Path chartDirectory, final String chartFileName,
            final String summaryFileName, final String scenarioSeriesToken, final String historicalStatus) {
        final LivePresetExecution execution = analyzeLivePreset(series, chartDirectory, chartFileName, summaryFileName,
                historicalStatus);
        final LivePresetLegacyView legacyView = generateLivePresetLegacyView(series, scenarioSeriesToken,
                execution.currentCycle(), execution.report(), chartDirectory);
        logLegacyCompatibleLivePreset(legacyView);
    }

    static LivePresetReport generateLivePresetReport(final BarSeries series, final Path chartDirectory,
            final String chartFileName, final String summaryFileName, final String historicalStatus) {
        return analyzeLivePreset(series, chartDirectory, chartFileName, summaryFileName, historicalStatus).report();
    }

    private static DemoReport buildHistoricalReport(final BarSeries series, final CanonicalStructure structure,
            final Path chartDirectory, final String registryVersion, final String datasetResource,
            final String rationale) {
        final MacroStudy study = structure.historicalStudy().orElseThrow();
        final Optional<Path> chartPath = saveHistoricalChart(series, study, chartDirectory);
        final String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        final Path summaryPath = chartDirectory.resolve(ElliottWaveBtcMacroCycleDemo.DEFAULT_SUMMARY_FILE_NAME)
                .toAbsolutePath()
                .normalize();
        final String baselineProfileId = ElliottWaveAnchorCalibrationHarness.canonicalBtcCalibratedProfile().id();
        final CurrentCycleSummary currentCycle = structure.currentCycle().summary().withChartPath(chartPathText);
        final DemoReport report = new DemoReport(registryVersion, datasetResource, baselineProfileId,
                study.selectedProfile().profile().id(), study.selectedProfile().profile().hypothesisId(),
                study.selectedProfile().historicalFitPassed(), rationale, chartPathText, summaryPath.toString(),
                CANONICAL_STRUCTURE_SOURCE, study.profileScores(), study.cycles(), study.hypotheses(), currentCycle);
        saveSummary(report.toJson(), summaryPath, "macro-cycle summary");
        return report;
    }

    /**
     * Saves the rendered macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final CanonicalStructure structure = analyzeCanonicalStructure(series);
        return saveHistoricalChart(series, structure.historicalStudy().orElseThrow(), chartDirectory);
    }

    /**
     * Saves the rendered macro-cycle chart for the supplied series and anchors.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final CanonicalStructure structure = analyzeCanonicalStructure(series, registry);
        return saveHistoricalChart(series, structure.historicalStudy().orElseThrow(), chartDirectory);
    }

    /**
     * Renders the macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series series window to analyze
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        final CanonicalStructure structure = analyzeCanonicalStructure(series);
        return renderHistoricalChart(series, structure.historicalStudy().orElseThrow());
    }

    /**
     * Renders the macro-cycle chart for the supplied series and anchors without
     * writing files.
     *
     * @param series   series window to analyze
     * @param registry anchor registry describing the macro-cycle turns to validate
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        final CanonicalStructure structure = analyzeCanonicalStructure(series, registry);
        return renderHistoricalChart(series, structure.historicalStudy().orElseThrow());
    }

    static CanonicalStructure analyzeCanonicalStructure(final BarSeries series) {
        return CANONICAL_ENGINE.analyzeCanonicalStructure(series);
    }

    static MacroStudy evaluateCanonicalMacroStudy(final BarSeries series) {
        return CANONICAL_ENGINE.evaluateCanonicalMacroStudy(series);
    }

    static MacroStudy evaluateMacroStudy(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return evaluateCanonicalMacroStudy(series, registry);
    }

    static MacroStudy evaluateCanonicalMacroStudy(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return CANONICAL_ENGINE.evaluateCanonicalMacroStudy(series, registry);
    }

    static List<MacroProfileEvaluation> evaluateCanonicalProfileSweep(final BarSeries series) {
        return CANONICAL_ENGINE.evaluateCanonicalProfileSweep(series);
    }

    static List<MacroProfileEvaluation> evaluateCanonicalProfileSweep(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return CANONICAL_ENGINE.evaluateCanonicalProfileSweep(series, registry);
    }

    static MacroStudy evaluateLegacyAnchoredStudyForHarnessComparison(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return CANONICAL_ENGINE.evaluateLegacyAnchoredStudyForHarnessComparison(series, registry);
    }

    static CanonicalStructure analyzeCanonicalStructure(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return CANONICAL_ENGINE.analyzeCanonicalStructure(series, registry);
    }

    static CurrentCycleAnalysis evaluateCurrentCycle(final BarSeries series, final MacroLogicProfile profile,
            final String historicalStatus) {
        return CANONICAL_ENGINE.evaluateCurrentCycle(series, profile, historicalStatus);
    }

    private static LivePresetExecution analyzeLivePreset(final BarSeries series, final Path chartDirectory,
            final String chartFileName, final String summaryFileName, final String historicalStatus) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        Objects.requireNonNull(chartFileName, "chartFileName");
        Objects.requireNonNull(summaryFileName, "summaryFileName");
        Objects.requireNonNull(historicalStatus, "historicalStatus");

        final CanonicalStructure structure = analyzeCanonicalStructure(series);
        return analyzeLivePreset(series, structure, chartDirectory, chartFileName, summaryFileName, historicalStatus);
    }

    private static LivePresetExecution analyzeLivePreset(final BarSeries series, final CanonicalStructure structure,
            final Path chartDirectory, final String chartFileName, final String summaryFileName,
            final String historicalStatus) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        Objects.requireNonNull(chartFileName, "chartFileName");
        Objects.requireNonNull(summaryFileName, "summaryFileName");
        Objects.requireNonNull(historicalStatus, "historicalStatus");

        final MacroStudy study = structure.historicalStudy().orElseThrow();
        final CurrentCycleAnalysis currentCycle = structure.currentCycle();
        final Optional<Path> chartPath = saveLiveCurrentCycleChart(series, structure, chartDirectory, chartFileName);
        final String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        final Path summaryPath = chartDirectory.resolve(summaryFileName).toAbsolutePath().normalize();
        final CurrentCycleSummary summary = currentCycle.summary().withChartPath(chartPathText);
        final LivePresetReport report = new LivePresetReport(series.getName(),
                series.getFirstBar().getEndTime().toString(), series.getLastBar().getEndTime().toString(),
                study.selectedProfile().profile().id(), study.selectedProfile().profile().hypothesisId(), chartPathText,
                summaryPath.toString(), CANONICAL_STRUCTURE_SOURCE, summary);
        saveSummary(report.toJson(), summaryPath, "live macro summary");
        return new LivePresetExecution(currentCycle.withSummary(summary), report);
    }

    static CanonicalStructure analyzeCanonicalStructure(final BarSeries series, final MacroLogicProfile profile,
            final String historicalStatus) {
        return CANONICAL_ENGINE.analyzeCanonicalStructure(series, profile, historicalStatus);
    }

    static List<MacroLogicProfile> logicProfiles() {
        return CANONICAL_ENGINE.logicProfiles();
    }

    static MacroLogicProfile defaultLiveMacroProfile() {
        return CANONICAL_ENGINE.defaultLiveMacroProfile();
    }

    static SegmentScenarioFit fitFromCoreAssessment(final LegSegment legSegment,
            final ElliottWaveAnalysisResult.WindowScenarioAssessment assessment, final boolean bullish,
            final boolean accepted) {
        final ElliottScenario scenario = assessment.scenario();
        final double structureScore = assessment.structureScore();
        final double ruleScore = assessment.ruleScore();
        final double spacingScore = assessment.spacingScore();
        final double strengthScore = assessment.strengthScore();
        final double fitScore = assessment.fitScore();
        return new SegmentScenarioFit(legSegment, scenario, fitScore, structureScore, ruleScore, spacingScore,
                strengthScore, bullish, accepted,
                bullish ? "Core-ranked anchored-window impulse fit" : "Core-ranked anchored-window corrective fit");
    }

    static List<BarLabel> buildWaveLabelsFromScenario(final BarSeries series, final ElliottScenario scenario,
            final Color labelColor) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(labelColor, "labelColor");

        final List<ElliottSwing> swings = scenario.swings();
        if (swings.isEmpty()) {
            return List.of();
        }

        final List<BarLabel> labels = new ArrayList<>();
        for (int index = 0; index < swings.size(); index++) {
            final ElliottSwing swing = swings.get(index);
            final boolean highPivot = swing.isRising();
            labels.add(new BarLabel(swing.toIndex(), offsetLabelValue(series, swing.toPrice(), highPivot),
                    waveLabelForPhase(scenario.currentPhase(), index), placementForPivot(highPivot), labelColor,
                    ElliottWaveBtcMacroCycleDemo.WAVE_LABEL_FONT_SCALE));
        }
        return List.copyOf(labels);
    }

    static double interpolateOverlayPrice(final double fromPrice, final double toPrice, final double progress) {
        final double clampedProgress = clamp(progress, 0.0, 1.0);
        if (!Double.isFinite(fromPrice) || !Double.isFinite(toPrice) || fromPrice <= 0.0 || toPrice <= 0.0) {
            return fromPrice + ((toPrice - fromPrice) * clampedProgress);
        }
        final double logFrom = Math.log(fromPrice);
        final double logTo = Math.log(toPrice);
        return Math.exp(logFrom + ((logTo - logFrom) * clampedProgress));
    }

    static String formatInvalidationCondition(final ElliottScenario scenario, final Num value) {
        if (scenario == null || value == null) {
            return "";
        }
        if (scenario.hasKnownDirection()) {
            return (scenario.isBullish() ? "<= " : ">= ") + formatNum(value);
        }
        return "= " + formatNum(value);
    }

    private static String orthodoxWaveFiveTargetRange(final ElliottWaveAnalysisResult.CurrentPhaseAssessment primary) {
        if (primary == null || primary.currentPhase() != ElliottPhase.WAVE4) {
            return "";
        }
        final ElliottScenario scenario = primary.scenario();
        if (scenario == null || !scenario.hasKnownDirection() || !scenario.isBullish()) {
            return "";
        }
        final List<ElliottSwing> swings = scenario.swings();
        if (swings.size() < 4) {
            return "";
        }
        final ElliottSwing wave1 = swings.get(0);
        final ElliottSwing wave3 = swings.get(2);
        final ElliottSwing wave4 = swings.get(3);
        final double waveZeroLow = wave1.fromPrice().doubleValue();
        final double waveOneHigh = wave1.toPrice().doubleValue();
        final double waveThreeHigh = wave3.toPrice().doubleValue();
        final double waveFourLow = wave4.toPrice().doubleValue();
        if (!Double.isFinite(waveZeroLow) || !Double.isFinite(waveOneHigh) || !Double.isFinite(waveThreeHigh)
                || !Double.isFinite(waveFourLow)) {
            return "";
        }
        final double waveOneLength = waveOneHigh - waveZeroLow;
        final double oneToThreeLength = waveThreeHigh - waveZeroLow;
        if (waveOneLength <= 0.0 || oneToThreeLength <= 0.0) {
            return "";
        }
        final double orthodoxLow = waveFourLow + (waveOneLength * 0.618);
        final double orthodoxEqualWaveOne = waveFourLow + waveOneLength;
        final double orthodoxExtended = waveFourLow + (oneToThreeLength * 0.618);
        final double lowerBound = Math.min(orthodoxLow, Math.min(orthodoxEqualWaveOne, orthodoxExtended));
        final double upperBound = Math.max(orthodoxLow, Math.max(orthodoxEqualWaveOne, orthodoxExtended));
        return formatPrice(lowerBound) + " to " + formatPrice(upperBound);
    }

    private static Optional<Path> saveLiveCurrentCycleChart(final BarSeries series, final CanonicalStructure structure,
            final Path chartDirectory, final String chartFileName) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        Objects.requireNonNull(chartFileName, "chartFileName");
        final CurrentCycleAnalysis currentCycle = structure.currentCycle();
        if (currentCycle.primaryFit() == null) {
            return Optional.empty();
        }

        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final JFreeChart chart = renderLiveCurrentCycleChart(series, structure);
        return chartWorkflow.saveChartImage(chart, series, chartFileName,
                ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
    }

    private static LivePresetLegacyView generateLivePresetLegacyView(final BarSeries series,
            final String scenarioSeriesToken, final CurrentCycleAnalysis currentCycle, final LivePresetReport report,
            final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(scenarioSeriesToken, "scenarioSeriesToken");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> displayCandidates = currentCycle
                .displayCandidates();
        final List<LivePresetScenarioView> scenarioViews = buildLivePresetScenarioViews(scenarioSeriesToken,
                displayCandidates);
        saveLegacyCompatibleScenarioCharts(series, scenarioViews, chartDirectory, ElliottDegree.CYCLE);
        return new LivePresetLegacyView(report, scenarioViews);
    }

    private static List<LivePresetScenarioView> buildLivePresetScenarioViews(final String seriesToken,
            final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        final double totalScore = candidates.stream()
                .mapToDouble(ElliottWaveAnalysisResult.CurrentCycleCandidate::totalScore)
                .sum();
        final List<LivePresetScenarioView> scenarioViews = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = candidates.get(index);
            final double probability = totalScore <= ElliottWaveBtcMacroCycleDemo.EPSILON ? 0.0
                    : candidate.totalScore() / totalScore;
            final String label = index == 0 ? "BASE CASE" : "ALTERNATIVE " + index;
            final String fileName = index == 0 ? livePresetChartBaseCaseFileName(seriesToken)
                    : livePresetChartAlternativeFileName(seriesToken, index);
            scenarioViews.add(new LivePresetScenarioView(label, fileName, probability, candidate));
        }
        return List.copyOf(scenarioViews);
    }

    private static void saveLegacyCompatibleScenarioCharts(final BarSeries series,
            final List<LivePresetScenarioView> scenarioViews, final Path chartDirectory, final ElliottDegree degree) {
        if (scenarioViews.isEmpty()) {
            return;
        }

        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final boolean isHeadless = GraphicsEnvironment.isHeadless();
        final String trendLabel = formatLegacyTrendLabel(scenarioViews);
        for (final LivePresetScenarioView scenarioView : scenarioViews) {
            final ChartPlan plan = buildLegacyCompatibleLiveScenarioPlan(series, scenarioView, trendLabel, degree);
            if (!isHeadless) {
                chartWorkflow.display(plan, buildLegacyScenarioWindowTitle(degree, trendLabel, scenarioView.label(),
                        scenarioView.candidate().fit().scenario(), series.getName()));
            }
            chartWorkflow.save(plan, scenarioView.fileName(), ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH,
                    ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
        }
    }

    private static String formatLegacyTrendLabel(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "TREND: UNKNOWN";
        }

        final long bullishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> view.candidate().fit().scenario().isBullish())
                .count();
        final long bearishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> !view.candidate().fit().scenario().isBullish())
                .count();
        if (bullishCount == bearishCount) {
            return "TREND: NEUTRAL";
        }
        return bullishCount > bearishCount ? "TREND: BULLISH" : "TREND: BEARISH";
    }

    private static ChartPlan buildLegacyCompatibleLiveScenarioPlan(final BarSeries series,
            final LivePresetScenarioView scenarioView, final String trendLabel, final ElliottDegree degree) {
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = scenarioView.candidate().fit();
        final Color scenarioColor = fit.scenario().isBullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR
                : ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR;
        final BarSeriesLabelIndicator labels = new BarSeriesLabelIndicator(series,
                buildWaveLabelsFromScenario(series, fit.scenario(), scenarioColor));
        final FixedIndicator<Num> scenarioPath = buildScenarioIndicator(series, fit.scenario(), fit.countLabel());
        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        return chartWorkflow.builder()
                .withTitle(buildLegacyScenarioTitle(degree, series, trendLabel, scenarioView.label(), fit.scenario()))
                .withSeries(series)
                .withIndicatorOverlay(scenarioPath)
                .withLineColor(scenarioColor)
                .withLineWidth(2.4f)
                .withOpacity(0.90f)
                .withLabel(fit.countLabel())
                .withIndicatorOverlay(labels)
                .withLineColor(Color.WHITE)
                .withLineWidth(2.2f)
                .withOpacity(0.95f)
                .withLabel("Wave pivots")
                .toPlan();
    }

    private static void logLegacyCompatibleLivePreset(final LivePresetLegacyView legacyView) {
        Objects.requireNonNull(legacyView, "legacyView");

        final LivePresetReport report = legacyView.report();
        final CurrentCycleSummary summary = report.currentCycle();
        final List<LivePresetScenarioView> scenarioViews = legacyView.scenarioViews();

        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", summarizeLegacyScenarioViews(scenarioViews));
        LOG.info("Strong consensus: {} | Consensus phase: {}", hasStrongConsensus(scenarioViews),
                consensusPhase(scenarioViews).map(Enum::name).orElse("NONE"));
        LOG.info("Trend bias: {}", formatLegacyTrendLabel(scenarioViews));
        LOG.info("Historical status: {}", summary.historicalStatus());
        if (!scenarioViews.isEmpty()) {
            final LivePresetScenarioView baseCase = scenarioViews.getFirst();
            final ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = baseCase.candidate().fit();
            final ElliottScenario scenario = fit.scenario();
            final ElliottConfidence confidence = scenario.confidence();
            LOG.info("BASE CASE SCENARIO: {} ({})", scenario.currentPhase(), scenario.type());
            LOG.info("  Overall confidence: {}% ({})",
                    String.format(java.util.Locale.ROOT, "%.1f", confidence.asPercentage()),
                    confidence.isHighConfidence() ? "HIGH" : confidence.isLowConfidence() ? "LOW" : "MEDIUM");
            LOG.info("  Scenario probability: raw={}%, calibrated={}%",
                    String.format(java.util.Locale.ROOT, "%.1f", baseCase.probability() * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f", baseCase.probability() * 100.0));
            LOG.info("  Factor scores: Fibonacci={}% | Time={}% | Alternation={}% | Channel={}% | Completeness={}%",
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.fibonacciScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.timeProportionScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.alternationScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.channelScore()) * 100.0),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            safeConfidenceScore(confidence.completenessScore()) * 100.0));
            LOG.info("  Primary reason: {}", confidence.primaryReason());
            LOG.info("  Weakest factor: {}", confidence.weakestFactor());
            LOG.info("  Direction: {} | Phase invalidation: {} | Structural invalidation: {} | Target: {}",
                    scenario.hasKnownDirection() ? (scenario.isBullish() ? "BULLISH" : "BEARISH") : "UNKNOWN",
                    formatInvalidationCondition(scenario, fit.phaseInvalidationPrice()),
                    formatInvalidationCondition(scenario, fit.invalidationPrice()), scenario.primaryTarget());
        }

        if (scenarioViews.size() > 1) {
            LOG.info("ALTERNATIVE SCENARIOS ({}):", scenarioViews.size() - 1);
            for (int index = 1; index < scenarioViews.size(); index++) {
                final LivePresetScenarioView alternative = scenarioViews.get(index);
                final ElliottScenario scenario = alternative.candidate().fit().scenario();
                LOG.info("  {}. {} ({}) - {}% confidence | raw={}%, calibrated={}%", index, scenario.currentPhase(),
                        scenario.type(),
                        String.format(java.util.Locale.ROOT, "%.1f", scenario.confidence().asPercentage()),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0),
                        String.format(java.util.Locale.ROOT, "%.1f", alternative.probability() * 100.0));
            }
        }
        LOG.info(
                "Current macro read: primary={} | alternate={} | currentWave={} | phase invalidation {} | structural invalidation {} | orthodox wave5 target {}",
                summary.primaryCount(), summary.alternateCount(), summary.currentWave(), summary.invalidationPrice(),
                summary.structuralInvalidationPrice(), summary.orthodoxWaveFiveTargetRange());
        LOG.info("Macro summary JSON: {}", report.summaryPath());
        LOG.info("Macro current-cycle chart: {}", report.chartPath());
        LOG.info("======================================");
    }

    private static String summarizeLegacyScenarioViews(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "No scenarios";
        }
        final LivePresetScenarioView baseCase = scenarioViews.getFirst();
        final StringBuilder summary = new StringBuilder();
        summary.append(scenarioViews.size())
                .append(" scenario(s): Base case=")
                .append(baseCase.candidate().fit().scenario().currentPhase())
                .append(" (")
                .append(String.format(java.util.Locale.ROOT, "%.1f%%",
                        baseCase.candidate().fit().scenario().confidence().asPercentage()))
                .append(")");
        if (scenarioViews.size() > 1) {
            summary.append(", ").append(scenarioViews.size() - 1).append(" alternative(s)");
        }
        consensusPhase(scenarioViews).ifPresent(phase -> summary.append(", consensus=").append(phase));
        return summary.toString();
    }

    private static boolean hasStrongConsensus(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return false;
        }
        if (scenarioViews.size() == 1) {
            return true;
        }
        final double spread = scenarioViews.getFirst().probability() - scenarioViews.get(1).probability();
        return spread >= 0.08;
    }

    private static Optional<ElliottPhase> consensusPhase(final List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return Optional.empty();
        }

        final Map<ElliottPhase, Double> phaseWeights = new LinkedHashMap<>();
        for (final LivePresetScenarioView scenarioView : scenarioViews) {
            final ElliottScenario scenario = scenarioView.candidate().fit().scenario();
            phaseWeights.merge(scenario.currentPhase(), scenarioView.probability(), Double::sum);
        }
        final Map.Entry<ElliottPhase, Double> bestPhase = phaseWeights.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (bestPhase == null || bestPhase.getValue() < 0.60) {
            return Optional.empty();
        }
        return Optional.of(bestPhase.getKey());
    }

    private static String buildLegacyScenarioTitle(final ElliottDegree degree, final BarSeries series,
            final String trendLabel, final String scenarioLabel, final ElliottScenario scenario) {
        return String.format(java.util.Locale.ROOT, "Elliott Wave (%s) - %s - %s - %s: %s (%s) - %.1f%% confidence",
                degree, series.getName(), trendLabel, scenarioLabel, scenario.currentPhase(), scenario.type(),
                scenario.confidence().asPercentage());
    }

    private static String buildLegacyScenarioWindowTitle(final ElliottDegree degree, final String trendLabel,
            final String scenarioLabel, final ElliottScenario scenario, final String seriesName) {
        return String.format(java.util.Locale.ROOT, "%s - %s - %s: %s (%s) - %.1f%% - %s", degree, trendLabel,
                scenarioLabel, scenario.currentPhase(), scenario.type(), scenario.confidence().asPercentage(),
                seriesName);
    }

    private static String livePresetChartBaseCaseFileName(final String seriesToken) {
        return "elliott-wave-analysis-" + seriesToken + "-cycle-base-case";
    }

    private static String livePresetChartAlternativeFileName(final String seriesToken, final int alternativeIndex) {
        return "elliott-wave-analysis-" + seriesToken + "-cycle-alternative-" + alternativeIndex;
    }

    private static String liveCurrentCycleChartFileName(final String seriesToken) {
        return "elliott-wave-" + seriesToken + "-live-macro-current-cycle";
    }

    private static String liveSummaryFileName(final String seriesToken) {
        return "elliott-wave-" + seriesToken + "-live-macro-current-cycle-summary.json";
    }

    private static String scenarioSeriesName(final BarSeries series) {
        final String rawName = series == null || series.getName() == null ? "series" : series.getName().trim();
        final String normalized = rawName.isEmpty() ? "series"
                : rawName.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+", "")
                        .replaceAll("-+$", "");
        return normalized.isBlank() ? "series" : normalized;
    }

    private static JFreeChart renderLiveCurrentCycleChart(final BarSeries series, final CanonicalStructure structure) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(structure, "structure");
        final CurrentCycleAnalysis currentCycle = structure.currentCycle();

        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit = currentCycle.primaryFit();
        final ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit = currentCycle.alternateFit();
        final BarSeriesLabelIndicator primaryLabels = primaryFit == null
                ? new BarSeriesLabelIndicator(series, List.of())
                : new BarSeriesLabelIndicator(series, buildWaveLabelsFromScenario(series, primaryFit.scenario(),
                        ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR));
        final FixedIndicator<Num> primaryPath = primaryFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().primaryCount())
                : buildScenarioIndicator(series, primaryFit.scenario(), currentCycle.summary().primaryCount());
        final FixedIndicator<Num> alternatePath = alternateFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().alternateCount())
                : buildScenarioIndicator(series, alternateFit.scenario(), currentCycle.summary().alternateCount());

        final String chartTitle = series.getName() + " live macro current cycle";
        final ChartPlan plan;
        if (alternateFit != null && !currentCycle.summary().alternateCount().isBlank()) {
            plan = chartWorkflow.builder()
                    .withTitle(chartTitle)
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(alternatePath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR)
                    .withLineWidth(2.0f)
                    .withOpacity(0.55f)
                    .withLabel(currentCycle.summary().alternateCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        } else {
            plan = chartWorkflow.builder()
                    .withTitle(chartTitle)
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        }
        final JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    private static double safeConfidenceScore(final double score) {
        return Double.isFinite(score) ? clamp(score, 0.0, 1.0) : 0.0;
    }

    private static double safeConfidenceScore(final Num score) {
        if (score == null || score.isNaN()) {
            return 0.0;
        }
        return safeConfidenceScore(score.doubleValue());
    }

    private static FixedIndicator<Num> buildScenarioIndicator(final BarSeries series, final ElliottScenario scenario,
            final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        applyScenarioPath(values, series, scenario);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static FixedIndicator<Num> emptyScenarioIndicator(final BarSeries series, final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    static void applyLogPriceAxis(final JFreeChart chart, final BarSeries series) {
        if (!(chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot)) {
            return;
        }
        if (combinedPlot.getSubplots() == null || combinedPlot.getSubplots().isEmpty()) {
            return;
        }

        final Object subplot = combinedPlot.getSubplots().getFirst();
        if (!(subplot instanceof XYPlot pricePlot)) {
            return;
        }

        final LogAxis logAxis = new LogAxis("Price (USD, log)");
        logAxis.setAutoRange(true);
        logAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        logAxis.setLabelPaint(Color.LIGHT_GRAY);
        logAxis.setSmallestValue(smallestPositiveLow(series));
        pricePlot.setRangeAxis(logAxis);
    }

    private static Optional<Path> saveHistoricalChart(final BarSeries series, final MacroStudy study,
            final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(study, "study");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        final ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        final JFreeChart chart = renderHistoricalChart(series, study);
        return chartWorkflow.saveChartImage(chart, series, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_FILE_NAME,
                ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT);
    }

    static JFreeChart renderHistoricalChart(final BarSeries series, final MacroStudy study) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(study, "study");

        final ChartWorkflow chartWorkflow = new ChartWorkflow();
        final List<DirectionalCycleSummary> cycleSummaries = study.cycles();
        final List<SegmentScenarioFit> segmentFits = study.selectedProfile().chartSegments();
        final boolean useStudyCycles = !cycleSummaries.isEmpty();
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> cycleAnchors = useStudyCycles
                ? buildCycleAnchors(cycleSummaries)
                : List.of();
        final List<LegSegment> legSegments = useStudyCycles ? buildLegSegmentsFromCycleSummaries(cycleSummaries)
                : List.of();
        final int currentCycleStartIndex = useStudyCycles ? latestBottomAnchorIndex(series, cycleAnchors)
                : Integer.MAX_VALUE;
        final BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series,
                useStudyCycles ? buildAnchorLabels(series, cycleAnchors) : List.of());
        final BarSeriesLabelIndicator waveLabels = new BarSeriesLabelIndicator(series,
                buildSegmentWaveLabels(series, segmentFits));
        final FixedIndicator<Num> bullishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, true, true,
                "Bullish accepted wave segments");
        final FixedIndicator<Num> bearishAcceptedFits = buildScenarioFitIndicator(series, segmentFits, false, true,
                "Bearish accepted wave segments");
        final FixedIndicator<Num> bullishFallbackFits = buildScenarioFitIndicator(series, segmentFits, true, false,
                "Bullish fallback wave segments");
        final FixedIndicator<Num> bearishFallbackFits = buildScenarioFitIndicator(series, segmentFits, false, false,
                "Bearish fallback wave segments");
        final FixedIndicator<Num> bullishLegs = buildCycleLegIndicator(series, legSegments, true, "Bullish 1-2-3-4-5",
                currentCycleStartIndex);
        final FixedIndicator<Num> bearishLegs = buildCycleLegIndicator(series, legSegments, false, "Bearish A-B-C",
                currentCycleStartIndex);
        final ChartPlan plan = chartWorkflow.builder()
                .withTitle(series.getName() + " macro cycles: bullish 1-5 tops and bearish A-C lows")
                .withSeries(series)
                .withIndicatorOverlay(bullishLegs)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bullish 1-2-3-4-5")
                .withIndicatorOverlay(bearishLegs)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR)
                .withLineWidth(4.2f)
                .withOpacity(0.60f)
                .withLabel("Bearish A-B-C")
                .withIndicatorOverlay(bullishAcceptedFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bullish accepted wave segments")
                .withIndicatorOverlay(bearishAcceptedFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR)
                .withLineWidth(2.4f)
                .withOpacity(0.72f)
                .withLabel("Bearish accepted wave segments")
                .withIndicatorOverlay(bullishFallbackFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bullish fallback wave segments")
                .withIndicatorOverlay(bearishFallbackFits)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.BEARISH_CANDIDATE_COLOR)
                .withLineWidth(1.8f)
                .withOpacity(0.56f)
                .withLabel("Bearish fallback wave segments")
                .withIndicatorOverlay(anchorLabels)
                .withLineColor(ElliottWaveBtcMacroCycleDemo.ANCHOR_OVERLAY_COLOR)
                .withLineWidth(1.5f)
                .withOpacity(0.55f)
                .withLabel("Macro-cycle anchors")
                .withIndicatorOverlay(waveLabels)
                .withLineColor(Color.WHITE)
                .withLineWidth(2.5f)
                .withOpacity(0.95f)
                .withLabel("Matched Elliott wave labels")
                .toPlan();
        final JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    private static List<ElliottWaveAnchorCalibrationHarness.Anchor> buildCycleAnchors(
            final List<DirectionalCycleSummary> cycleSummaries) {
        final LinkedHashMap<String, ElliottWaveAnchorCalibrationHarness.Anchor> anchors = new LinkedHashMap<>();
        for (final DirectionalCycleSummary cycle : cycleSummaries) {
            final ElliottWaveAnchorRegistry.AnchorPartition partition = ElliottWaveAnchorRegistry.AnchorPartition
                    .valueOf(cycle.partition().toUpperCase(Locale.ROOT));
            anchors.putIfAbsent(cycle.cycleId() + "-start",
                    new ElliottWaveAnchorCalibrationHarness.Anchor(cycle.cycleId() + "-start",
                            ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, Instant.parse(cycle.startTimeUtc()),
                            Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE1), partition,
                            "cycle-summary-start"));
            anchors.putIfAbsent(cycle.cycleId() + "-peak",
                    new ElliottWaveAnchorCalibrationHarness.Anchor(cycle.cycleId() + "-peak",
                            ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, Instant.parse(cycle.peakTimeUtc()),
                            Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5), partition, "cycle-summary-peak"));
            anchors.putIfAbsent(cycle.cycleId() + "-low",
                    new ElliottWaveAnchorCalibrationHarness.Anchor(cycle.cycleId() + "-low",
                            ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, Instant.parse(cycle.lowTimeUtc()),
                            Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C), partition,
                            "cycle-summary-low"));
        }
        return anchors.values()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
    }

    private static MacroProfileEvaluation evaluateLegacyAnchoredProfile(final BarSeries series,
            final MacroLogicProfile profile, final List<MacroCycle> historicalCycles) {
        final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        final Map<String, SegmentScenarioFit> segmentById = new LinkedHashMap<>();
        final Map<String, CycleFit> matchedCycleFitsById = new LinkedHashMap<>();
        final List<CycleFit> cycleFits = new ArrayList<>();
        for (final MacroCycle cycle : historicalCycles) {
            final SegmentScenarioFit bullishFit = fitSegment(series, cycle.bullishLeg(), profile, profileRunner)
                    .orElse(null);
            final SegmentScenarioFit bearishFit = fitSegment(series, cycle.bearishLeg(), profile, profileRunner)
                    .orElse(null);
            if (bullishFit != null) {
                segmentById.putIfAbsent(segmentKey(cycle.bullishLeg()), bullishFit);
            }
            if (bearishFit != null) {
                segmentById.putIfAbsent(segmentKey(cycle.bearishLeg()), bearishFit);
            }
            final CycleFit cycleFit = CycleFit.create(cycle, bullishFit, bearishFit);
            cycleFits.add(cycleFit);
            if (bullishFit != null && bearishFit != null) {
                matchedCycleFitsById.putIfAbsent(cycle.id(), cycleFit);
            }
        }
        final List<SegmentScenarioFit> chartSegments = List.copyOf(segmentById.values());
        final TruthTargetCoverage truthTargetCoverage = truthTargetCoverage(historicalCycles, matchedCycleFitsById,
                List.of());
        return buildMacroProfileEvaluation(profile, cycleFits, chartSegments, truthTargetCoverage);
    }

    private static MacroProfileEvaluation evaluateCanonicalProfile(final BarSeries series,
            final MacroLogicProfile profile, final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return evaluateCanonicalProfilePair(series, profile, registry).truthTargetEvaluation();
    }

    private static HistoricalProfileEvaluations evaluateCanonicalProfilePair(final BarSeries series,
            final MacroLogicProfile profile, final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        final ElliottWaveAnalysisResult.HistoricalStructureAssessment structure = profileRunner
                .analyzeHistoricalStructure(series);
        final Map<String, CycleFit> runtimeCycleFitsById = new LinkedHashMap<>();
        final Map<String, SegmentScenarioFit> runtimeChartSegmentsById = new LinkedHashMap<>();
        final Map<String, CycleFit> truthTargetCycleFitsById = new LinkedHashMap<>();
        final Map<String, SegmentScenarioFit> truthTargetChartSegmentsById = new LinkedHashMap<>();
        final List<MacroCycle> unexpectedCycles = new ArrayList<>();
        for (final ElliottWaveAnalysisResult.HistoricalCycleAssessment cycle : structure.cycles()) {
            final MacroCycle runtimeCycle = runtimeMacroCycle(series, cycle);
            final SegmentScenarioFit runtimeBullishFit = fitFromCoreAssessment(runtimeCycle.bullishLeg(),
                    cycle.bullishLeg().assessment(), true, cycle.bullishLeg().accepted());
            final SegmentScenarioFit runtimeBearishFit = fitFromCoreAssessment(runtimeCycle.bearishLeg(),
                    cycle.bearishLeg().assessment(), false, cycle.bearishLeg().accepted());
            runtimeCycleFitsById.putIfAbsent(runtimeCycle.id(),
                    CycleFit.create(runtimeCycle, runtimeBullishFit, runtimeBearishFit));
            runtimeChartSegmentsById.putIfAbsent(segmentKey(runtimeCycle.bullishLeg()), runtimeBullishFit);
            runtimeChartSegmentsById.putIfAbsent(segmentKey(runtimeCycle.bearishLeg()), runtimeBearishFit);

            final Optional<MacroCycle> matchedCycle = matchCycleToRegistry(series, registry, cycle);
            if (matchedCycle.isEmpty()) {
                unexpectedCycles.add(runtimeCycle);
                continue;
            }
            final MacroCycle macroCycle = matchedCycle.orElseThrow();
            final SegmentScenarioFit bullishFit = fitFromCoreAssessment(macroCycle.bullishLeg(),
                    cycle.bullishLeg().assessment(), true, cycle.bullishLeg().accepted());
            final SegmentScenarioFit bearishFit = fitFromCoreAssessment(macroCycle.bearishLeg(),
                    cycle.bearishLeg().assessment(), false, cycle.bearishLeg().accepted());
            if (truthTargetCycleFitsById.containsKey(macroCycle.id())) {
                unexpectedCycles.add(runtimeCycle);
                continue;
            }
            truthTargetCycleFitsById.putIfAbsent(macroCycle.id(), CycleFit.create(macroCycle, bullishFit, bearishFit));
            truthTargetChartSegmentsById.putIfAbsent(segmentKey(macroCycle.bullishLeg()), bullishFit);
            truthTargetChartSegmentsById.putIfAbsent(segmentKey(macroCycle.bearishLeg()), bearishFit);
        }
        final MacroProfileEvaluation runtimeEvaluation = buildMacroProfileEvaluation(profile,
                List.copyOf(runtimeCycleFitsById.values()), List.copyOf(runtimeChartSegmentsById.values()),
                TruthTargetCoverage.none());
        final TruthTargetCoverage truthTargetCoverage = truthTargetCoverage(registry, truthTargetCycleFitsById,
                unexpectedCycles);
        final MacroProfileEvaluation truthTargetEvaluation = buildMacroProfileEvaluation(profile,
                List.copyOf(truthTargetCycleFitsById.values()), List.copyOf(truthTargetChartSegmentsById.values()),
                truthTargetCoverage);
        return new HistoricalProfileEvaluations(runtimeEvaluation, truthTargetEvaluation);
    }

    private static MacroProfileEvaluation evaluateCanonicalProfile(final BarSeries series,
            final MacroLogicProfile profile) {
        final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
        final ElliottWaveAnalysisResult.HistoricalStructureAssessment structure = profileRunner
                .analyzeHistoricalStructure(series);
        final Map<String, CycleFit> cycleFitsById = new LinkedHashMap<>();
        final Map<String, SegmentScenarioFit> chartSegmentsById = new LinkedHashMap<>();
        for (final ElliottWaveAnalysisResult.HistoricalCycleAssessment cycle : structure.cycles()) {
            final MacroCycle macroCycle = runtimeMacroCycle(series, cycle);
            final SegmentScenarioFit bullishFit = fitFromCoreAssessment(macroCycle.bullishLeg(),
                    cycle.bullishLeg().assessment(), true, cycle.bullishLeg().accepted());
            final SegmentScenarioFit bearishFit = fitFromCoreAssessment(macroCycle.bearishLeg(),
                    cycle.bearishLeg().assessment(), false, cycle.bearishLeg().accepted());
            cycleFitsById.putIfAbsent(macroCycle.id(), CycleFit.create(macroCycle, bullishFit, bearishFit));
            chartSegmentsById.putIfAbsent(segmentKey(macroCycle.bullishLeg()), bullishFit);
            chartSegmentsById.putIfAbsent(segmentKey(macroCycle.bearishLeg()), bearishFit);
        }
        return buildMacroProfileEvaluation(profile, List.copyOf(cycleFitsById.values()),
                List.copyOf(chartSegmentsById.values()), TruthTargetCoverage.none());
    }

    private static MacroProfileEvaluation buildMacroProfileEvaluation(final MacroLogicProfile profile,
            final List<CycleFit> cycleFits, final List<SegmentScenarioFit> chartSegments,
            final TruthTargetCoverage truthTargetCoverage) {

        int acceptedCycles = 0;
        int acceptedSegments = 0;
        double scoreTotal = 0.0;
        for (final CycleFit cycleFit : cycleFits) {
            if (cycleFit.accepted()) {
                acceptedCycles++;
            }
            if (cycleFit.bullishFit() != null && cycleFit.bullishFit().accepted()) {
                acceptedSegments++;
            }
            if (cycleFit.bearishFit() != null && cycleFit.bearishFit().accepted()) {
                acceptedSegments++;
            }
            scoreTotal += cycleFit.aggregateScore();
        }
        final double aggregateScore = cycleFits.isEmpty() ? 0.0 : scoreTotal / cycleFits.size();
        final boolean historicalFitPassed;
        if (truthTargetCoverage.hasExpectations()) {
            historicalFitPassed = truthTargetCoverage.complete();
        } else {
            historicalFitPassed = !cycleFits.isEmpty() && acceptedCycles == cycleFits.size();
        }
        return new MacroProfileEvaluation(profile, aggregateScore, acceptedCycles, acceptedSegments,
                historicalFitPassed, truthTargetCoverage, List.copyOf(cycleFits), List.copyOf(chartSegments));
    }

    private static List<DirectionalCycleSummary> cycleSummaries(final MacroProfileEvaluation evaluation) {
        return evaluation.cycleFits().stream().map(DirectionalCycleSummary::from).toList();
    }

    private static Optional<MacroCycle> matchCycleToRegistry(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry,
            final ElliottWaveAnalysisResult.HistoricalCycleAssessment cycle) {
        final Instant peakTime = series.getBar(cycle.bullishLeg().endIndex()).getEndTime();
        final Instant lowTime = series.getBar(cycle.bearishLeg().endIndex()).getEndTime();
        return buildHistoricalCycles(registry).stream()
                .filter(expectedCycle -> withinTolerance(peakTime, expectedCycle.peak()))
                .filter(expectedCycle -> withinTolerance(lowTime, expectedCycle.low()))
                .min(Comparator.comparingLong(
                        expectedCycle -> Math.abs(Duration.between(expectedCycle.peak().at(), peakTime).toMillis())
                                + Math.abs(Duration.between(expectedCycle.low().at(), lowTime).toMillis())));
    }

    private static TruthTargetCoverage truthTargetCoverage(
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry,
            final Map<String, CycleFit> cycleFitsById, final List<MacroCycle> unexpectedCycles) {
        final List<MacroCycle> expectedCycles = buildHistoricalCycles(registry);
        return truthTargetCoverage(expectedCycles, cycleFitsById, unexpectedCycles);
    }

    private static TruthTargetCoverage truthTargetCoverage(final List<MacroCycle> expectedCycles,
            final Map<String, CycleFit> cycleFitsById, final List<MacroCycle> unexpectedCycles) {
        final List<String> expectedCycleIds = expectedCycles.stream().map(MacroCycle::id).toList();
        final Instant earliestExpectedStart = expectedCycles.getFirst().start().at();
        final Instant latestExpectedLow = expectedCycles.getLast().low().at();
        final List<String> unexpectedCycleIds = unexpectedCycles.stream()
                .filter(cycle -> !cycle.start().at().isBefore(earliestExpectedStart))
                .filter(cycle -> !cycle.low().at().isAfter(latestExpectedLow))
                .filter(cycle -> expectedCycles.stream()
                        .noneMatch(expectedCycle -> isNestedWithinExpectedCycle(cycle, expectedCycle)))
                .map(MacroCycle::id)
                .toList();
        final List<String> missingExpectedCycleIds = expectedCycleIds.stream()
                .filter(expectedCycleId -> !cycleFitsById.containsKey(expectedCycleId))
                .toList();
        return new TruthTargetCoverage(expectedCycleIds.size(), cycleFitsById.size(), missingExpectedCycleIds.size(),
                unexpectedCycleIds.size(), missingExpectedCycleIds, List.copyOf(unexpectedCycleIds));
    }

    private static boolean isNestedWithinExpectedCycle(final MacroCycle cycle, final MacroCycle expectedCycle) {
        return !cycle.start().at().isBefore(expectedCycle.start().at())
                && !cycle.low().at().isAfter(expectedCycle.low().at())
                && cycle.low().at().isBefore(expectedCycle.low().at());
    }

    private static MacroCycle runtimeMacroCycle(final BarSeries series,
            final ElliottWaveAnalysisResult.HistoricalCycleAssessment cycle) {
        final ElliottWaveAnchorCalibrationHarness.Anchor start = runtimeAnchor(series, cycle.bullishLeg().startIndex(),
                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, Set.of(ElliottPhase.CORRECTIVE_C), "start");
        final ElliottWaveAnchorCalibrationHarness.Anchor peak = runtimeAnchor(series, cycle.bullishLeg().endIndex(),
                ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, Set.of(ElliottPhase.WAVE5), "peak");
        final ElliottWaveAnchorCalibrationHarness.Anchor low = runtimeAnchor(series, cycle.bearishLeg().endIndex(),
                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, Set.of(ElliottPhase.CORRECTIVE_C), "low");
        final LegSegment bullishLeg = new LegSegment(start, peak, true);
        final LegSegment bearishLeg = new LegSegment(peak, low, false);
        final String cycleId = "runtime-cycle-" + cycle.bullishLeg().startIndex() + "-" + cycle.bullishLeg().endIndex()
                + "-" + cycle.bearishLeg().endIndex();
        return new MacroCycle(cycleId,
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION.name().toLowerCase(Locale.ROOT), start, peak, low,
                bullishLeg, bearishLeg);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor runtimeAnchor(final BarSeries series, final int index,
            final ElliottWaveAnchorCalibrationHarness.AnchorType type, final Set<ElliottPhase> expectedPhases,
            final String label) {
        final Instant time = series.getBar(index).getEndTime();
        return new ElliottWaveAnchorCalibrationHarness.Anchor(
                "runtime-" + label + "-" + index + "-" + time.toString().replace(':', '-'), type, time, Duration.ZERO,
                Duration.ZERO, expectedPhases, ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION,
                "Series-native canonical historical structure");
    }

    private static boolean withinTolerance(final Instant actual,
            final ElliottWaveAnchorCalibrationHarness.Anchor anchor) {
        final Instant windowStart = anchor.at().minus(anchor.toleranceBefore());
        final Instant windowEnd = anchor.at().plus(anchor.toleranceAfter());
        return !actual.isBefore(windowStart) && !actual.isAfter(windowEnd);
    }

    private static ElliottWaveAnalysisRunner buildProfileRunner(final MacroLogicProfile profile) {
        final int runnerMaxScenarios = Math.max(profile.runnerMaxScenarios(),
                ElliottWaveBtcMacroCycleDemo.MIN_CORE_SEGMENT_SCENARIOS);
        return ElliottWaveAnalysisRunner.builder()
                .degree(profile.runnerDegree())
                .logicProfile(profile.coreLogicProfile())
                .higherDegrees(profile.runnerHigherDegrees())
                .lowerDegrees(profile.runnerLowerDegrees())
                .maxScenarios(runnerMaxScenarios)
                .minConfidence(0.0)
                .seriesSelector((inputSeries, ignoredDegree) -> inputSeries)
                .build();
    }

    static Comparator<MacroProfileEvaluation> profileEvaluationComparator() {
        return PROFILE_EVALUATION_COMPARATOR;
    }

    /**
     * Internal canonical macro engine used by the public demo controller.
     *
     * <p>
     * This keeps profile sweep orchestration, truth-target scoring, and current
     * profile selection behind one non-public boundary so the demo entry points can
     * stay focused on report generation and chart persistence.
     */
    private static final class CanonicalEngine {

        private final List<MacroLogicProfile> logicProfiles;
        private final MacroLogicProfile defaultLiveMacroProfile;

        private CanonicalEngine(final List<MacroLogicProfile> logicProfiles) {
            Objects.requireNonNull(logicProfiles, "logicProfiles");
            this.logicProfiles = List.copyOf(logicProfiles);
            this.defaultLiveMacroProfile = this.logicProfiles.stream()
                    .filter(profile -> profile.id().equals("calibrated-baseline"))
                    .findFirst()
                    .orElseGet(this.logicProfiles::getFirst);
        }

        private CanonicalStructure analyzeCanonicalStructure(final BarSeries series) {
            final MacroStudy study = evaluateCanonicalMacroStudy(series);
            return new CanonicalStructure(Optional.of(study), study.currentCycleAnalysis());
        }

        private MacroStudy evaluateCanonicalMacroStudy(final BarSeries series) {
            Objects.requireNonNull(series, "series");

            final List<MacroProfileEvaluation> evaluations = evaluateCanonicalProfileSweep(series);
            final MacroProfileEvaluation selectedProfile = evaluations.getFirst();
            final List<ProfileScoreSummary> profileScores = evaluations.stream()
                    .map(ProfileScoreSummary::from)
                    .toList();
            final List<DirectionalCycleSummary> cycles = cycleSummaries(selectedProfile);
            final List<HypothesisResult> hypotheses = buildHypotheses(evaluations);
            final CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, selectedProfile.profile(),
                    historicalStructureStatus(selectedProfile.historicalFitPassed()));
            return new MacroStudy(selectedProfile, List.copyOf(evaluations), profileScores, cycles, hypotheses,
                    currentCycle);
        }

        private MacroStudy evaluateCanonicalMacroStudy(final BarSeries series,
                final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(registry, "registry");

            final List<MacroProfileEvaluation> runtimeEvaluations = new ArrayList<>();
            final List<MacroProfileEvaluation> truthTargetEvaluations = new ArrayList<>();
            for (final MacroLogicProfile profile : logicProfiles) {
                final HistoricalProfileEvaluations profileEvaluations = evaluateCanonicalProfilePair(series, profile,
                        registry);
                runtimeEvaluations.add(profileEvaluations.runtimeEvaluation());
                truthTargetEvaluations.add(profileEvaluations.truthTargetEvaluation());
            }
            runtimeEvaluations.sort(RUNTIME_PROFILE_EVALUATION_COMPARATOR);
            truthTargetEvaluations.sort(PROFILE_EVALUATION_COMPARATOR);

            final MacroProfileEvaluation selectedRuntimeProfile = runtimeEvaluations.getFirst();
            final MacroProfileEvaluation selectedProfile = truthTargetEvaluations.getFirst();
            final List<ProfileScoreSummary> profileScores = truthTargetEvaluations.stream()
                    .map(ProfileScoreSummary::from)
                    .toList();
            final List<DirectionalCycleSummary> cycles = selectedProfile.cycleFits()
                    .stream()
                    .map(DirectionalCycleSummary::from)
                    .toList();
            final List<HypothesisResult> hypotheses = buildHypotheses(truthTargetEvaluations);
            final CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, selectedRuntimeProfile.profile(),
                    historicalTruthTargetStatus(selectedProfile.historicalFitPassed()));
            return new MacroStudy(selectedProfile, List.copyOf(truthTargetEvaluations), profileScores, cycles,
                    hypotheses, currentCycle);
        }

        private List<MacroProfileEvaluation> evaluateCanonicalProfileSweep(final BarSeries series) {
            Objects.requireNonNull(series, "series");
            final List<MacroProfileEvaluation> evaluations = new ArrayList<>();
            for (final MacroLogicProfile profile : logicProfiles) {
                evaluations.add(evaluateCanonicalProfile(series, profile));
            }
            evaluations.sort(RUNTIME_PROFILE_EVALUATION_COMPARATOR);
            return List.copyOf(evaluations);
        }

        private List<MacroProfileEvaluation> evaluateCanonicalProfileSweep(final BarSeries series,
                final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(registry, "registry");
            final List<MacroProfileEvaluation> evaluations = new ArrayList<>();
            for (final MacroLogicProfile profile : logicProfiles) {
                evaluations.add(evaluateCanonicalProfilePair(series, profile, registry).truthTargetEvaluation());
            }
            evaluations.sort(PROFILE_EVALUATION_COMPARATOR);
            return List.copyOf(evaluations);
        }

        private MacroStudy evaluateLegacyAnchoredStudyForHarnessComparison(final BarSeries series,
                final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(registry, "registry");

            final List<MacroCycle> historicalCycles = buildHistoricalCycles(registry);
            final List<MacroProfileEvaluation> evaluations = new ArrayList<>();
            for (final MacroLogicProfile profile : logicProfiles) {
                evaluations.add(evaluateLegacyAnchoredProfile(series, profile, historicalCycles));
            }
            evaluations.sort(PROFILE_EVALUATION_COMPARATOR);
            final MacroProfileEvaluation selectedProfile = evaluations.getFirst();
            final List<ProfileScoreSummary> profileScores = evaluations.stream()
                    .map(ProfileScoreSummary::from)
                    .toList();
            final List<DirectionalCycleSummary> cycles = selectedProfile.cycleFits()
                    .stream()
                    .map(DirectionalCycleSummary::from)
                    .toList();
            final List<HypothesisResult> hypotheses = buildHypotheses(evaluations);
            final CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, selectedProfile.profile(),
                    historicalTruthTargetStatus(selectedProfile.historicalFitPassed()));
            return new MacroStudy(selectedProfile, List.copyOf(evaluations), profileScores, cycles, hypotheses,
                    currentCycle);
        }

        private CanonicalStructure analyzeCanonicalStructure(final BarSeries series,
                final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
            final MacroStudy study = evaluateCanonicalMacroStudy(series, registry);
            return new CanonicalStructure(Optional.of(study), study.currentCycleAnalysis());
        }

        private CurrentCycleAnalysis evaluateCurrentCycle(final BarSeries series, final MacroLogicProfile profile,
                final String historicalStatus) {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(historicalStatus, "historicalStatus");

            final ElliottWaveAnalysisRunner profileRunner = buildProfileRunner(profile);
            final ElliottWaveAnalysisResult.CurrentCycleAssessment assessment = profileRunner
                    .analyzeCurrentCycle(series);
            final ElliottWaveAnalysisResult.CurrentPhaseAssessment primary = assessment.primary();
            final ElliottWaveAnalysisResult.CurrentPhaseAssessment alternate = assessment.alternate();
            final int startIndex = assessment.startIndex();
            final String primaryCount = primary == null ? "No current bullish count" : primary.countLabel();
            final String alternateCount = alternate == null ? "" : alternate.countLabel();
            final String currentWave = primary == null ? "" : primary.currentPhase().name();
            final String invalidation = primary == null ? ""
                    : formatInvalidationCondition(primary.scenario(), primary.phaseInvalidationPrice());
            final String structuralInvalidation = primary == null ? ""
                    : formatInvalidationCondition(primary.scenario(), primary.invalidationPrice());
            final String startTimeUtc = series.getBar(startIndex).getEndTime().toString();
            final String latestTimeUtc = series.getLastBar().getEndTime().toString();
            final CurrentCycleSummary summary = new CurrentCycleSummary(startTimeUtc, latestTimeUtc, profile.id(),
                    historicalStatus, primaryCount, alternateCount, currentWave, invalidation, structuralInvalidation,
                    orthodoxWaveFiveTargetRange(primary), primary == null ? 0.0 : primary.fitScore(),
                    alternate == null ? 0.0 : alternate.fitScore(), "");
            return new CurrentCycleAnalysis(summary, primary, alternate, assessment.candidates(),
                    assessment.distinctCandidates(5));
        }

        private CanonicalStructure analyzeCanonicalStructure(final BarSeries series, final MacroLogicProfile profile,
                final String historicalStatus) {
            return new CanonicalStructure(Optional.empty(), evaluateCurrentCycle(series, profile, historicalStatus));
        }

        private List<MacroLogicProfile> logicProfiles() {
            return logicProfiles;
        }

        private MacroLogicProfile defaultLiveMacroProfile() {
            return defaultLiveMacroProfile;
        }
    }

    private static List<HypothesisResult> buildHypotheses(final List<MacroProfileEvaluation> evaluations) {
        final MacroProfileEvaluation baseline = findEvaluation(evaluations, "calibrated-baseline");
        final MacroProfileEvaluation hierarchical = findEvaluation(evaluations, "h1-hierarchical-swing");
        final MacroProfileEvaluation relaxedImpulse = findEvaluation(evaluations, "h2-pattern-aware-impulse");
        final MacroProfileEvaluation relaxedCorrective = findEvaluation(evaluations, "h3-pattern-aware-corrective");
        final MacroProfileEvaluation anchorFirst = findEvaluation(evaluations, "h4-span-aware-hybrid");

        return List.of(hypothesisFromProfile("H1", "Swing extraction is the main macro bottleneck", hierarchical,
                baseline, "Hierarchical pivots outperform the calibrated baseline on the committed macro truth target.",
                "Hierarchical pivots do not improve the historical truth-target fit enough beyond the calibrated baseline."),
                hypothesisFromProfile("H2", "Rigid impulse rules are too strict for the macro target", relaxedImpulse,
                        hierarchical,
                        "Pattern-aware impulse emphasis improves the historical cycle fit beyond the hierarchical baseline.",
                        "Pattern-aware impulse emphasis does not materially improve the historical cycle fit."),
                hypothesisFromProfile("H3", "Corrective handling is the missing coverage", relaxedCorrective,
                        relaxedImpulse,
                        "Pattern-aware corrective breadth improves the historical cycle fit beyond impulse-focused tuning.",
                        "Pattern-aware corrective breadth does not materially improve the historical cycle fit."),
                hypothesisFromProfile("H4", "Span-aware scoring beats phase-first selection", anchorFirst,
                        relaxedCorrective,
                        "The span-aware hybrid profile is the strongest overall truth-target fit and wins the macro study.",
                        "The span-aware hybrid profile does not beat the best simpler profile on the truth target."));
    }

    private static HypothesisResult hypothesisFromProfile(final String id, final String title,
            final MacroProfileEvaluation candidate, final MacroProfileEvaluation baseline,
            final String supportedSummary, final String rejectedSummary) {
        final boolean supported = candidate.aggregateScore() > baseline.aggregateScore() + 0.01
                || candidate.acceptedCycles() > baseline.acceptedCycles();
        final Map<String, String> evidence = orderedEvidence("candidateProfile", candidate.profile().id(),
                "candidateScore", formatScore(candidate.aggregateScore()), "candidateAcceptedCycles",
                Integer.toString(candidate.acceptedCycles()), "baselineProfile", baseline.profile().id(),
                "baselineScore", formatScore(baseline.aggregateScore()), "baselineAcceptedCycles",
                Integer.toString(baseline.acceptedCycles()));
        return new HypothesisResult(id, title, supported, supported ? supportedSummary : rejectedSummary, evidence);
    }

    private static MacroProfileEvaluation findEvaluation(final List<MacroProfileEvaluation> evaluations,
            final String profileId) {
        return evaluations.stream()
                .filter(evaluation -> evaluation.profile().id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing profile " + profileId));
    }

    private static String historicalStructureStatus(final boolean historicalFitPassed) {
        return historicalFitPassed ? HISTORICAL_STRUCTURE_FIT_PASSED : HISTORICAL_STRUCTURE_STILL_PARTIAL;
    }

    private static String historicalTruthTargetStatus(final boolean historicalFitPassed) {
        return historicalFitPassed ? HISTORICAL_TRUTH_TARGET_FIT_PASSED : HISTORICAL_TRUTH_TARGET_STILL_PARTIAL;
    }

    private static Optional<SegmentScenarioFit> fitSegment(final BarSeries series, final LegSegment legSegment,
            final MacroLogicProfile profile, final ElliottWaveAnalysisRunner profileRunner) {
        final int startIndex = nearestIndex(series, legSegment.fromAnchor().at());
        final int endIndex = nearestIndex(series, legSegment.toAnchor().at());
        if (endIndex <= startIndex) {
            return Optional.empty();
        }
        return legSegment.bullish()
                ? fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, true)
                : fitSegmentFromCoreRunner(series, legSegment, profile, profileRunner, startIndex, endIndex, false);
    }

    private static Optional<SegmentScenarioFit> fitSegmentFromCoreRunner(final BarSeries series,
            final LegSegment legSegment, final MacroLogicProfile profile, final ElliottWaveAnalysisRunner profileRunner,
            final int startIndex, final int endIndex, final boolean bullish) {
        return profileRunner
                .selectAcceptedOrFallbackTerminalLegForWindow(series, startIndex, endIndex, bullish,
                        ElliottWaveBtcMacroCycleDemo.MAX_CORE_ANCHOR_DRIFT_BARS,
                        Math.max(ElliottWaveBtcMacroCycleDemo.DEFAULT_ACCEPTED_SEGMENT_SCORE,
                                profile.acceptanceThreshold()),
                        0.30, 0.35, 0.80)
                .map(selection -> fitFromCoreAssessment(legSegment, selection.assessment(), bullish,
                        selection.accepted()));
    }

    private static List<BarLabel> buildAnchorLabels(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return buildAnchorLabels(series, registry.anchors());
    }

    private static List<BarLabel> buildAnchorLabels(final BarSeries series,
            final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors) {
        final List<BarLabel> labels = new ArrayList<>();
        final Num topPad = series.numFactory().numOf("1.02");
        final Num lowPad = series.numFactory().numOf("0.98");
        for (final ElliottWaveAnchorCalibrationHarness.Anchor anchor : anchors) {
            final int barIndex = nearestIndex(series, anchor.at());
            final Bar bar = series.getBar(barIndex);
            final boolean top = anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            final Num yValue = top ? bar.getHighPrice().multipliedBy(topPad) : bar.getLowPrice().multipliedBy(lowPad);
            final String date = bar.getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
            final String text = top ? "Bullish 1-5 top\n" + date : "Bearish A-C low\n" + date;
            final LabelPlacement placement = top ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
            final Color labelColor = top ? ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR
                    : ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR;
            labels.add(new BarLabel(barIndex, yValue, text, placement, labelColor));
        }
        return List.copyOf(labels);
    }

    private static List<BarLabel> buildSegmentWaveLabels(final BarSeries series,
            final List<SegmentScenarioFit> segmentFits) {
        final List<BarLabel> rawLabels = new ArrayList<>();
        for (final SegmentScenarioFit fit : segmentFits) {
            final Color labelColor = fit.accepted()
                    ? (fit.bullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR
                            : ElliottWaveBtcMacroCycleDemo.BEARISH_WAVE_COLOR)
                    : (fit.bullish() ? ElliottWaveBtcMacroCycleDemo.BULLISH_CANDIDATE_COLOR
                            : ElliottWaveBtcMacroCycleDemo.BEARISH_CANDIDATE_COLOR);
            rawLabels.addAll(buildWaveLabelsFromScenario(series, fit.scenario(), labelColor));
        }
        return deconflictLabels(series, rawLabels);
    }

    private static List<BarLabel> deconflictLabels(final BarSeries series, final List<BarLabel> labels) {
        final List<BarLabel> ordered = labels.stream().sorted(Comparator.comparingInt(BarLabel::barIndex)).toList();
        final List<BarLabel> adjusted = new ArrayList<>();
        for (final BarLabel label : ordered) {
            int clusterDepth = 0;
            for (int index = adjusted.size() - 1; index >= 0; index--) {
                final BarLabel prior = adjusted.get(index);
                if (label.barIndex() - prior.barIndex() > ElliottWaveBtcMacroCycleDemo.LABEL_CLUSTER_BAR_GAP) {
                    break;
                }
                if (prior.placement() == label.placement()) {
                    clusterDepth++;
                }
            }
            final Num adjustedY = applyLabelClusterOffset(series, label, clusterDepth);
            adjusted.add(new BarLabel(label.barIndex(), adjustedY, label.text(), label.placement(), label.color(),
                    label.fontScale()));
        }
        return List.copyOf(adjusted);
    }

    private static Num applyLabelClusterOffset(final BarSeries series, final BarLabel label, final int clusterDepth) {
        if (clusterDepth == 0) {
            return label.yValue();
        }
        final double step = 0.04 * clusterDepth;
        final double multiplier = switch (label.placement()) {
        case ABOVE -> 1.0 + step;
        case BELOW -> 1.0 - step;
        case CENTER -> 1.0;
        };
        return label.yValue().multipliedBy(series.numFactory().numOf(multiplier));
    }

    private static FixedIndicator<Num> buildScenarioFitIndicator(final BarSeries series,
            final List<SegmentScenarioFit> segmentFits, final boolean bullish, final boolean accepted,
            final String label) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (final SegmentScenarioFit fit : segmentFits) {
            if (fit.bullish() != bullish || fit.accepted() != accepted) {
                continue;
            }
            applyScenarioPath(values, series, fit.scenario());
        }
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void applyScenarioPath(final Num[] values, final BarSeries series, final ElliottScenario scenario) {
        for (final ElliottSwing swing : scenario.swings()) {
            final int fromIndex = Math.max(series.getBeginIndex(), Math.min(swing.fromIndex(), swing.toIndex()));
            final int toIndex = Math.min(series.getEndIndex(), Math.max(swing.fromIndex(), swing.toIndex()));
            if (toIndex < fromIndex) {
                continue;
            }
            final double fromPrice = swing.fromPrice().doubleValue();
            final double toPrice = swing.toPrice().doubleValue();
            final int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                final double progress = (double) (index - fromIndex) / length;
                final double interpolated = interpolateOverlayPrice(fromPrice, toPrice, progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
    }

    private static FixedIndicator<Num> buildCycleLegIndicator(final BarSeries series,
            final List<LegSegment> legSegments, final boolean bullish, final String label,
            final int currentCycleStartIndex) {
        final Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        for (final LegSegment legSegment : legSegments) {
            if (legSegment.bullish() != bullish) {
                continue;
            }
            final ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = legSegment.fromAnchor();
            final ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = legSegment.toAnchor();
            final int fromIndex = nearestIndex(series, fromAnchor.at());
            final int toIndex = nearestIndex(series, toAnchor.at());
            if (toIndex < fromIndex) {
                continue;
            }

            final double fromPrice = anchorPrice(series, fromIndex, fromAnchor.type());
            final double toPrice = anchorPrice(series, toIndex, toAnchor.type());
            final int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                final double progress = (double) (index - fromIndex) / length;
                final double interpolated = fromPrice + ((toPrice - fromPrice) * progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
        clipCurrentCycleMacroGuide(values, currentCycleStartIndex);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void clipCurrentCycleMacroGuide(final Num[] values, final int currentCycleStartIndex) {
        if (currentCycleStartIndex == Integer.MAX_VALUE) {
            return;
        }
        final int clipFromIndex = Math.max(0, currentCycleStartIndex + 1);
        for (int index = clipFromIndex; index < values.length; index++) {
            values[index] = NaN;
        }
    }

    private static List<LegSegment> buildLegSegmentsFromCycleSummaries(
            final List<DirectionalCycleSummary> cycleSummaries) {
        return buildLegSegmentsFromAnchors(buildCycleAnchors(cycleSummaries));
    }

    private static List<LegSegment> buildLegSegmentsFromAnchors(
            final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors) {
        final List<LegSegment> legSegments = new ArrayList<>();
        for (int index = 1; index < anchors.size(); index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor fromAnchor = anchors.get(index - 1);
            final ElliottWaveAnchorCalibrationHarness.Anchor toAnchor = anchors.get(index);
            if (fromAnchor.type() == toAnchor.type()) {
                continue;
            }
            final boolean bullish = fromAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    && toAnchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            legSegments.add(new LegSegment(fromAnchor, toAnchor, bullish));
        }
        return List.copyOf(legSegments);
    }

    private static List<MacroCycle> buildHistoricalCycles(
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors = registry.anchors()
                .stream()
                .sorted(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .toList();
        final List<MacroCycle> cycles = new ArrayList<>();
        for (int index = 0; index <= anchors.size() - 3; index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor start = anchors.get(index);
            final ElliottWaveAnchorCalibrationHarness.Anchor peak = anchors.get(index + 1);
            final ElliottWaveAnchorCalibrationHarness.Anchor low = anchors.get(index + 2);
            if (start.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM
                    || peak.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.TOP
                    || low.type() != ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM) {
                continue;
            }
            final String partition = low.partition().name().toLowerCase();
            final LegSegment bullishLeg = new LegSegment(start, peak, true);
            final LegSegment bearishLeg = new LegSegment(peak, low, false);
            cycles.add(new MacroCycle(start.id() + "->" + peak.id() + "->" + low.id(), partition, start, peak, low,
                    bullishLeg, bearishLeg));
        }
        return List.copyOf(cycles);
    }

    private static String segmentKey(final LegSegment legSegment) {
        return legSegment.fromAnchor().id() + "->" + legSegment.toAnchor().id();
    }

    private static int latestBottomAnchorIndex(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return latestBottomAnchorIndex(series, registry.anchors());
    }

    private static int latestBottomAnchorIndex(final BarSeries series,
            final List<ElliottWaveAnchorCalibrationHarness.Anchor> anchors) {
        return anchors.stream()
                .filter(anchor -> anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM)
                .max(Comparator.comparing(ElliottWaveAnchorCalibrationHarness.Anchor::at))
                .map(anchor -> nearestIndex(series, anchor.at()))
                .orElse(Integer.MAX_VALUE);
    }

    private static double anchorPrice(final BarSeries series, final int barIndex,
            final ElliottWaveAnchorCalibrationHarness.AnchorType anchorType) {
        final Bar bar = series.getBar(barIndex);
        return anchorType == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? bar.getHighPrice().doubleValue()
                : bar.getLowPrice().doubleValue();
    }

    private static String waveLabelForPhase(final ElliottPhase phase, final int waveIndex) {
        if (phase.isImpulse()) {
            return String.valueOf(waveIndex + 1);
        }
        if (phase.isCorrective()) {
            return switch (waveIndex) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
        }
        return String.valueOf(waveIndex + 1);
    }

    private static LabelPlacement placementForPivot(final boolean highPivot) {
        return highPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private static Num offsetLabelValue(final BarSeries series, final Num pivotPrice, final boolean highPivot) {
        final Num multiplier = highPivot ? series.numFactory().numOf("1.02") : series.numFactory().numOf("0.98");
        return pivotPrice.multipliedBy(multiplier);
    }

    private static int nearestIndex(final BarSeries series, final Instant target) {
        int nearest = series.getBeginIndex();
        long bestDistance = Long.MAX_VALUE;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            final long distance = Math.abs(series.getBar(index).getEndTime().toEpochMilli() - target.toEpochMilli());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static double smallestPositiveLow(final BarSeries series) {
        double smallest = Double.POSITIVE_INFINITY;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            final double low = series.getBar(index).getLowPrice().doubleValue();
            if (Double.isFinite(low) && low > 0.0 && low < smallest) {
                smallest = low;
            }
        }
        return Double.isFinite(smallest) ? smallest : 1.0;
    }

    private static String formatScore(final double score) {
        return String.format(java.util.Locale.ROOT, "%.3f", score);
    }

    private static String formatNum(final Num value) {
        return value == null ? "" : value.toString();
    }

    private static String formatPrice(final double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static Map<String, String> orderedEvidence(final String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain complete key/value pairs");
        }
        final Map<String, String> ordered = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            ordered.put(entries[index], entries[index + 1]);
        }
        return Map.copyOf(ordered);
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void saveSummary(final String json, final Path summaryPath, final String description) {
        try {
            Files.createDirectories(summaryPath.getParent());
            Files.writeString(summaryPath, json);
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to write " + description + " " + summaryPath, exception);
        }
    }

    /**
     * Canonical engine result shared by historical and live report generation.
     *
     * @param historicalStudy optional historical macro-study when the caller asked
     *                        for completed-cycle evaluation
     * @param currentCycle    series-native current-cycle analysis
     */
    record CanonicalStructure(Optional<MacroStudy> historicalStudy, CurrentCycleAnalysis currentCycle) {

        CanonicalStructure {
            historicalStudy = historicalStudy == null ? Optional.empty() : historicalStudy;
            Objects.requireNonNull(currentCycle, "currentCycle");
        }
    }

    private record LivePresetExecution(CurrentCycleAnalysis currentCycle, LivePresetReport report) {

        LivePresetExecution {
            Objects.requireNonNull(currentCycle, "currentCycle");
            Objects.requireNonNull(report, "report");
        }
    }

    private record LivePresetLegacyView(LivePresetReport report, List<LivePresetScenarioView> scenarioViews) {

        LivePresetLegacyView {
            Objects.requireNonNull(report, "report");
            scenarioViews = scenarioViews == null ? List.of() : List.copyOf(scenarioViews);
        }
    }

    private record LivePresetScenarioView(String label, String fileName, double probability,
            ElliottWaveAnalysisResult.CurrentCycleCandidate candidate) {

        LivePresetScenarioView {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(candidate, "candidate");
        }
    }
}
