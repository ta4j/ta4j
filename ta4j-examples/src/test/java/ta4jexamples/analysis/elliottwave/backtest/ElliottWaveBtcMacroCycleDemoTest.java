/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
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
