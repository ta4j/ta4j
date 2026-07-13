/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.lppl.LPPLExhaustionSide;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class SectorLPPLBenchmarkTest {

    @TempDir
    Path tempDirectory;

    @Test
    void trendControlIsExactlyLogLinearAndPreservesEndpoint() throws IOException {
        BarSeries source = load("SMH");
        BarSeries trend = SectorLPPLBenchmark.trendSeries(source);
        double[] returns = SectorLPPLBenchmark.logReturns(trend);

        assertEquals(source.getBarCount(), trend.getBarCount());
        assertEquals(source.getFirstBar().getClosePrice().doubleValue(),
                trend.getFirstBar().getClosePrice().doubleValue(), 0.000000001);
        assertEquals(source.getLastBar().getClosePrice().doubleValue(),
                trend.getLastBar().getClosePrice().doubleValue(), 0.000000001);
        for (double value : returns) {
            assertEquals(returns[0], value, 0.000000000001);
        }
    }

    @Test
    void randomizedReturnControlIsDeterministicAndPreservesReturnsAndEndpoint() throws IOException {
        BarSeries source = load("XSD");
        int[] first = SectorLPPLBenchmark.permutation(source.getBarCount() - 1, 20260710L);
        int[] second = SectorLPPLBenchmark.permutation(source.getBarCount() - 1, 20260710L);
        BarSeries randomized = SectorLPPLBenchmark.randomizedReturnSeries(source, first);
        double[] expectedReturns = SectorLPPLBenchmark.logReturns(source);
        double[] actualReturns = SectorLPPLBenchmark.logReturns(randomized);
        Arrays.sort(expectedReturns);
        Arrays.sort(actualReturns);

        assertArrayEquals(first, second);
        assertArrayEquals(expectedReturns, actualReturns, 0.000000000001);
        assertEquals(source.getLastBar().getClosePrice().doubleValue(),
                randomized.getLastBar().getClosePrice().doubleValue(), 0.00000001);
        assertEquals(source.getLastBar().getEndTime(), randomized.getLastBar().getEndTime());
    }

    @Test
    void empiricalPValueUsesTwoSidedPlusOneCorrection() {
        assertEquals(0.6, SectorLPPLBenchmark.empiricalPValue(-0.8, new double[] { -0.9, 0.2, 0.8, -0.1 }), 0.0000001);
        assertEquals(0.2, SectorLPPLBenchmark.empiricalPValue(1.0, new double[] { -0.9, 0.2, 0.8, -0.1 }), 0.0000001);
    }

    @Test
    void summaryDoesNotClaimBinomialConfidenceForSynchronizedNullPaths() {
        SectorLPPLBenchmark.Metadata metadata = new SectorLPPLBenchmark.Metadata(1, "2026-07-10", "data", "profile",
                20260710L, 199, 21, 750, 0.10, 0.05);
        Map<String, SectorLPPLBenchmark.Metrics> instruments = new LinkedHashMap<>();
        instruments.put("FIRST", metricsWithRates(0.50, 0.05));
        instruments.put("SYNCHRONIZED", metricsWithRates(0.50, 0.05));
        Map<String, SectorLPPLBenchmark.Metrics> groups = Map.of("GROUP", metricsWithRates(0.50, 0.05));
        SectorLPPLBenchmark.Result result = new SectorLPPLBenchmark.Result(true, metadata, instruments, groups,
                List.of(), List.of(), SectorLPPLBenchmark.Manifest.from(metadata, instruments, groups));

        String summary = result.renderSummary();

        assertFalse(summary.contains("95%_wilson"),
                "synchronized and leave-one-out-ranked null rows are not independent binomial trials");
        assertTrue(summary.contains("instrument_gated_false_positive_rate="));
        assertTrue(summary.contains("group_gated_false_positive_rate="));
    }

    @Test
    void rollingCadenceIncludesWarmupAndCurrentSession() {
        assertEquals(List.of(), SectorLPPLBenchmark.rollingEndIndices(749, 750, 21));
        assertEquals(List.of(749), SectorLPPLBenchmark.rollingEndIndices(750, 750, 21));
        assertEquals(List.of(749, 770, 779), SectorLPPLBenchmark.rollingEndIndices(780, 750, 21));
    }

    @Test
    void groupQualificationRequiresTwoSupportingLensesAndRejectsOppositeEvidence() {
        SectorLPPLBenchmark.Metrics group = qualified(LPPLExhaustionSide.BUBBLE_EXHAUSTION);
        SectorLPPLBenchmark.Metrics bubble = qualified(LPPLExhaustionSide.BUBBLE_EXHAUSTION);
        SectorLPPLBenchmark.Metrics crash = qualified(LPPLExhaustionSide.CRASH_EXHAUSTION);

        assertTrue(SectorLPPLBenchmark.groupQualified(group, LPPLExhaustionSide.BUBBLE_EXHAUSTION,
                List.of(bubble, bubble, SectorLPPLBenchmark.Metrics.unavailable())));
        assertFalse(SectorLPPLBenchmark.groupQualified(group, LPPLExhaustionSide.BUBBLE_EXHAUSTION,
                List.of(bubble, SectorLPPLBenchmark.Metrics.unavailable(), SectorLPPLBenchmark.Metrics.unavailable())));
        assertFalse(SectorLPPLBenchmark.groupQualified(group, LPPLExhaustionSide.BUBBLE_EXHAUSTION,
                List.of(bubble, bubble, crash)));
    }

    @Test
    void manifestFingerprintRejectsDifferentDataOrProfile() {
        List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded = loadUniverse();
        SectorLPPLExhaustionMapDemo.AnalysisProfile profile = SectorLPPLExhaustionMapDemo.AnalysisProfile.production();
        SectorLPPLBenchmark.Metadata metadata = new SectorLPPLBenchmark.Metadata(1, "2026-07-10",
                SectorLPPLBenchmark.dataDigest(loaded), SectorLPPLBenchmark.profileFingerprint(profile), 20260710L, 199,
                21, 750, 0.10, 0.05);
        Map<String, SectorLPPLBenchmark.Metrics> instruments = new LinkedHashMap<>();
        SectorLPPLExhaustionMapDemo.universe()
                .forEach(definition -> instruments.put(definition.ticker(), qualified(LPPLExhaustionSide.NONE)));
        Map<String, SectorLPPLBenchmark.Metrics> groups = new LinkedHashMap<>();
        SectorLPPLExhaustionMapDemo.coverageGroups()
                .forEach(group -> groups.put(group.name(), qualified(LPPLExhaustionSide.NONE)));
        SectorLPPLBenchmark.Manifest manifest = SectorLPPLBenchmark.Manifest.from(metadata, instruments, groups);

        assertTrue(SectorLPPLBenchmark.matches(manifest, loaded, profile));
        SectorLPPLExhaustionMapDemo.AnalysisProfile changed = new SectorLPPLExhaustionMapDemo.AnalysisProfile(
                new int[] { 125 }, 5, 10, 30, 240, 0.75);
        assertFalse(SectorLPPLBenchmark.matches(manifest, loaded, changed));
        SectorLPPLBenchmark.Metadata wrongData = new SectorLPPLBenchmark.Metadata(1, "2026-07-10", "wrong",
                metadata.profileFingerprint(), 20260710L, 199, 21, 750, 0.10, 0.05);
        assertFalse(SectorLPPLBenchmark.matches(SectorLPPLBenchmark.Manifest.from(wrongData, instruments, groups),
                loaded, profile));
        SectorLPPLBenchmark.Metadata incomplete = new SectorLPPLBenchmark.Metadata(1, "2026-07-10", null,
                metadata.profileFingerprint(), 20260710L, 199, 21, 750, 0.10, 0.05);
        assertFalse(SectorLPPLBenchmark.matches(SectorLPPLBenchmark.Manifest.from(incomplete, instruments, groups),
                loaded, profile));
    }

    @Test
    void committedManifestLoadsForProductionResources() throws IOException {
        Optional<SectorLPPLBenchmark.Result> result = SectorLPPLBenchmark.loadCommitted(loadUniverse(),
                SectorLPPLExhaustionMapDemo.AnalysisProfile.production());

        assertTrue(result.isPresent());
        assertEquals(36, result.orElseThrow().instrumentMetrics().size());
        assertEquals(12, result.orElseThrow().groupMetrics().size());
        assertTrue(result.orElseThrow()
                .groupMetrics()
                .values()
                .stream()
                .noneMatch(SectorLPPLBenchmark.Metrics::qualified));
    }

    @Test
    void smallBenchmarkWritesDeterministicPrimaryArtifacts() throws IOException {
        List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded = loadUniverse();
        SectorLPPLExhaustionMapDemo.AnalysisProfile profile = new SectorLPPLExhaustionMapDemo.AnalysisProfile(
                new int[] { 125 }, 5, 10, 30, 20, 0.70);
        List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed = loaded.stream()
                .map(instrument -> SectorLPPLExhaustionMapDemo.analyzeInstrument(instrument.definition(),
                        instrument.series(), profile))
                .toList();
        SectorLPPLBenchmark.BenchmarkConfig config = new SectorLPPLBenchmark.BenchmarkConfig(20260710L, 5, 500, 750,
                0.10, 0.20);

        SectorLPPLBenchmark.Result result = SectorLPPLBenchmark.run(loaded, observed, profile, tempDirectory, config);
        result.writeArtifacts(tempDirectory);

        assertTrue(result.available());
        assertEquals(36, result.instrumentMetrics().size());
        assertEquals(12, result.groupMetrics().size());
        assertEquals((36 + 12) * 5, result.nullScores().size());
        assertTrue(Files.size(tempDirectory.resolve("lppl-benchmark-progress.csv")) > 0);
        assertTrue(Files.size(tempDirectory.resolve("lppl-null-benchmarks.csv")) > 0);
        assertTrue(Files.size(tempDirectory.resolve("lppl-rolling-snapshots.csv")) > 0);
        assertTrue(Files.readString(tempDirectory.resolve("lppl-benchmark-summary.txt"))
                .contains("group_raw_null_signal_rate="));
        assertTrue(Files.readString(tempDirectory.resolve("lppl-benchmark-summary.txt"))
                .contains("group_gated_false_positive_rate="));
        assertFalse(result.instrumentMetrics().values().stream().anyMatch(metrics -> !metrics.available()));
        assertTrue(
                result.instrumentMetrics().values().stream().allMatch(metrics -> metrics.rollingSnapshotCount() == 4));
    }

    @Test
    @Tag("analysis-demo")
    void productionBenchmarkUsesTheLockedFalsePositiveContract() throws IOException {
        List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded = loadUniverse();
        SectorLPPLExhaustionMapDemo.AnalysisProfile profile = SectorLPPLExhaustionMapDemo.AnalysisProfile.production();
        List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed = loaded.stream()
                .map(instrument -> SectorLPPLExhaustionMapDemo.analyzeInstrument(instrument.definition(),
                        instrument.series(), profile))
                .toList();

        SectorLPPLBenchmark.Result result = SectorLPPLBenchmark.run(loaded, observed, profile, tempDirectory);

        assertEquals(199, result.metadata().permutations());
        assertEquals(20260710L, result.metadata().seed());
        assertEquals(21, result.metadata().rollingStep());
        assertEquals(750, result.metadata().rollingWarmup());
        assertTrue(result.groupMetrics()
                .values()
                .stream()
                .allMatch(metrics -> metrics.estimatedGatedFalsePositiveRate() <= 0.05));
    }

    private List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loadUniverse() {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        return SectorLPPLExhaustionMapDemo.universe()
                .stream()
                .map(definition -> new SectorLPPLExhaustionMapDemo.LoadedInstrument(definition,
                        dataSource.loadSeries(definition.resource())))
                .toList();
    }

    private BarSeries load(String ticker) throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = SectorLPPLExhaustionMapDemo.universe()
                .stream()
                .filter(candidate -> candidate.ticker().equals(ticker))
                .findFirst()
                .orElseThrow();
        return new JsonFileBarSeriesDataSource().loadSeries(definition.resource());
    }

    private static SectorLPPLBenchmark.Metrics qualified(LPPLExhaustionSide side) {
        return new SectorLPPLBenchmark.Metrics(true, true, side, 0.01, 0.20, 0.03, -0.20, true, 10, 0.40, 0.30, 0.90, 2,
                42);
    }

    private static SectorLPPLBenchmark.Metrics metricsWithRates(double nullSignalRate, double falsePositiveRate) {
        return new SectorLPPLBenchmark.Metrics(true, false, LPPLExhaustionSide.NONE, 0.50, nullSignalRate,
                falsePositiveRate, 0.20, false, 10, 0.40, 0.30, 0.90, 2, 42);
    }
}
