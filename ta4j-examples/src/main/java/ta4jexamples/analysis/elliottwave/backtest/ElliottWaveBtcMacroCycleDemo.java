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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * BTC-first Elliott Wave macro-cycle study and demo.
 *
 * <p>
 * This demo is the BTC-specific reporting wrapper around the unified core
 * Elliott runner. It treats the locked BTC anchor registry as a historical
 * truth set, replays each macro leg through the same core-ranked window
 * selection used by the library, and then renders the resulting study as JSON
 * plus charts. Multiple logic profiles are scored side-by-side so the demo can
 * answer two reproducible questions:
 * <ul>
 * <li>Which constraint profile fits the historical BTC cycles best?</li>
 * <li>Given that winning profile, what phase does the current cycle most
 * resemble?</li>
 * </ul>
 *
 * <p>
 * The historical report and the live preset intentionally use different inputs:
 * the historical report consumes the ossified BTC dataset plus the locked macro
 * anchors, while the live preset must infer the current-cycle start from the
 * supplied series alone. Both paths now rely on the same core Elliott logic;
 * the wrapper remains responsible for BTC resource loading, profile defaults,
 * and chart/report rendering.
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

    private static final Logger LOG = LogManager.getLogger(ElliottWaveBtcMacroCycleDemo.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double EPSILON = 1e-9;

    private ElliottWaveBtcMacroCycleDemo() {
    }

    /**
     * Runs the BTC macro-cycle study and logs the resulting JSON summary.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        DemoReport report = generateReport(DEFAULT_CHART_DIRECTORY);
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    /**
     * Runs the series-native live BTC macro preset on the supplied series and logs
     * the resulting JSON summary.
     *
     * <p>
     * Unlike the historical truth-set report, this entry point discovers the
     * current-cycle start from the supplied series itself. That keeps the live
     * preset causal with respect to the provided bars instead of assuming access to
     * a longer external anchor history, while still using the same core-ranked
     * Elliott logic as the historical BTC study.
     *
     * @param series         live or loaded BTC series to analyze
     * @param chartDirectory directory for the saved current-cycle chart and JSON
     *                       summary
     * @since 0.22.4
     */
    public static void runLivePreset(BarSeries series, Path chartDirectory) {
        LivePresetAnalysis analysis = analyzeLivePreset(series, chartDirectory);
        LivePresetLegacyView legacyView = generateLivePresetLegacyView(series, analysis, chartDirectory);
        logLegacyCompatibleLivePreset(legacyView);
    }

    static DemoReport generateReport(Path chartDirectory) {
        BarSeries series = requireSeries(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE,
                ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME);
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        return ElliottWaveMacroCycleDemo.generateHistoricalReport(series, registry, chartDirectory);
    }

    static DemoReport generateReport(BarSeries series, ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry,
            Path chartDirectory) {
        return ElliottWaveMacroCycleDemo.generateHistoricalReport(series, registry, chartDirectory);
    }

    static LivePresetReport generateLivePresetReport(BarSeries series, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        return analyzeLivePreset(series, chartDirectory).report();
    }

    private static LivePresetAnalysis analyzeLivePreset(BarSeries series, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        MacroLogicProfile profile = defaultLiveMacroProfile();
        CurrentCycleAnalysis currentCycle = evaluateCurrentCycle(series, profile,
                "BTC macro profile prevalidated from historical cycle truth set");
        Optional<Path> chartPath = saveLiveCurrentCycleChart(series, currentCycle, chartDirectory);
        String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        Path summaryPath = chartDirectory.resolve(DEFAULT_LIVE_SUMMARY_FILE_NAME).toAbsolutePath().normalize();
        CurrentCycleSummary summary = currentCycle.summary().withChartPath(chartPathText);
        LivePresetReport report = new LivePresetReport(series.getName(), series.getFirstBar().getEndTime().toString(),
                series.getLastBar().getEndTime().toString(), profile.id(), profile.hypothesisId(), chartPathText,
                summaryPath.toString(), summary);
        saveSummary(report, summaryPath);
        return new LivePresetAnalysis(profile, currentCycle.withSummary(summary), report);
    }

    static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, Path chartDirectory) {
        return ElliottWaveMacroCycleDemo.saveHistoricalChart(series, registry, chartDirectory);
    }

    private static Optional<Path> saveLiveCurrentCycleChart(BarSeries series, CurrentCycleAnalysis currentCycle,
            Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        if (currentCycle.primaryFit() == null) {
            return Optional.empty();
        }

        ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        JFreeChart chart = renderLiveCurrentCycleChart(series, currentCycle);
        return chartWorkflow.saveChartImage(chart, series, DEFAULT_LIVE_CHART_FILE_NAME, DEFAULT_CHART_WIDTH,
                DEFAULT_CHART_HEIGHT);
    }

    private static LivePresetLegacyView generateLivePresetLegacyView(BarSeries series, LivePresetAnalysis analysis,
            Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        List<ElliottWaveAnalysisResult.CurrentCycleCandidate> displayCandidates = analysis.currentCycle()
                .displayCandidates();
        List<LivePresetScenarioView> scenarioViews = buildLivePresetScenarioViews(displayCandidates);
        saveLegacyCompatibleScenarioCharts(series, scenarioViews, chartDirectory, ElliottDegree.CYCLE);
        return new LivePresetLegacyView(analysis.report(), scenarioViews);
    }

    private static List<LivePresetScenarioView> buildLivePresetScenarioViews(
            List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        double totalScore = candidates.stream()
                .mapToDouble(ElliottWaveAnalysisResult.CurrentCycleCandidate::totalScore)
                .sum();
        List<LivePresetScenarioView> scenarioViews = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = candidates.get(index);
            double probability = totalScore <= EPSILON ? 0.0 : candidate.totalScore() / totalScore;
            String label = index == 0 ? "BASE CASE" : "ALTERNATIVE " + index;
            String fileName = index == 0 ? livePresetChartBaseCaseFileName(candidate.fit().scenario())
                    : livePresetChartAlternativeFileName(candidate.fit().scenario(), index);
            scenarioViews.add(new LivePresetScenarioView(label, fileName, probability, candidate));
        }
        return List.copyOf(scenarioViews);
    }

    private static void saveLegacyCompatibleScenarioCharts(BarSeries series, List<LivePresetScenarioView> scenarioViews,
            Path chartDirectory, ElliottDegree degree) {
        if (scenarioViews.isEmpty()) {
            return;
        }

        ChartWorkflow chartWorkflow = new ChartWorkflow(chartDirectory.toString());
        boolean isHeadless = GraphicsEnvironment.isHeadless();
        String trendLabel = formatLegacyTrendLabel(scenarioViews);
        for (LivePresetScenarioView scenarioView : scenarioViews) {
            ChartPlan plan = buildLegacyCompatibleLiveScenarioPlan(series, scenarioView, trendLabel, degree);
            if (!isHeadless) {
                chartWorkflow.display(plan, buildLegacyScenarioWindowTitle(degree, trendLabel, scenarioView.label(),
                        scenarioView.candidate().fit().scenario(), series.getName()));
            }
            chartWorkflow.save(plan, scenarioView.fileName(), DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT);
        }
    }

    private static String formatLegacyTrendLabel(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "TREND: UNKNOWN";
        }

        long bullishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> view.candidate().fit().scenario().isBullish())
                .count();
        long bearishCount = scenarioViews.stream()
                .filter(view -> view.candidate().fit().scenario().hasKnownDirection())
                .filter(view -> !view.candidate().fit().scenario().isBullish())
                .count();
        if (bullishCount == bearishCount) {
            return "TREND: NEUTRAL";
        }
        return bullishCount > bearishCount ? "TREND: BULLISH" : "TREND: BEARISH";
    }

    private static ChartPlan buildLegacyCompatibleLiveScenarioPlan(BarSeries series,
            LivePresetScenarioView scenarioView, String trendLabel, ElliottDegree degree) {
        ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = scenarioView.candidate().fit();
        Color scenarioColor = fit.scenario().isBullish() ? BULLISH_WAVE_COLOR : BEARISH_WAVE_COLOR;
        BarSeriesLabelIndicator labels = new BarSeriesLabelIndicator(series,
                buildWaveLabelsFromScenario(series, fit.scenario(), scenarioColor));
        FixedIndicator<Num> scenarioPath = buildScenarioIndicator(series, fit.scenario(), fit.countLabel());
        ChartWorkflow chartWorkflow = new ChartWorkflow();
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

    private static void logLegacyCompatibleLivePreset(LivePresetLegacyView legacyView) {
        Objects.requireNonNull(legacyView, "legacyView");

        LivePresetReport report = legacyView.report();
        CurrentCycleSummary summary = report.currentCycle();
        List<LivePresetScenarioView> scenarioViews = legacyView.scenarioViews();

        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", summarizeLegacyScenarioViews(scenarioViews));
        LOG.info("Strong consensus: {} | Consensus phase: {}", hasStrongConsensus(scenarioViews),
                consensusPhase(scenarioViews).map(Enum::name).orElse("NONE"));
        LOG.info("Trend bias: {}", formatLegacyTrendLabel(scenarioViews));
        LOG.info("Historical status: {}", summary.historicalStatus());
        if (!scenarioViews.isEmpty()) {
            LivePresetScenarioView baseCase = scenarioViews.getFirst();
            ElliottWaveAnalysisResult.CurrentPhaseAssessment fit = baseCase.candidate().fit();
            ElliottScenario scenario = baseCase.candidate().fit().scenario();
            ElliottConfidence confidence = scenario.confidence();
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
                LivePresetScenarioView alternative = scenarioViews.get(index);
                ElliottScenario scenario = alternative.candidate().fit().scenario();
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

    private static String summarizeLegacyScenarioViews(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return "No scenarios";
        }
        LivePresetScenarioView baseCase = scenarioViews.getFirst();
        StringBuilder summary = new StringBuilder();
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
        Optional<ElliottPhase> consensus = consensusPhase(scenarioViews);
        consensus.ifPresent(phase -> summary.append(", consensus=").append(phase));
        return summary.toString();
    }

    private static boolean hasStrongConsensus(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return false;
        }
        if (scenarioViews.size() == 1) {
            return true;
        }
        double spread = scenarioViews.getFirst().probability() - scenarioViews.get(1).probability();
        return spread >= 0.08;
    }

    private static Optional<ElliottPhase> consensusPhase(List<LivePresetScenarioView> scenarioViews) {
        if (scenarioViews.isEmpty()) {
            return Optional.empty();
        }

        Map<ElliottPhase, Double> phaseWeights = new LinkedHashMap<>();
        for (LivePresetScenarioView scenarioView : scenarioViews) {
            ElliottScenario scenario = scenarioView.candidate().fit().scenario();
            phaseWeights.merge(scenario.currentPhase(), scenarioView.probability(), Double::sum);
        }
        Map.Entry<ElliottPhase, Double> bestPhase = phaseWeights.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (bestPhase == null || bestPhase.getValue() < 0.60) {
            return Optional.empty();
        }
        return Optional.of(bestPhase.getKey());
    }

    private static String buildLegacyScenarioTitle(ElliottDegree degree, BarSeries series, String trendLabel,
            String scenarioLabel, ElliottScenario scenario) {
        return String.format(java.util.Locale.ROOT, "Elliott Wave (%s) - %s - %s - %s: %s (%s) - %.1f%% confidence",
                degree, series.getName(), trendLabel, scenarioLabel, scenario.currentPhase(), scenario.type(),
                scenario.confidence().asPercentage());
    }

    private static String buildLegacyScenarioWindowTitle(ElliottDegree degree, String trendLabel, String scenarioLabel,
            ElliottScenario scenario, String seriesName) {
        return String.format(java.util.Locale.ROOT, "%s - %s - %s: %s (%s) - %.1f%% - %s", degree, trendLabel,
                scenarioLabel, scenario.currentPhase(), scenario.type(), scenario.confidence().asPercentage(),
                seriesName);
    }

    private static String livePresetChartBaseCaseFileName(ElliottScenario scenario) {
        return "elliott-wave-analysis-" + scenarioSeriesName(scenario) + "-cycle-base-case";
    }

    private static String livePresetChartAlternativeFileName(ElliottScenario scenario, int alternativeIndex) {
        return "elliott-wave-analysis-" + scenarioSeriesName(scenario) + "-cycle-alternative-" + alternativeIndex;
    }

    private static String scenarioSeriesName(ElliottScenario scenario) {
        return "btc-usd";
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return ElliottWaveMacroCycleDemo.renderHistoricalChart(series, registry);
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, MacroStudy study) {
        return ElliottWaveMacroCycleDemo.renderHistoricalChart(series, registry);
    }

    private static JFreeChart renderLiveCurrentCycleChart(BarSeries series, CurrentCycleAnalysis currentCycle) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(currentCycle, "currentCycle");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ElliottWaveAnalysisResult.CurrentPhaseAssessment primaryFit = currentCycle.primaryFit();
        ElliottWaveAnalysisResult.CurrentPhaseAssessment alternateFit = currentCycle.alternateFit();
        BarSeriesLabelIndicator primaryLabels = primaryFit == null ? new BarSeriesLabelIndicator(series, List.of())
                : new BarSeriesLabelIndicator(series,
                        buildWaveLabelsFromScenario(series, primaryFit.scenario(), BULLISH_WAVE_COLOR));
        FixedIndicator<Num> primaryPath = primaryFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().primaryCount())
                : buildScenarioIndicator(series, primaryFit.scenario(), currentCycle.summary().primaryCount());
        FixedIndicator<Num> alternatePath = alternateFit == null
                ? emptyScenarioIndicator(series, currentCycle.summary().alternateCount())
                : buildScenarioIndicator(series, alternateFit.scenario(), currentCycle.summary().alternateCount());

        ChartPlan plan;
        if (alternateFit != null && !currentCycle.summary().alternateCount().isBlank()) {
            plan = chartWorkflow.builder()
                    .withTitle("BTC live macro current cycle")
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(alternatePath)
                    .withLineColor(BULLISH_CANDIDATE_COLOR)
                    .withLineWidth(2.0f)
                    .withOpacity(0.55f)
                    .withLabel(currentCycle.summary().alternateCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        } else {
            plan = chartWorkflow.builder()
                    .withTitle("BTC live macro current cycle")
                    .withSeries(series)
                    .withIndicatorOverlay(primaryPath)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(3.0f)
                    .withOpacity(0.82f)
                    .withLabel(currentCycle.summary().primaryCount())
                    .withIndicatorOverlay(primaryLabels)
                    .withLineColor(BULLISH_WAVE_COLOR)
                    .withLineWidth(2.2f)
                    .withOpacity(0.95f)
                    .withLabel("Current wave labels")
                    .toPlan();
        }
        JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    static MacroStudy evaluateMacroStudy(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        return ElliottWaveMacroCycleDemo.evaluateMacroStudy(series, registry);
    }

    static List<BarLabel> buildWaveLabelsFromScenario(BarSeries series, ElliottScenario scenario, Color labelColor) {
        return ElliottWaveMacroCycleDemo.buildWaveLabelsFromScenario(series, scenario, labelColor);
    }

    private static CurrentCycleAnalysis evaluateCurrentCycle(BarSeries series, MacroLogicProfile profile,
            String historicalStatus) {
        return ElliottWaveMacroCycleDemo.evaluateCurrentCycle(series, profile, historicalStatus);
    }

    private static SegmentScenarioFit fitFromCoreAssessment(LegSegment legSegment, MacroLogicProfile profile,
            ElliottWaveAnalysisResult.WindowScenarioAssessment assessment, boolean bullish, int startIndex,
            int endIndex) {
        return ElliottWaveMacroCycleDemo.fitFromCoreAssessment(legSegment, profile, assessment, bullish, startIndex,
                endIndex);
    }

    private static double safeConfidenceScore(double score) {
        return Double.isFinite(score) ? clamp(score, 0.0, 1.0) : 0.0;
    }

    private static double safeConfidenceScore(Num score) {
        if (score == null || score.isNaN()) {
            return 0.0;
        }
        return safeConfidenceScore(score.doubleValue());
    }

    private static FixedIndicator<Num> buildScenarioIndicator(BarSeries series, ElliottScenario scenario,
            String label) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        applyScenarioPath(values, series, scenario);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static FixedIndicator<Num> emptyScenarioIndicator(BarSeries series, String label) {
        Num[] values = new Num[series.getEndIndex() + 1];
        Arrays.fill(values, NaN);
        return new FixedIndicator<>(series, values) {
            @Override
            public String toString() {
                return label;
            }
        };
    }

    private static void applyScenarioPath(Num[] values, BarSeries series, ElliottScenario scenario) {
        for (ElliottSwing swing : scenario.swings()) {
            int fromIndex = Math.max(series.getBeginIndex(), Math.min(swing.fromIndex(), swing.toIndex()));
            int toIndex = Math.min(series.getEndIndex(), Math.max(swing.fromIndex(), swing.toIndex()));
            if (toIndex < fromIndex) {
                continue;
            }
            double fromPrice = swing.fromPrice().doubleValue();
            double toPrice = swing.toPrice().doubleValue();
            int length = Math.max(1, toIndex - fromIndex);
            for (int index = fromIndex; index <= toIndex; index++) {
                double progress = (double) (index - fromIndex) / length;
                double interpolated = interpolateOverlayPrice(fromPrice, toPrice, progress);
                values[index] = series.numFactory().numOf(interpolated);
            }
        }
    }

    static double interpolateOverlayPrice(double fromPrice, double toPrice, double progress) {
        return ElliottWaveMacroCycleDemo.interpolateOverlayPrice(fromPrice, toPrice, progress);
    }

    private static void applyLogPriceAxis(JFreeChart chart, BarSeries series) {
        ElliottWaveMacroCycleDemo.applyLogPriceAxis(chart, series);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BarSeries requireSeries(String resource, String seriesName) {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class, resource,
                seriesName, LOG);
        if (series == null) {
            throw new IllegalStateException("Unable to load required resource " + resource);
        }
        return series;
    }

    private static void saveSummary(DemoReport report, Path summaryPath) {
        saveJsonSummary(report.toJson(), summaryPath, "BTC macro-cycle summary");
    }

    private static void saveSummary(LivePresetReport report, Path summaryPath) {
        saveJsonSummary(report.toJson(), summaryPath, "BTC live macro summary");
    }

    private static void saveJsonSummary(String json, Path summaryPath, String description) {
        try {
            Files.createDirectories(summaryPath.getParent());
            Files.writeString(summaryPath, json);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write " + description + " " + summaryPath, exception);
        }
    }

    private static List<MacroLogicProfile> logicProfiles() {
        return ElliottWaveMacroCycleDemo.logicProfiles();
    }

    private static MacroLogicProfile defaultLiveMacroProfile() {
        return ElliottWaveMacroCycleDemo.defaultLiveMacroProfile();
    }

    private static String formatInvalidationCondition(ElliottScenario scenario, Num value) {
        return ElliottWaveMacroCycleDemo.formatInvalidationCondition(scenario, value);
    }

    record DemoReport(String registryVersion, String datasetResource, String baselineProfileId,
            String selectedProfileId, String selectedHypothesisId, boolean historicalFitPassed,
            String harnessDecisionRationale, String chartPath, String summaryPath,
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
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    record LivePresetReport(String seriesName, String startTimeUtc, String latestTimeUtc, String selectedProfileId,
            String selectedHypothesisId, String chartPath, String summaryPath, CurrentCycleSummary currentCycle) {

        LivePresetReport {
            Objects.requireNonNull(seriesName, "seriesName");
            Objects.requireNonNull(startTimeUtc, "startTimeUtc");
            Objects.requireNonNull(latestTimeUtc, "latestTimeUtc");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(selectedHypothesisId, "selectedHypothesisId");
            Objects.requireNonNull(chartPath, "chartPath");
            Objects.requireNonNull(summaryPath, "summaryPath");
            Objects.requireNonNull(currentCycle, "currentCycle");
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    private record LivePresetAnalysis(MacroLogicProfile profile, CurrentCycleAnalysis currentCycle,
            LivePresetReport report) {

        LivePresetAnalysis {
            Objects.requireNonNull(profile, "profile");
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

    record ProfileScoreSummary(String profileId, String hypothesisId, String title, double aggregateScore,
            int acceptedCycles, int acceptedSegments, boolean historicalFitPassed) {

        static ProfileScoreSummary from(MacroProfileEvaluation evaluation) {
            return new ProfileScoreSummary(evaluation.profile().id(), evaluation.profile().hypothesisId(),
                    evaluation.profile().title(), evaluation.aggregateScore(), evaluation.acceptedCycles(),
                    evaluation.acceptedSegments(), evaluation.historicalFitPassed());
        }
    }

    record DirectionalCycleSummary(String partition, String cycleId, String impulseLabel, String peakLabel,
            String correctionLabel, String lowLabel, String startTimeUtc, String peakTimeUtc, String lowTimeUtc,
            double bullishScore, double bearishScore, boolean bullishAccepted, boolean bearishAccepted,
            boolean accepted, String status) {

        static DirectionalCycleSummary from(CycleFit cycleFit) {
            MacroCycle cycle = cycleFit.cycle();
            String status;
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

        CurrentCycleSummary withChartPath(String newChartPath) {
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

        CurrentCycleAnalysis withSummary(CurrentCycleSummary updatedSummary) {
            return new CurrentCycleAnalysis(updatedSummary, primaryFit, alternateFit, candidates, displayCandidates);
        }
    }

    record MacroStudy(MacroProfileEvaluation selectedProfile, List<MacroProfileEvaluation> evaluations,
            List<ProfileScoreSummary> profileScores, List<DirectionalCycleSummary> cycles,
            List<HypothesisResult> hypotheses, CurrentCycleSummary currentCycle,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment currentPrimaryFit,
            ElliottWaveAnalysisResult.CurrentPhaseAssessment currentAlternateFit) {

        MacroStudy {
            Objects.requireNonNull(selectedProfile, "selectedProfile");
            evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
            profileScores = profileScores == null ? List.of() : List.copyOf(profileScores);
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
            Objects.requireNonNull(currentCycle, "currentCycle");
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

        static CycleFit create(MacroCycle cycle, SegmentScenarioFit bullishFit, SegmentScenarioFit bearishFit) {
            double bullishScore = bullishFit == null ? 0.0 : bullishFit.fitScore();
            double bearishScore = bearishFit == null ? 0.0 : bearishFit.fitScore();
            double aggregate = (bullishScore + bearishScore) / 2.0;
            boolean accepted = bullishFit != null && bullishFit.accepted() && bearishFit != null
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
        public int compareTo(SegmentScenarioFit other) {
            int acceptedComparison = Boolean.compare(other.accepted, accepted);
            if (acceptedComparison != 0) {
                return acceptedComparison;
            }
            int scoreComparison = Double.compare(other.fitScore, fitScore);
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
