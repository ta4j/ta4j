/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.num.DoubleNumFactory;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

class ElliottWaveAnchorRegistryTest {

    @Test
    void loadParsesDefaultRegistryResource() {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);

        assertEquals("btc-macro-cycle-anchors-v2", registry.registryId());
        assertEquals(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, registry.datasetResource());
        assertFalse(registry.provenance().isBlank());
        assertEquals(8, registry.anchors().size());
        assertEquals("btc-2011-cycle-top", registry.anchors().getFirst().id());
        assertEquals("btc-2013-cycle-top", registry.anchors().get(2).id());
        assertEquals("btc-2022-cycle-bottom", registry.anchors().getLast().id());
        assertTrue(registry.anchors()
                .stream()
                .filter(anchor -> anchor.kind() == ElliottWaveAnchorRegistry.AnchorKind.BOTTOM)
                .allMatch(anchor -> anchor.expectedPhases().equals(Set.of(ElliottPhase.CORRECTIVE_C))));
    }

    @Test
    void resolveBindsAnchorsToSeriesAndAssignsTrailingHoldoutPartition() {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveAnchorRegistryTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveAnchorRegistryTest.class));

        List<ElliottWaveAnchorRegistry.ResolvedAnchor> resolved = registry.resolve(series, 3);

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

        List<ElliottWaveAnchorRegistry.ResolvedAnchor> resolved = registry.resolve(series, 0);

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

    @Test
    void anchorSpecExposesExpectedPhasesAsUnmodifiableSet() {
        ElliottWaveAnchorRegistry.AnchorSpec anchor = new ElliottWaveAnchorRegistry.AnchorSpec("btc-top", "Top",
                ElliottWaveAnchorRegistry.AnchorKind.TOP, Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-03T00:00:00Z"), Set.of(ElliottPhase.WAVE5), "test source", "");

        assertThrows(UnsupportedOperationException.class, () -> anchor.expectedPhases().add(ElliottPhase.WAVE3));
    }

    @Test
    void resolveSkipsInvalidWindowPricesWhenLaterBarsRemainUsable() {
        BarSeries series = syntheticSeriesWithInvalidLeadingWindowPrice();
        ElliottWaveAnchorRegistry.AnchorSpec anchor = new ElliottWaveAnchorRegistry.AnchorSpec("btc-top", "Top",
                ElliottWaveAnchorRegistry.AnchorKind.TOP, Instant.parse("2024-01-02T00:00:00Z"),
                Instant.parse("2024-01-03T00:00:00Z"), Set.of(ElliottPhase.WAVE5), "test source", "");
        ElliottWaveAnchorRegistry registry = registry(anchor);

        List<ElliottWaveAnchorRegistry.ResolvedAnchor> resolved = registry.resolve(series, 0);

        assertEquals(1, resolved.size());
        assertEquals(1, resolved.getFirst().decisionIndex());
        assertEquals(110.0, resolved.getFirst().resolvedPrice());
        assertEquals(Instant.parse("2024-01-03T00:00:00Z"), resolved.getFirst().resolvedTime());
    }

    private static BarSeries syntheticSeriesWithInvalidLeadingWindowPrice() {
        BarSeries series = new BaseBarSeriesBuilder().withName("anchor-registry-test")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        Duration period = Duration.ofDays(1);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        series.barBuilder()
                .timePeriod(period)
                .endTime(start.plus(period))
                .openPrice(100)
                .highPrice(Double.NaN)
                .lowPrice(95)
                .closePrice(Double.NaN)
                .volume(1000)
                .add();
        series.barBuilder()
                .timePeriod(period)
                .endTime(start.plus(period.multipliedBy(2)))
                .openPrice(100)
                .highPrice(110)
                .lowPrice(96)
                .closePrice(108)
                .volume(1000)
                .add();
        series.barBuilder()
                .timePeriod(period)
                .endTime(start.plus(period.multipliedBy(3)))
                .openPrice(108)
                .highPrice(105)
                .lowPrice(94)
                .closePrice(100)
                .volume(1000)
                .add();
        return series;
    }

    private static ElliottWaveAnchorRegistry registry(ElliottWaveAnchorRegistry.AnchorSpec anchor) {
        try {
            Constructor<ElliottWaveAnchorRegistry> constructor = ElliottWaveAnchorRegistry.class
                    .getDeclaredConstructor(String.class, String.class, String.class, List.class);
            constructor.setAccessible(true);
            return constructor.newInstance("synthetic-registry", "synthetic-dataset", "synthetic-provenance",
                    List.of(anchor));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to instantiate synthetic registry", ex);
        }
    }
}
