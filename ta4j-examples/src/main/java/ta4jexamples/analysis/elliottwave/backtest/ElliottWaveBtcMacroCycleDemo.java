/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveMacroCycleDemo.CurrentCycleSummary;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveMacroCycleDemo.DirectionalCycleSummary;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveMacroCycleDemo.HypothesisResult;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveMacroCycleDemo.MacroStudy;
import ta4jexamples.analysis.elliottwave.backtest.ElliottWaveMacroCycleDemo.ProfileScoreSummary;
import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

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
}
