/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.awt.Color;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.PatternSet;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.storage.FileSystemChartStorage;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Ossified BTC macro-cycle demo for Elliott Wave cycle validation.
 *
 * <p>
 * The demo keeps the long-history TradingView BTC daily export and the strict
 * BTC macro-cycle registry on disk, then turns the latest harness state into a
 * user-facing artifact:
 * <ul>
 * <li>Explicit bullish {@code 1-2-3-4-5} versus bearish {@code A-B-C} cycle
 * labels</li>
 * <li>A saved chart with the curated BTC anchor points overlaid</li>
 * <li>Evidence-backed hypotheses for the current 2021/2022 miss</li>
 * </ul>
 *
 * <p>
 * The output is emitted as a single JSON payload prefixed with
 * {@value #RESULT_PREFIX}. This keeps the demo reproducible and easy to inspect
 * from the command line or CI logs.
 *
 * @since 0.22.4
 */
public final class ElliottWaveBtcMacroCycleDemo {

    static final String RESULT_PREFIX = "EW_BTC_MACRO_DEMO: ";
    static final Path DEFAULT_CHART_DIRECTORY = Path.of("temp", "charts");
    static final String DEFAULT_CHART_FILE_NAME = "elliott-wave-btc-macro-cycles";
    static final int DEFAULT_CHART_WIDTH = 2560;
    static final int DEFAULT_CHART_HEIGHT = 1440;
    static final String RECENT_TOP_ANCHOR_ID = "btc-2021-cycle-top";
    static final String RECENT_LOW_ANCHOR_ID = "btc-2022-cycle-bottom";

    private static final Logger LOG = LogManager.getLogger(ElliottWaveBtcMacroCycleDemo.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ElliottWaveBtcMacroCycleDemo() {
    }

    /**
     * Runs the ossified BTC macro-cycle demo.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        DemoReport report = generateReport(DEFAULT_CHART_DIRECTORY);
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    static DemoReport generateReport(Path chartDirectory) {
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        ElliottWaveAnchorCalibrationHarness.ReportBundle harnessReport = ElliottWaveAnchorCalibrationHarness
                .generateDefaultReport();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = harnessReport.anchorRegistry();
        BarSeries series = requireSeries(registry.datasetResource(),
                ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME);

        List<DirectionalCycleSummary> cycles = describeCycles(harnessReport);
        Optional<Path> chartPath = saveMacroCycleChart(series, registry, chartDirectory);

        MacroCycleProbe baselineMinuteProbe = probeRecentMacroCycle(series, registry,
                buildRunner(ElliottDegree.MINUTE, 2), "minute-f2-h2l2-max25-sw0");
        MacroCycleProbe minorProbe = probeRecentMacroCycle(series, registry, buildRunner(ElliottDegree.MINOR, 2),
                "minor-f2-h2l2-max25-sw0");
        int expectedHoldoutAnchors = (int) registry.anchors()
                .stream()
                .filter(anchor -> anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT)
                .count();

        List<HypothesisResult> hypotheses = List.of(
                holdoutGeometryHypothesis(expectedHoldoutAnchors,
                        harnessReport.baselineEvaluation().anchors().holdout().anchorCount(),
                        harnessReport.baselineEvaluation().anchors().holdout().sampleCount(),
                        harnessReport.baselineEvaluation().cycles().holdout().cycleCount()),
                startOffsetHypothesis(baselineMinuteProbe), coarserDegreeHypothesis(baselineMinuteProbe, minorProbe),
                correctiveCoverageHypothesis(minorProbe.lowAnchor()));

        String chartPathText = chartPath.map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        return new DemoReport(registry.version(), registry.datasetResource(),
                harnessReport.baselineEvaluation().profile().id(), harnessReport.decision().selectedProfileId(),
                harnessReport.decision().rationale(), chartPathText, cycles, hypotheses);
    }

    static List<DirectionalCycleSummary> describeCycles(
            ElliottWaveAnchorCalibrationHarness.ReportBundle harnessReport) {
        Objects.requireNonNull(harnessReport, "harnessReport");

        List<DirectionalCycleSummary> cycles = new ArrayList<>();
        for (ElliottWaveAnchorCalibrationHarness.CycleSummary summary : harnessReport.baselineEvaluation()
                .cycles()
                .validation()
                .cycles()) {
            cycles.add(DirectionalCycleSummary.from("validation", summary));
        }
        for (ElliottWaveAnchorCalibrationHarness.CycleSummary summary : harnessReport.baselineEvaluation()
                .cycles()
                .holdout()
                .cycles()) {
            cycles.add(DirectionalCycleSummary.from("holdout", summary));
        }
        return List.copyOf(cycles);
    }

    static Optional<Path> saveMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");

        JFreeChart chart = renderMacroCycleChart(series, registry);
        FileSystemChartStorage storage = new FileSystemChartStorage(chartDirectory);
        return storage.save(chart, series, DEFAULT_CHART_FILE_NAME, DEFAULT_CHART_WIDTH, DEFAULT_CHART_HEIGHT);
    }

    static JFreeChart renderMacroCycleChart(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        BarSeriesLabelIndicator anchorLabels = new BarSeriesLabelIndicator(series, buildAnchorLabels(series, registry));
        ChartPlan plan = chartWorkflow.builder()
                .withTitle("BTC macro cycles: bullish 1-5 tops and bearish A-C lows")
                .withSeries(series)
                .withIndicatorOverlay(anchorLabels)
                .withLineColor(new Color(0xFFB74D))
                .withLineWidth(2.0f)
                .withOpacity(0.95f)
                .withLabel("BTC macro-cycle anchors")
                .toPlan();
        JFreeChart chart = chartWorkflow.render(plan);
        applyLogPriceAxis(chart, series);
        return chart;
    }

    static HypothesisResult holdoutGeometryHypothesis(int expectedHoldoutAnchorCount, int observedHoldoutAnchorCount,
            int holdoutSampleCount, int holdoutCycleCount) {
        Map<String, String> evidence = orderedEvidence("expectedHoldoutAnchorCount",
                Integer.toString(expectedHoldoutAnchorCount), "observedHoldoutAnchorCount",
                Integer.toString(observedHoldoutAnchorCount), "holdoutSampleCount",
                Integer.toString(holdoutSampleCount), "holdoutCycleCount", Integer.toString(holdoutCycleCount));
        boolean supported = expectedHoldoutAnchorCount > 0 && observedHoldoutAnchorCount == 0
                && holdoutSampleCount == 0;
        String summary = supported
                ? "The locked walk-forward holdout split does not intersect the 2021/2022 BTC anchor windows on the extended 2009-2026 daily series, so the harness cannot score that recent cycle as holdout yet."
                : "The locked walk-forward holdout split does intersect the recent BTC anchor windows, so split geometry is not the main blocker.";
        return new HypothesisResult("holdout-window-misalignment", "Recent cycle sits outside the locked holdout fold",
                supported, summary, evidence);
    }

    static HypothesisResult startOffsetHypothesis(MacroCycleProbe baselineProbe) {
        Objects.requireNonNull(baselineProbe, "baselineProbe");

        AnchorProbeObservation peak = baselineProbe.peakAnchor();
        AnchorProbeObservation low = baselineProbe.lowAnchor();
        boolean peakNeedsExpandedSearch = improvedBeyondLegacyStartCap(peak);
        boolean lowNeedsExpandedSearch = improvedBeyondLegacyStartCap(low);
        boolean supported = peakNeedsExpandedSearch || lowNeedsExpandedSearch;

        Map<String, String> evidence = orderedEvidence("peakBestRank", Integer.toString(peak.bestRank()),
                "peakLegacyBestRank", Integer.toString(peak.legacyBestRank()), "peakStartIndex",
                Integer.toString(peak.matchedScenarioStartIndex()), "lowBestRank", Integer.toString(low.bestRank()),
                "lowLegacyBestRank", Integer.toString(low.legacyBestRank()), "lowStartIndex",
                Integer.toString(low.matchedScenarioStartIndex()));
        String summary = supported
                ? "A later-starting exact-anchor match appears only when the generator can search beyond the legacy first-three swing starts, so the hard-coded start cap was suppressing at least one BTC macro interpretation."
                : "Even with the wider start-offset search, the exact-anchor BTC probe still misses or ties the legacy first-three-start subset, so the remaining 2021/2022 miss is not just a start-offset problem.";
        return new HypothesisResult("full-start-offset-search",
                "Later-starting macro counts were clipped by the first-three-start search cap", supported, summary,
                evidence);
    }

    static HypothesisResult coarserDegreeHypothesis(MacroCycleProbe baselineMinuteProbe, MacroCycleProbe minorProbe) {
        Objects.requireNonNull(baselineMinuteProbe, "baselineMinuteProbe");
        Objects.requireNonNull(minorProbe, "minorProbe");

        boolean supported = minorProbe.top3MatchCount() > baselineMinuteProbe.top3MatchCount()
                || minorProbe.rankScore() > baselineMinuteProbe.rankScore();
        Map<String, String> evidence = orderedEvidence("minutePeakRank",
                Integer.toString(baselineMinuteProbe.peakAnchor().bestRank()), "minuteLowRank",
                Integer.toString(baselineMinuteProbe.lowAnchor().bestRank()), "minorPeakRank",
                Integer.toString(minorProbe.peakAnchor().bestRank()), "minorLowRank",
                Integer.toString(minorProbe.lowAnchor().bestRank()));
        String summary = supported
                ? "A coarser MINOR base degree improves the recent BTC exact-anchor ranks relative to the tuned MINUTE baseline, so the daily macro cycle likely wants a slower degree than the current walk-forward default."
                : "Switching the exact-anchor probe from MINUTE to MINOR does not improve the recent BTC macro-cycle ranks enough to explain the 2021/2022 miss by degree alone.";
        return new HypothesisResult("coarser-base-degree",
                "The daily BTC macro cycle may need a coarser base degree than the tuned MINUTE profile", supported,
                summary, evidence);
    }

    static HypothesisResult correctiveCoverageHypothesis(AnchorProbeObservation lowAnchor) {
        Objects.requireNonNull(lowAnchor, "lowAnchor");

        int triangleCount = lowAnchor.scenarioTypeCounts().getOrDefault(ScenarioType.CORRECTIVE_TRIANGLE.name(), 0);
        int complexCount = lowAnchor.scenarioTypeCounts().getOrDefault(ScenarioType.CORRECTIVE_COMPLEX.name(), 0);
        boolean supported = triangleCount == 0 && complexCount == 0;
        Map<String, String> evidence = orderedEvidence("impulseCount",
                Integer.toString(lowAnchor.scenarioTypeCounts().getOrDefault(ScenarioType.IMPULSE.name(), 0)),
                "zigzagCount",
                Integer.toString(lowAnchor.scenarioTypeCounts().getOrDefault(ScenarioType.CORRECTIVE_ZIGZAG.name(), 0)),
                "flatCount",
                Integer.toString(lowAnchor.scenarioTypeCounts().getOrDefault(ScenarioType.CORRECTIVE_FLAT.name(), 0)),
                "triangleCount", Integer.toString(triangleCount), "complexCount", Integer.toString(complexCount));
        String summary = supported
                ? "The recent 2022 low probe still searches only impulses, zigzags, and flats. Triangle and complex corrective families remain absent from the generated scenario set, so the current core logic cannot even express those BTC correction shapes yet."
                : "Triangle or complex corrective scenarios are already present in the recent 2022 low probe, so corrective-family coverage is not the current search-space bottleneck.";
        return new HypothesisResult("missing-complex-correctives",
                "The current generator may be missing the corrective family BTC 2021-2022 needs", supported, summary,
                evidence);
    }

    private static MacroCycleProbe probeRecentMacroCycle(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, ElliottWaveAnalysisRunner runner,
            String profileId) {
        ElliottWaveAnchorCalibrationHarness.Anchor peakAnchor = findAnchor(registry, RECENT_TOP_ANCHOR_ID);
        ElliottWaveAnchorCalibrationHarness.Anchor lowAnchor = findAnchor(registry, RECENT_LOW_ANCHOR_ID);
        return new MacroCycleProbe(profileId, probeAnchor(series, peakAnchor, runner),
                probeAnchor(series, lowAnchor, runner));
    }

    private static ElliottWaveAnalysisRunner buildRunner(ElliottDegree degree, int fractalWindow) {
        return ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(2)
                .lowerDegrees(2)
                .patternSet(PatternSet.all())
                .maxScenarios(25)
                .minConfidence(0.0)
                .scenarioSwingWindow(0)
                .swingDetector(SwingDetectors.fractal(fractalWindow))
                .swingFilter(swings -> swings == null ? List.of() : List.copyOf(swings))
                .build();
    }

    private static AnchorProbeObservation probeAnchor(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.Anchor anchor, ElliottWaveAnalysisRunner runner) {
        int anchorIndex = nearestIndex(series, anchor.at());
        BarSeries prefix = series.getSubSeries(series.getBeginIndex(), anchorIndex + 1);
        ElliottWaveAnalysisResult result = runner.analyze(prefix);
        ElliottAnalysisResult baseAnalysis = result.analysisFor(result.baseDegree())
                .orElseThrow(() -> new IllegalStateException("Base analysis missing for " + result.baseDegree()))
                .analysis();

        int bestRank = 0;
        int legacyBestRank = 0;
        ElliottScenario bestScenario = null;
        int rank = 1;
        for (ElliottWaveAnalysisResult.BaseScenarioAssessment assessment : result.rankedBaseScenarios()) {
            ElliottScenario scenario = assessment.scenario();
            if (!matchesAnchor(scenario, anchor)) {
                rank++;
                continue;
            }
            if (bestRank == 0) {
                bestRank = rank;
                bestScenario = scenario;
            }
            if (scenario.startIndex() <= 2 && legacyBestRank == 0) {
                legacyBestRank = rank;
            }
            rank++;
        }

        Map<String, Integer> scenarioTypeCounts = countScenarioTypes(baseAnalysis.scenarios().all());
        String matchedScenarioId = bestScenario == null ? "" : bestScenario.id();
        String matchedScenarioType = bestScenario == null ? "" : bestScenario.type().name();
        int matchedScenarioStartIndex = bestScenario == null ? -1 : bestScenario.startIndex();
        double matchedConfidence = bestScenario == null ? Double.NaN : bestScenario.confidenceScore().doubleValue();
        return new AnchorProbeObservation(anchor.id(), anchor.at().toString(), expectedLabel(anchor), bestRank,
                legacyBestRank, matchedScenarioId, matchedScenarioType, matchedScenarioStartIndex, matchedConfidence,
                baseAnalysis.processedSwings().size(), baseAnalysis.scenarios().all().size(), scenarioTypeCounts);
    }

    private static List<BarLabel> buildAnchorLabels(BarSeries series,
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        List<BarLabel> labels = new ArrayList<>();
        Num topPad = series.numFactory().numOf("1.02");
        Num lowPad = series.numFactory().numOf("0.98");
        for (ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            int barIndex = nearestIndex(series, anchor.at());
            Bar bar = series.getBar(barIndex);
            boolean top = anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP;
            Num yValue = top ? bar.getHighPrice().multipliedBy(topPad) : bar.getLowPrice().multipliedBy(lowPad);
            String date = bar.getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString();
            String text = top ? "Bullish 1-5 top\n" + date : "Bearish A-C low\n" + date;
            LabelPlacement placement = top ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
            labels.add(new BarLabel(barIndex, yValue, text, placement));
        }
        return List.copyOf(labels);
    }

    private static void applyLogPriceAxis(JFreeChart chart, BarSeries series) {
        if (!(chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot)) {
            return;
        }
        if (combinedPlot.getSubplots() == null || combinedPlot.getSubplots().isEmpty()) {
            return;
        }

        Object subplot = combinedPlot.getSubplots().getFirst();
        if (!(subplot instanceof XYPlot pricePlot)) {
            return;
        }

        LogAxis logAxis = new LogAxis("Price (USD, log)");
        logAxis.setAutoRange(true);
        logAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        logAxis.setLabelPaint(Color.LIGHT_GRAY);
        logAxis.setSmallestValue(smallestPositiveLow(series));
        pricePlot.setRangeAxis(logAxis);
    }

    private static double smallestPositiveLow(BarSeries series) {
        double smallest = Double.POSITIVE_INFINITY;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            double low = series.getBar(index).getLowPrice().doubleValue();
            if (Double.isFinite(low) && low > 0.0 && low < smallest) {
                smallest = low;
            }
        }
        return Double.isFinite(smallest) ? smallest : 1.0;
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor findAnchor(
            ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, String anchorId) {
        for (ElliottWaveAnchorCalibrationHarness.Anchor anchor : registry.anchors()) {
            if (anchor.id().equals(anchorId)) {
                return anchor;
            }
        }
        throw new IllegalArgumentException("Unknown BTC anchor id: " + anchorId);
    }

    private static boolean matchesAnchor(ElliottScenario scenario, ElliottWaveAnchorCalibrationHarness.Anchor anchor) {
        if (scenario == null || !scenario.hasKnownDirection()) {
            return false;
        }
        ElliottPhase phase = scenario.currentPhase();
        if (!anchor.expectedPhases().contains(phase)) {
            return false;
        }
        if (phase.isImpulse()) {
            return scenario.isBullish();
        }
        if (phase.isCorrective()) {
            return scenario.isBearish();
        }
        return false;
    }

    private static boolean improvedBeyondLegacyStartCap(AnchorProbeObservation observation) {
        return observation.bestRank() > 0
                && (observation.legacyBestRank() == 0 || observation.bestRank() < observation.legacyBestRank());
    }

    private static String expectedLabel(ElliottWaveAnchorCalibrationHarness.Anchor anchor) {
        return anchor.type() == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? "Bullish WAVE5 top"
                : "Bearish CORRECTIVE_C low";
    }

    private static Map<String, Integer> countScenarioTypes(List<ElliottScenario> scenarios) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ScenarioType type : ScenarioType.values()) {
            counts.put(type.name(), 0);
        }
        for (ElliottScenario scenario : scenarios) {
            counts.merge(scenario.type().name(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, String> orderedEvidence(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain complete key/value pairs");
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            ordered.put(entries[index], entries[index + 1]);
        }
        return Map.copyOf(ordered);
    }

    private static int nearestIndex(BarSeries series, Instant target) {
        int nearest = series.getBeginIndex();
        long bestDistance = Long.MAX_VALUE;
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            long distance = Math.abs(series.getBar(index).getEndTime().toEpochMilli() - target.toEpochMilli());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private static BarSeries requireSeries(String resource, String seriesName) {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class, resource,
                seriesName, LOG);
        if (series == null) {
            throw new IllegalStateException("Unable to load required resource " + resource);
        }
        return series;
    }

    record DemoReport(String registryVersion, String datasetResource, String baselineProfileId,
            String selectedProfileId, String harnessDecisionRationale, String chartPath,
            List<DirectionalCycleSummary> cycles, List<HypothesisResult> hypotheses) {

        DemoReport {
            Objects.requireNonNull(registryVersion, "registryVersion");
            Objects.requireNonNull(datasetResource, "datasetResource");
            Objects.requireNonNull(baselineProfileId, "baselineProfileId");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(harnessDecisionRationale, "harnessDecisionRationale");
            Objects.requireNonNull(chartPath, "chartPath");
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    record DirectionalCycleSummary(String partition, String cycleId, String impulseLabel, String peakLabel,
            String correctionLabel, String lowLabel, String startTimeUtc, String peakTimeUtc, String lowTimeUtc,
            int topBestRank, int lowBestRank, boolean orderedTop3Hit, String status) {

        DirectionalCycleSummary {
            Objects.requireNonNull(partition, "partition");
            Objects.requireNonNull(cycleId, "cycleId");
            Objects.requireNonNull(impulseLabel, "impulseLabel");
            Objects.requireNonNull(peakLabel, "peakLabel");
            Objects.requireNonNull(correctionLabel, "correctionLabel");
            Objects.requireNonNull(lowLabel, "lowLabel");
            Objects.requireNonNull(startTimeUtc, "startTimeUtc");
            Objects.requireNonNull(peakTimeUtc, "peakTimeUtc");
            Objects.requireNonNull(lowTimeUtc, "lowTimeUtc");
            Objects.requireNonNull(status, "status");
        }

        static DirectionalCycleSummary from(String partition,
                ElliottWaveAnchorCalibrationHarness.CycleSummary summary) {
            String status = summary.orderedTop3Hit() ? "ordered top-3 cycle match"
                    : summary.topBestRank() == 0 && summary.lowBestRank() == 0 ? "no anchor-window match"
                            : "partial anchor-window match";
            return new DirectionalCycleSummary(partition, summary.cycleId(), "Bullish 1-2-3-4-5", "Bullish WAVE5 top",
                    "Bearish A-B-C", "Bearish CORRECTIVE_C low", summary.startTimeUtc(), summary.peakTimeUtc(),
                    summary.lowTimeUtc(), summary.topBestRank(), summary.lowBestRank(), summary.orderedTop3Hit(),
                    status);
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

    record MacroCycleProbe(String profileId, AnchorProbeObservation peakAnchor, AnchorProbeObservation lowAnchor) {

        MacroCycleProbe {
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(peakAnchor, "peakAnchor");
            Objects.requireNonNull(lowAnchor, "lowAnchor");
        }

        int top3MatchCount() {
            int hits = 0;
            if (peakAnchor.bestRank() > 0 && peakAnchor.bestRank() <= 3) {
                hits++;
            }
            if (lowAnchor.bestRank() > 0 && lowAnchor.bestRank() <= 3) {
                hits++;
            }
            return hits;
        }

        int rankScore() {
            return anchorRankScore(peakAnchor.bestRank()) + anchorRankScore(lowAnchor.bestRank());
        }

        private static int anchorRankScore(int rank) {
            if (rank == 1) {
                return 4;
            }
            if (rank == 2) {
                return 3;
            }
            if (rank == 3) {
                return 2;
            }
            if (rank > 3) {
                return 1;
            }
            return 0;
        }
    }

    record AnchorProbeObservation(String anchorId, String anchorTimeUtc, String expectedLabel, int bestRank,
            int legacyBestRank, String matchedScenarioId, String matchedScenarioType, int matchedScenarioStartIndex,
            double matchedConfidence, int processedSwingCount, int scenarioCount,
            Map<String, Integer> scenarioTypeCounts) {

        AnchorProbeObservation {
            Objects.requireNonNull(anchorId, "anchorId");
            Objects.requireNonNull(anchorTimeUtc, "anchorTimeUtc");
            Objects.requireNonNull(expectedLabel, "expectedLabel");
            Objects.requireNonNull(matchedScenarioId, "matchedScenarioId");
            Objects.requireNonNull(matchedScenarioType, "matchedScenarioType");
            scenarioTypeCounts = scenarioTypeCounts == null ? Map.of() : new TreeMap<>(scenarioTypeCounts);
        }
    }
}
