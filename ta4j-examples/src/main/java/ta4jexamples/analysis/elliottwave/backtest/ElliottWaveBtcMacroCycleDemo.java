/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.awt.Color;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottLogicProfile;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;

/**
 * BTC-specific wrapper around the generic macro-cycle demo.
 *
 * <p>
 * The generic demo now owns both the historical macro study and the live
 * current-cycle reporting flow. This wrapper exists to keep the fixed BTC
 * dataset entry points, the locked BTC anchor truth set, and the canonical BTC
 * chart/summary filenames stable for users and regression tests.
 *
 * @since 0.22.4
 */
public final class ElliottWaveBtcMacroCycleDemo {

    static final String RESULT_PREFIX = "EW_BTC_MACRO_DEMO: ";
    static final String LIVE_RESULT_PREFIX = "EW_BTC_LIVE_MACRO: ";
    static final Path DEFAULT_CHART_DIRECTORY = Path.of("temp", "charts");
    static final String DEFAULT_CHART_FILE_NAME = "elliott-wave-btc-macro-cycles";
    static final String DEFAULT_SUMMARY_FILE_NAME = "elliott-wave-btc-macro-cycles-summary.json";
    static final String DEFAULT_LIVE_CHART_FILE_NAME = "elliott-wave-btc-live-macro-current-cycle";
    static final String DEFAULT_LIVE_SUMMARY_FILE_NAME = "elliott-wave-btc-live-macro-current-cycle-summary.json";
    static final int DEFAULT_CHART_WIDTH = 3840;
    static final int DEFAULT_CHART_HEIGHT = 2160;
    static final int MIN_CORE_SEGMENT_SCENARIOS = 1000;
    static final int MAX_CORE_ANCHOR_DRIFT_BARS = 3;
    static final double DEFAULT_ACCEPTED_SEGMENT_SCORE = 0.64;
    static final int LABEL_CLUSTER_BAR_GAP = 18;
    static final Color BULLISH_LEG_COLOR = new Color(0x66BB6A);
    static final Color BEARISH_LEG_COLOR = new Color(0xEF5350);
    static final Color BULLISH_WAVE_COLOR = new Color(0x81C784);
    static final Color BEARISH_WAVE_COLOR = new Color(0xE57373);
    static final Color BULLISH_CANDIDATE_COLOR = new Color(0xC8E6C9);
    static final Color BEARISH_CANDIDATE_COLOR = new Color(0xFFCDD2);
    static final Color ANCHOR_OVERLAY_COLOR = new Color(0xCFD8DC);
    static final double WAVE_LABEL_FONT_SCALE = 3.0;
    static final double EPSILON = 1e-9;

    private static final Logger LOG = LogManager.getLogger(ElliottWaveBtcMacroCycleDemo.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BTC_LIVE_HISTORICAL_STATUS = "BTC macro profile prevalidated from historical cycle truth set";

    private ElliottWaveBtcMacroCycleDemo() {
    }

    /**
     * Runs the BTC macro-cycle study and logs the resulting JSON summary.
     *
     * @param args unused
     */
    public static void main(final String[] args) {
        final DemoReport report = generateReport(DEFAULT_CHART_DIRECTORY);
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    /**
     * Runs the series-native live BTC macro preset on the supplied series and logs
     * the resulting JSON summary.
     *
     * @param series         live or loaded BTC series to analyze
     * @param chartDirectory directory for the saved current-cycle chart and JSON
     *                       summary
     * @since 0.22.4
     */
    public static void runLivePreset(final BarSeries series, final Path chartDirectory) {
        ElliottWaveMacroCycleDemo.runLivePreset(series, chartDirectory, DEFAULT_LIVE_CHART_FILE_NAME,
                DEFAULT_LIVE_SUMMARY_FILE_NAME, "btc-usd", BTC_LIVE_HISTORICAL_STATUS);
    }

    static DemoReport generateReport(final Path chartDirectory) {
        final BarSeries series = requireSeries(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE,
                ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME);
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        return DemoReport.from(ElliottWaveMacroCycleDemo.generateHistoricalReport(series, registry, chartDirectory));
    }

    static DemoReport generateReport(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        return DemoReport.from(ElliottWaveMacroCycleDemo.generateHistoricalReport(series, registry, chartDirectory));
    }

    static LivePresetReport generateLivePresetReport(final BarSeries series, final Path chartDirectory) {
        return LivePresetReport.from(ElliottWaveMacroCycleDemo.generateLivePresetReport(series, chartDirectory,
                DEFAULT_LIVE_CHART_FILE_NAME, DEFAULT_LIVE_SUMMARY_FILE_NAME, BTC_LIVE_HISTORICAL_STATUS));
    }

    static Optional<Path> saveMacroCycleChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        return ElliottWaveMacroCycleDemo.saveHistoricalChart(series, registry, chartDirectory);
    }

    static JFreeChart renderMacroCycleChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return ElliottWaveMacroCycleDemo.renderHistoricalChart(series, registry);
    }

    static JFreeChart renderMacroCycleChart(final BarSeries series, final MacroStudy study) {
        return ElliottWaveMacroCycleDemo.renderHistoricalChart(series, study);
    }

    static MacroStudy evaluateMacroStudy(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return ElliottWaveMacroCycleDemo.evaluateMacroStudy(series, registry);
    }

    private static BarSeries requireSeries(final String resource, final String seriesName) {
        final BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                resource, seriesName, LOG);
        if (series == null) {
            throw new IllegalStateException("Unable to load required resource " + resource);
        }
        return series;
    }

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

        static DemoReport from(final ElliottWaveMacroCycleDemo.DemoReport report) {
            return new DemoReport(report.registryVersion(), report.datasetResource(), report.baselineProfileId(),
                    report.selectedProfileId(), report.selectedHypothesisId(), report.historicalFitPassed(),
                    report.harnessDecisionRationale(), report.chartPath(), report.summaryPath(),
                    report.structureSource(), report.profileScores(), report.cycles(), report.hypotheses(),
                    report.currentCycle());
        }
    }

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

        static LivePresetReport from(final ElliottWaveMacroCycleDemo.LivePresetReport report) {
            return new LivePresetReport(report.seriesName(), report.startTimeUtc(), report.latestTimeUtc(),
                    report.selectedProfileId(), report.selectedHypothesisId(), report.chartPath(), report.summaryPath(),
                    report.structureSource(), report.currentCycle());
        }
    }

    record ProfileScoreSummary(String profileId, String hypothesisId, String title, double aggregateScore,
            int acceptedCycles, int acceptedSegments, boolean historicalFitPassed) {

        static ProfileScoreSummary from(final MacroProfileEvaluation evaluation) {
            return new ProfileScoreSummary(evaluation.profile().id(), evaluation.profile().hypothesisId(),
                    evaluation.profile().title(), evaluation.aggregateScore(), evaluation.acceptedCycles(),
                    evaluation.acceptedSegments(), evaluation.historicalFitPassed());
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

    record MacroProfileEvaluation(MacroLogicProfile profile, double aggregateScore, int acceptedCycles,
            int acceptedSegments, boolean historicalFitPassed, List<CycleFit> cycleFits,
            List<SegmentScenarioFit> chartSegments) {

        MacroProfileEvaluation {
            Objects.requireNonNull(profile, "profile");
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

        Instant earliestAcceptedStartTime() {
            return cycleFits.stream()
                    .filter(CycleFit::accepted)
                    .map(cycleFit -> cycleFit.cycle().start().at())
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.MAX);
        }
    }

    record MacroLogicProfile(String id, String hypothesisId, String title, int orthodoxyRank,
            ElliottLogicProfile coreLogicProfile, ElliottDegree runnerDegree) {

        MacroLogicProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hypothesisId, "hypothesisId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(coreLogicProfile, "coreLogicProfile");
            Objects.requireNonNull(runnerDegree, "runnerDegree");
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
}
