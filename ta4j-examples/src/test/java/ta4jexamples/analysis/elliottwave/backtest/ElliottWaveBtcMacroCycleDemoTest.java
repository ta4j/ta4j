/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;

class ElliottWaveBtcMacroCycleDemoTest {

    @Test
    void buildWaveLabelsFromScenarioUsesCurrentPhaseInsteadOfScenarioType() {
        BarSeries series = extendedSyntheticSeries();
        ElliottScenario bullishScenario = ElliottScenario.builder()
                .id("btc-bullish")
                .currentPhase(ElliottPhase.WAVE5)
                .swings(List.of(
                        new ElliottSwing(0, 1, series.numFactory().numOf(100), series.numFactory().numOf(120),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(1, 2, series.numFactory().numOf(120), series.numFactory().numOf(108),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(2, 3, series.numFactory().numOf(108), series.numFactory().numOf(146),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(3, 4, series.numFactory().numOf(146), series.numFactory().numOf(130),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(4, 5, series.numFactory().numOf(130), series.numFactory().numOf(156),
                                ElliottDegree.MINUTE)))
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.CORRECTIVE_ZIGZAG)
                .startIndex(0)
                .build();
        ElliottScenario correctiveScenario = ElliottScenario.builder()
                .id("btc-corrective")
                .currentPhase(ElliottPhase.CORRECTIVE_C)
                .swings(List.of(
                        new ElliottSwing(0, 1, series.numFactory().numOf(160), series.numFactory().numOf(130),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(1, 2, series.numFactory().numOf(130), series.numFactory().numOf(145),
                                ElliottDegree.MINUTE),
                        new ElliottSwing(2, 3, series.numFactory().numOf(145), series.numFactory().numOf(118),
                                ElliottDegree.MINUTE)))
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINUTE)
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();

        List<BarLabel> bullishLabels = ElliottWaveBtcMacroCycleDemo.buildWaveLabelsFromScenario(series, bullishScenario,
                ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR);
        List<BarLabel> correctiveLabels = ElliottWaveBtcMacroCycleDemo.buildWaveLabelsFromScenario(series,
                correctiveScenario, ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR);

        assertEquals(List.of("1", "2", "3", "4", "5"), bullishLabels.stream().map(BarLabel::text).toList());
        assertEquals(List.of("A", "B", "C"), correctiveLabels.stream().map(BarLabel::text).toList());
    }

    @Test
    void evaluateMacroStudyProducesProfileTableCycleSummaryAndCurrentCycleFallback() {
        BarSeries series = studySyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 1),
                        anchor("btc-bottom-2011", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 2),
                        anchor("btc-top-2013", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 5),
                        anchor("btc-bottom-2015", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 7)));

        ElliottWaveBtcMacroCycleDemo.MacroStudy study = ElliottWaveBtcMacroCycleDemo.evaluateMacroStudy(series,
                registry);

        assertEquals(5, study.profileScores().size());
        assertEquals(1, study.cycles().size());
        assertEquals("Bullish 1-2-3-4-5", study.cycles().getFirst().impulseLabel());
        assertEquals("Bearish A-B-C", study.cycles().getFirst().correctionLabel());
        assertEquals(4, study.hypotheses().size());
        assertFalse(study.selectedProfile().profile().id().isBlank());
        assertEquals(series.getBar(7).getEndTime().toString(), study.currentCycle().startTimeUtc());
        assertEquals(study.selectedProfile().profile().id(), study.currentCycle().winningProfileId());
        assertNotNull(study.currentPrimaryFit());
        assertFalse(study.currentPrimaryFit().scenario().swings().isEmpty());
    }

    @Test
    void saveMacroCycleChartWritesImage() throws Exception {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));
        Path tempDir = Files.createTempDirectory("btc-macro-cycle-demo");

        try {
            Optional<Path> savedPath = ElliottWaveBtcMacroCycleDemo.saveMacroCycleChart(series, registry, tempDir);

            assertTrue(savedPath.isPresent());
            assertTrue(Files.exists(savedPath.get()));
            assertTrue(savedPath.get().getFileName().toString().endsWith(".jpg"));
            BufferedImage image = readImage(savedPath.get());
            assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_WIDTH, image.getWidth());
            assertEquals(ElliottWaveBtcMacroCycleDemo.DEFAULT_CHART_HEIGHT, image.getHeight());
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void realDatasetHistoricalCyclesProduceAcceptedFitsAndPersistArtifacts() throws Exception {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveBtcMacroCycleDemo.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveBtcMacroCycleDemoTest.class));
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        Path tempDir = Files.createTempDirectory("btc-macro-cycle-real");

        try {
            ElliottWaveBtcMacroCycleDemo.MacroStudy study = ElliottWaveBtcMacroCycleDemo.evaluateMacroStudy(series,
                    registry);
            ElliottWaveBtcMacroCycleDemo.DemoReport report = ElliottWaveBtcMacroCycleDemo.generateReport(tempDir);

            assertTrue(study.selectedProfile().historicalFitPassed());
            assertEquals(3, study.cycles().size());
            assertTrue(
                    study.cycles().stream().allMatch(ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary::accepted));
            assertFalse(study.currentCycle().currentWave().isBlank());
            assertTrue(Files.exists(Path.of(report.chartPath())));
            assertTrue(Files.exists(Path.of(report.summaryPath())));
            assertEquals(report.chartPath(), report.currentCycle().chartPath());
        } finally {
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void renderMacroCycleChartUsesLogAxisOnMainPricePlot() {
        BarSeries series = chartSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 0),
                        anchor("btc-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 4),
                        anchor("btc-low", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 6)));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);

        assertTrue(chart.getPlot() instanceof CombinedDomainXYPlot);
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertFalse(combinedPlot.getSubplots().isEmpty());
        XYPlot mainPlot = (XYPlot) combinedPlot.getSubplots().getFirst();
        assertTrue(mainPlot.getRangeAxis() instanceof LogAxis);
        assertEquals("Price (USD, log)", mainPlot.getRangeAxis().getLabel());

        XYItemRenderer bullishRenderer = findRenderer(mainPlot, "Bullish 1-2-3-4-5");
        XYItemRenderer bearishRenderer = findRenderer(mainPlot, "Bearish A-B-C");
        XYItemRenderer currentRenderer = findRenderer(mainPlot, "Current-cycle wave-count segments");

        assertNotNull(bullishRenderer);
        assertNotNull(bearishRenderer);
        assertNotNull(currentRenderer);
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR, bullishRenderer.getSeriesPaint(0));
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR, bearishRenderer.getSeriesPaint(0));
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BULLISH_WAVE_COLOR, currentRenderer.getSeriesPaint(0));
    }

    @Test
    void renderMacroCycleChartDrawsLeadingBearishPreludeAndIntermediateBullCycle() {
        BarSeries series = extendedSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                List.of(anchor("btc-top-1", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 0),
                        anchor("btc-bottom-1", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 1),
                        anchor("btc-top-2", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series, 3),
                        anchor("btc-bottom-2", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series, 5)));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);
        XYPlot mainPlot = (XYPlot) ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().getFirst();

        XYDataset bullishDataset = findDataset(mainPlot, "Bullish 1-2-3-4-5");
        XYDataset bearishDataset = findDataset(mainPlot, "Bearish A-B-C");

        assertNotNull(bullishDataset);
        assertNotNull(bearishDataset);
        assertEquals(1, bullishDataset.getSeriesCount());
        assertEquals(2, bearishDataset.getSeriesCount());
    }

    @Test
    void interpolateOverlayPriceUsesLogSpaceOnPositivePrices() {
        double midpoint = ElliottWaveBtcMacroCycleDemo.interpolateOverlayPrice(100.0, 1600.0, 0.5);

        assertEquals(400.0, midpoint, 1.0e-10);
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor anchor(String id,
            ElliottWaveAnchorCalibrationHarness.AnchorType type, BarSeries series, int index) {
        return new ElliottWaveAnchorCalibrationHarness.Anchor(id, type, series.getBar(index).getEndTime(),
                Duration.ZERO, Duration.ZERO,
                type == ElliottWaveAnchorCalibrationHarness.AnchorType.TOP ? Set.of(ElliottPhase.WAVE5)
                        : Set.of(ElliottPhase.CORRECTIVE_C),
                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic");
    }

    private static BarSeries chartSyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-chart-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 100.0, 102.0, 96.0, 101.0 }, { 101.0, 126.0, 100.0, 124.0 },
                { 124.0, 125.0, 112.0, 114.0 }, { 114.0, 150.0, 113.0, 148.0 }, { 148.0, 170.0, 147.0, 168.0 },
                { 168.0, 169.0, 132.0, 136.0 }, { 136.0, 138.0, 108.0, 110.0 }, { 110.0, 128.0, 109.0, 126.0 },
                { 126.0, 144.0, 124.0, 142.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BarSeries studySyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-study-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 140.0, 142.0, 132.0, 136.0 }, { 136.0, 170.0, 134.0, 166.0 },
                { 166.0, 168.0, 118.0, 122.0 }, { 122.0, 136.0, 120.0, 132.0 }, { 132.0, 168.0, 130.0, 164.0 },
                { 164.0, 205.0, 162.0, 202.0 }, { 202.0, 204.0, 156.0, 160.0 }, { 160.0, 162.0, 112.0, 116.0 },
                { 116.0, 144.0, 114.0, 142.0 }, { 142.0, 174.0, 140.0, 171.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BarSeries extendedSyntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-extended-chart-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 130.0, 136.0, 128.0, 134.0 }, { 134.0, 135.0, 112.0, 114.0 },
                { 114.0, 125.0, 113.0, 122.0 }, { 122.0, 152.0, 121.0, 150.0 }, { 150.0, 151.0, 138.0, 140.0 },
                { 140.0, 141.0, 100.0, 102.0 }, { 102.0, 122.0, 101.0, 118.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEndTime.plus(Duration.ofDays(index)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }

    private static BufferedImage readImage(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unable to decode image " + path);
        }
        return image;
    }

    private static XYItemRenderer findRenderer(XYPlot plot, String seriesKey) {
        XYDataset dataset = findDataset(plot, seriesKey);
        if (dataset == null) {
            return null;
        }
        for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
            if (plot.getDataset(datasetIndex) == dataset) {
                return plot.getRenderer(datasetIndex);
            }
        }
        return null;
    }

    private static XYDataset findDataset(XYPlot plot, String seriesKey) {
        for (int datasetIndex = 0; datasetIndex < plot.getDatasetCount(); datasetIndex++) {
            XYDataset dataset = plot.getDataset(datasetIndex);
            if (dataset == null || dataset.getSeriesCount() == 0) {
                continue;
            }
            if (seriesKey.equals(dataset.getSeriesKey(0).toString())) {
                return dataset;
            }
        }
        return null;
    }

    private static void assertPaintMatches(Color expected, java.awt.Paint paint) {
        Color actual = assertInstanceOf(Color.class, paint);
        assertEquals(expected.getRed(), actual.getRed());
        assertEquals(expected.getGreen(), actual.getGreen());
        assertEquals(expected.getBlue(), actual.getBlue());
        assertTrue(actual.getAlpha() > 0);
    }
}
