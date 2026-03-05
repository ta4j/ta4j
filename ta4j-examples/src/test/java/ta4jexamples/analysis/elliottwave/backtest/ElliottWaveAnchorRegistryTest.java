/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottPhase;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

class ElliottWaveAnchorRegistryTest {

    @Test
    void loadParsesDefaultRegistryResource() {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);

        assertEquals("btc-cycle-anchors-v1", registry.registryId());
        assertEquals(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, registry.datasetResource());
        assertFalse(registry.provenance().isBlank());
        assertEquals(12, registry.anchors().size());
        assertEquals("btc-2017-cycle-top", registry.anchors().getFirst().id());
        assertEquals("btc-2025-autumn-top", registry.anchors().getLast().id());
    }

    @Test
    void resolveBindsAnchorsToSeriesAndAssignsTrailingHoldoutPartition() {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveAnchorRegistryTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveAnchorRegistryTest.class));

        var resolved = registry.resolve(series, 3);

        assertEquals(registry.anchors().size(), resolved.size());
        assertEquals(3,
                resolved.stream()
                        .filter(anchor -> anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT)
                        .count());
        assertEquals(ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, resolved.getFirst().partition());
        assertEquals(ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, resolved.getLast().partition());
        for (ElliottWaveAnchorRegistry.ResolvedAnchor anchor : resolved) {
            assertTrue(anchor.decisionIndex() >= series.getBeginIndex());
            assertTrue(anchor.decisionIndex() <= series.getEndIndex());
            assertFalse(anchor.resolvedTime().isBefore(anchor.spec().windowStart()));
            assertFalse(anchor.resolvedTime().isAfter(anchor.spec().windowEnd()));
            assertTrue(Double.isFinite(anchor.resolvedPrice()));
        }
    }

    @Test
    void resolveHonorsZeroRequestedHoldoutAnchors() {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveAnchorRegistryTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveAnchorRegistryTest.class));

        var resolved = registry.resolve(series, 0);

        assertEquals(0,
                resolved.stream()
                        .filter(anchor -> anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT)
                        .count());
        assertTrue(resolved.stream()
                .allMatch(anchor -> anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION));
    }

    @Test
    void anchorSpecRejectsMissingExpectedPhasesAndBackwardsWindows() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-02T00:00:00Z");

        IllegalArgumentException emptyPhases = assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnchorRegistry.AnchorSpec("btc-top", "Top",
                        ElliottWaveAnchorRegistry.AnchorKind.TOP, start, end, Set.of(), "test source", ""));
        IllegalArgumentException backwardsWindow = assertThrows(IllegalArgumentException.class,
                () -> new ElliottWaveAnchorRegistry.AnchorSpec("btc-top", "Top",
                        ElliottWaveAnchorRegistry.AnchorKind.TOP, end, start, Set.of(ElliottPhase.WAVE5), "test source",
                        ""));

        assertEquals("expectedPhases must not be empty", emptyPhases.getMessage());
        assertEquals("windowEnd must not be before windowStart", backwardsWindow.getMessage());
    }
}
