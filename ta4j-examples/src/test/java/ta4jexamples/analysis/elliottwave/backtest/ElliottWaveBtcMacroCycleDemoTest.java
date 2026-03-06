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
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottPhase;

class ElliottWaveBtcMacroCycleDemoTest {

    @Test
    void directionalCycleSummaryUsesExplicitBullishAndBearishLabels() {
        ElliottWaveAnchorCalibrationHarness.CycleSummary summary = new ElliottWaveAnchorCalibrationHarness.CycleSummary(
                "btc-2018->btc-2021->btc-2022", "btc-2018-cycle-bottom", "btc-2021-cycle-top", "btc-2022-cycle-bottom",
                "2018-12-16T00:00:00Z", "2021-11-11T00:00:00Z", "2022-11-22T00:00:00Z", "btc provenance", 10, 9, 12, 1,
                11, 1, 15, 14, 17, 2, 16, 1, true, true);

        ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary cycle = ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary
                .from("holdout", summary);

        assertEquals("Bullish 1-2-3-4-5", cycle.impulseLabel());
        assertEquals("Bullish WAVE5 top", cycle.peakLabel());
        assertEquals("Bearish A-B-C", cycle.correctionLabel());
        assertEquals("Bearish CORRECTIVE_C low", cycle.lowLabel());
        assertEquals("ordered top-3 cycle match", cycle.status());
    }

    @Test
    void startOffsetHypothesisFlagsExpandedSearchWhenLegacySubsetMisses() {
        ElliottWaveBtcMacroCycleDemo.MacroCycleProbe probe = new ElliottWaveBtcMacroCycleDemo.MacroCycleProbe("minute",
                observation("btc-2021-cycle-top", 1, 0, 4, Map.of()),
                observation("btc-2022-cycle-bottom", 0, 0, -1, Map.of(ScenarioTypeName.IMPULSE, 2)));

        ElliottWaveBtcMacroCycleDemo.HypothesisResult hypothesis = ElliottWaveBtcMacroCycleDemo
                .startOffsetHypothesis(probe);

        assertTrue(hypothesis.supported());
        assertEquals("1", hypothesis.evidence().get("peakBestRank"));
        assertEquals("0", hypothesis.evidence().get("peakLegacyBestRank"));
    }

    @Test
    void correctiveCoverageHypothesisFlagsMissingTriangleAndComplexFamilies() {
        ElliottWaveBtcMacroCycleDemo.AnchorProbeObservation lowAnchor = observation("btc-2022-cycle-bottom", 0, 0, -1,
                Map.of(ScenarioTypeName.IMPULSE, 3, ScenarioTypeName.CORRECTIVE_ZIGZAG, 2,
                        ScenarioTypeName.CORRECTIVE_FLAT, 1, ScenarioTypeName.CORRECTIVE_TRIANGLE, 0,
                        ScenarioTypeName.CORRECTIVE_COMPLEX, 0));

        ElliottWaveBtcMacroCycleDemo.HypothesisResult hypothesis = ElliottWaveBtcMacroCycleDemo
                .correctiveCoverageHypothesis(lowAnchor);

        assertTrue(hypothesis.supported());
        assertEquals("0", hypothesis.evidence().get("triangleCount"));
        assertEquals("0", hypothesis.evidence().get("complexCount"));
    }

    @Test
    void saveMacroCycleChartWritesImage() throws Exception {
        BarSeries series = syntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                java.util.List.of(
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-top",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series.getBar(1).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic top"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-bottom",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series.getBar(3).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic bottom")));
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
                } catch (Exception ignored) {
                    // Best effort cleanup for a temp test directory.
                }
            });
        }
    }

    @Test
    void coarserDegreeHypothesisRequiresAnActualRankImprovement() {
        ElliottWaveBtcMacroCycleDemo.MacroCycleProbe minute = new ElliottWaveBtcMacroCycleDemo.MacroCycleProbe("minute",
                observation("btc-2021-cycle-top", 0, 0, -1, Map.of()),
                observation("btc-2022-cycle-bottom", 0, 0, -1, Map.of()));
        ElliottWaveBtcMacroCycleDemo.MacroCycleProbe minor = new ElliottWaveBtcMacroCycleDemo.MacroCycleProbe("minor",
                observation("btc-2021-cycle-top", 2, 0, 4, Map.of()),
                observation("btc-2022-cycle-bottom", 0, 0, -1, Map.of()));

        ElliottWaveBtcMacroCycleDemo.HypothesisResult hypothesis = ElliottWaveBtcMacroCycleDemo
                .coarserDegreeHypothesis(minute, minor);

        assertTrue(hypothesis.supported());
        assertEquals("2", hypothesis.evidence().get("minorPeakRank"));
        assertFalse(hypothesis.summary().isBlank());
    }

    @Test
    void renderMacroCycleChartUsesLogAxisOnMainPricePlot() {
        BarSeries series = syntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                java.util.List.of(
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-bottom",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series.getBar(0).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic bottom"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-top",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series.getBar(1).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic top"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-low",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series.getBar(3).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic low")));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);

        assertTrue(chart.getPlot() instanceof CombinedDomainXYPlot);
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertFalse(combinedPlot.getSubplots().isEmpty());
        XYPlot mainPlot = (XYPlot) combinedPlot.getSubplots().getFirst();
        assertTrue(mainPlot.getRangeAxis() instanceof LogAxis);
        assertEquals("Price (USD, log)", mainPlot.getRangeAxis().getLabel());

        XYItemRenderer bullishRenderer = findRenderer(mainPlot, "Bullish 1-2-3-4-5");
        XYItemRenderer bearishRenderer = findRenderer(mainPlot, "Bearish A-B-C");

        assertNotNull(bullishRenderer, "Bullish cycle renderer should be present");
        assertNotNull(bearishRenderer, "Bearish cycle renderer should be present");
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BULLISH_LEG_COLOR, bullishRenderer.getSeriesPaint(0));
        assertPaintMatches(ElliottWaveBtcMacroCycleDemo.BEARISH_LEG_COLOR, bearishRenderer.getSeriesPaint(0));
    }

    @Test
    void renderMacroCycleChartDrawsLeadingBearishPreludeAndIntermediateBullCycle() {
        BarSeries series = extendedSyntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-demo-test", "synthetic.json", "synthetic provenance",
                java.util.List.of(
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-top-1",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series.getBar(0).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic top 1"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-bottom-1",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series.getBar(1).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic bottom 1"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-top-2",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.TOP, series.getBar(3).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic top 2"),
                        new ElliottWaveAnchorCalibrationHarness.Anchor("btc-bottom-2",
                                ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM, series.getBar(5).getEndTime(),
                                Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.CORRECTIVE_C),
                                ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "synthetic bottom 2")));

        JFreeChart chart = ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);
        XYPlot mainPlot = (XYPlot) ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().getFirst();

        XYDataset bullishDataset = findDataset(mainPlot, "Bullish 1-2-3-4-5");
        XYDataset bearishDataset = findDataset(mainPlot, "Bearish A-B-C");

        assertNotNull(bullishDataset, "Bullish dataset should be present");
        assertNotNull(bearishDataset, "Bearish dataset should be present");
        assertEquals(1, bullishDataset.getSeriesCount(), "One bottom-to-top span should render bullish");
        assertEquals(2, bearishDataset.getSeriesCount(), "Leading and trailing top-to-bottom spans should render");
    }

    private static ElliottWaveBtcMacroCycleDemo.AnchorProbeObservation observation(String anchorId, int bestRank,
            int legacyBestRank, int matchedScenarioStartIndex, Map<String, Integer> scenarioTypeCounts) {
        return new ElliottWaveBtcMacroCycleDemo.AnchorProbeObservation(anchorId, "2022-11-22T00:00:00Z",
                "Bearish CORRECTIVE_C low", bestRank, legacyBestRank, "", "", matchedScenarioStartIndex, Double.NaN, 8,
                5, scenarioTypeCounts);
    }

    private static BarSeries syntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("btc-demo-chart-series").build();
        Instant firstEndTime = Instant.parse("2020-01-01T00:00:00Z");
        double[][] values = { { 100.0, 105.0, 99.0, 104.0 }, { 104.0, 120.0, 103.0, 118.0 },
                { 118.0, 119.0, 90.0, 95.0 }, { 95.0, 98.0, 80.0, 82.0 } };
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
        double[][] values = { { 130.0, 135.0, 128.0, 132.0 }, { 132.0, 134.0, 110.0, 112.0 },
                { 112.0, 120.0, 111.0, 118.0 }, { 118.0, 150.0, 117.0, 147.0 }, { 147.0, 149.0, 140.0, 143.0 },
                { 143.0, 144.0, 100.0, 101.0 } };
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
            if (plot.getDataset(datasetIndex) == null || plot.getDataset(datasetIndex).getSeriesCount() == 0) {
                continue;
            }
            if (seriesKey.equals(plot.getDataset(datasetIndex).getSeriesKey(0).toString())) {
                return plot.getDataset(datasetIndex);
            }
        }
        return null;
    }

    private static void assertPaintMatches(Color expected, java.awt.Paint paint) {
        Color actual = assertInstanceOf(Color.class, paint);
        assertEquals(expected.getRed(), actual.getRed());
        assertEquals(expected.getGreen(), actual.getGreen());
        assertEquals(expected.getBlue(), actual.getBlue());
        assertTrue(actual.getAlpha() > 0, "Rendered overlay should remain visible");
    }

    private static final class ScenarioTypeName {
        private static final String IMPULSE = "IMPULSE";
        private static final String CORRECTIVE_ZIGZAG = "CORRECTIVE_ZIGZAG";
        private static final String CORRECTIVE_FLAT = "CORRECTIVE_FLAT";
        private static final String CORRECTIVE_TRIANGLE = "CORRECTIVE_TRIANGLE";
        private static final String CORRECTIVE_COMPLEX = "CORRECTIVE_COMPLEX";

        private ScenarioTypeName() {
        }
    }
}
