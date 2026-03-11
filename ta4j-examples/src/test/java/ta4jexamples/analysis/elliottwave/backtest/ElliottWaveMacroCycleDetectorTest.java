/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

/**
 * Locks down anchor-free macro-cycle detection against the committed BTC truth
 * set.
 *
 * <p>
 * The detector is allowed to infer its own anchor ids and provenance, but the
 * recovered macro turns must stay aligned with the committed BTC anchor
 * windows, and the resulting full-history report must preserve the same macro
 * outlook and accepted cycle structure as the registry-backed run.
 *
 * @since 0.22.4
 */
class ElliottWaveMacroCycleDetectorTest {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMacroCycleDetectorTest.class);

    @TempDir
    Path chartDirectory;

    @Test
    void inferredBitcoinAnchorsRecoverCommittedMacroTurns() {
        final BarSeries series = loadBitcoinSeries();
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry expected = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry inferred = ElliottWaveMacroCycleDetector
                .inferAnchorRegistry(series);

        assertEquals(expected.anchors().size(), inferred.anchors().size());

        for (int index = 0; index < expected.anchors().size(); index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor expectedAnchor = expected.anchors().get(index);
            final ElliottWaveAnchorCalibrationHarness.Anchor inferredAnchor = inferred.anchors().get(index);
            assertEquals(expectedAnchor.type(), inferredAnchor.type());
            assertEquals(expectedAnchor.partition(), inferredAnchor.partition());
            assertEquals(expectedAnchor.expectedPhases(), inferredAnchor.expectedPhases());
            assertWithinDays(expectedAnchor.at(), inferredAnchor.at(), 21);
        }
    }

    @Test
    void anchorFreeHistoricalMacroDemoMatchesRegistryBackedBitcoinReport() throws Exception {
        final BarSeries series = loadBitcoinSeries();
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);

        final ElliottWaveBtcMacroCycleDemo.DemoReport registryBacked = ElliottWaveMacroCycleDemo
                .generateHistoricalReport(series, registry, chartDirectory.resolve("registry"));
        final ElliottWaveBtcMacroCycleDemo.DemoReport inferred = ElliottWaveMacroCycleDemo
                .generateHistoricalReport(series, chartDirectory.resolve("inferred"));

        assertEquals(registryBacked.baselineProfileId(), inferred.baselineProfileId());
        assertEquals(registryBacked.selectedProfileId(), inferred.selectedProfileId());
        assertEquals(registryBacked.selectedHypothesisId(), inferred.selectedHypothesisId());
        assertEquals(registryBacked.historicalFitPassed(), inferred.historicalFitPassed());
        assertEquals(registryBacked.profileScores(), inferred.profileScores());
        assertEquals(registryBacked.hypotheses(), inferred.hypotheses());
        assertEquals(cycleSignatures(registryBacked.cycles()), cycleSignatures(inferred.cycles()));
        assertTrue(Files.exists(Path.of(inferred.chartPath())));
        assertTrue(Files.exists(Path.of(inferred.summaryPath())));
    }

    private static BarSeries loadBitcoinSeries() {
        final BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveMacroCycleDetectorTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                LOG);
        assertNotNull(series);
        return series;
    }

    private static void assertWithinDays(final Instant expected, final Instant actual, final long maxDays) {
        final Duration delta = Duration.between(expected, actual).abs();
        assertTrue(delta.compareTo(Duration.ofDays(maxDays)) <= 0,
                () -> "expected " + actual + " to stay within " + maxDays + " days of " + expected);
    }

    private static List<String> cycleSignatures(
            final List<ElliottWaveBtcMacroCycleDemo.DirectionalCycleSummary> cycles) {
        return cycles.stream()
                .map(cycle -> String.join("|", cycle.partition(), cycle.impulseLabel(), cycle.peakLabel(),
                        cycle.correctionLabel(), cycle.lowLabel(), cycle.startTimeUtc(), cycle.peakTimeUtc(),
                        cycle.lowTimeUtc(), String.valueOf(cycle.bullishScore()), String.valueOf(cycle.bearishScore()),
                        String.valueOf(cycle.bullishAccepted()), String.valueOf(cycle.bearishAccepted()),
                        String.valueOf(cycle.accepted()), cycle.status()))
                .toList();
    }
}
