/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.lppl.LPPLExhaustionSide;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class SectorLPPLExhaustionMapDemoTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 7, 10);

    @TempDir
    Path tempDirectory;

    @Test
    void universeHasTwelveGroupsAndExactlyThreeUniqueEtfLensesPerGroup() {
        List<SectorLPPLExhaustionMapDemo.CoverageGroup> groups = SectorLPPLExhaustionMapDemo.coverageGroups();
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> universe = SectorLPPLExhaustionMapDemo.universe();

        assertEquals(12, groups.size());
        assertEquals(36, universe.size());
        assertEquals(36,
                universe.stream().map(SectorLPPLExhaustionMapDemo.InstrumentDefinition::ticker).distinct().count());
        assertTrue(groups.stream().allMatch(group -> group.instruments().size() == 3));
        assertTrue(groups.stream()
                .allMatch(group -> group.instruments()
                        .stream()
                        .map(SectorLPPLExhaustionMapDemo.InstrumentDefinition::lens)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of(SectorLPPLExhaustionMapDemo.Lens.PRIMARY,
                                SectorLPPLExhaustionMapDemo.Lens.EQUAL_WEIGHT,
                                SectorLPPLExhaustionMapDemo.Lens.ALTERNATIVE))));
        assertEquals(Set.of("SMH", "XSD", "SOXX"),
                groups.getLast()
                        .instruments()
                        .stream()
                        .map(SectorLPPLExhaustionMapDemo.InstrumentDefinition::ticker)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aggregateReportsRawMedianNumericHorizonsAndLensConflict() {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions = definitions("Energy");
        List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> instruments = List.of(
                snapshot(definitions.get(0), -0.95, 20, unavailable()),
                snapshot(definitions.get(1), -0.40, 50, unavailable()),
                snapshot(definitions.get(2), 0.20, 90, unavailable()));

        SectorLPPLExhaustionMapDemo.GroupSnapshot group = SectorLPPLExhaustionMapDemo.aggregate(instruments).getFirst();

        assertEquals(-0.40, group.rawRegimeScore(), 0.000001);
        assertEquals(LPPLExhaustionSide.BUBBLE_EXHAUSTION, group.rawSide());
        assertEquals(0, group.lensSupportCount());
        assertEquals(3, group.lensCount());
        assertFalse(group.lensConflicted());
        assertEquals(27.5, group.criticalHorizonSessionsQ1(), 0.000001);
        assertEquals(35.0, group.criticalHorizonSessionsMedian(), 0.000001);
        assertEquals(42.5, group.criticalHorizonSessionsQ3(), 0.000001);
        assertEquals("NONE", group.benchmarkQualifiedSide());
    }

    @Test
    void rawSideUsesExplicitNumericThreshold() {
        assertEquals(LPPLExhaustionSide.BUBBLE_EXHAUSTION, SectorLPPLExhaustionMapDemo.rawSide(-0.10));
        assertEquals(LPPLExhaustionSide.NONE, SectorLPPLExhaustionMapDemo.rawSide(-0.0999));
        assertEquals(LPPLExhaustionSide.NONE, SectorLPPLExhaustionMapDemo.rawSide(0.0999));
        assertEquals(LPPLExhaustionSide.CRASH_EXHAUSTION, SectorLPPLExhaustionMapDemo.rawSide(0.10));
    }

    @Test
    void groupWithNoRawSideReportsMissingNumericHorizon() {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions = definitions("Utilities");
        SectorLPPLExhaustionMapDemo.GroupSnapshot group = SectorLPPLExhaustionMapDemo
                .aggregate(List.of(snapshot(definitions.get(0), 0.0, Double.NaN, unavailable()),
                        snapshot(definitions.get(1), 0.0, Double.NaN, unavailable()),
                        snapshot(definitions.get(2), 0.0, Double.NaN, unavailable())))
                .getFirst();

        assertEquals(LPPLExhaustionSide.NONE, group.rawSide());
        assertTrue(Double.isNaN(group.criticalHorizonSessionsQ1()));
        assertTrue(Double.isNaN(group.criticalHorizonSessionsMedian()));
        assertTrue(Double.isNaN(group.criticalHorizonSessionsQ3()));
    }

    @Test
    void instrumentScoreMatchesStructuralFormulaAndReportsQuartiles() throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = definition("XLC");
        SectorLPPLExhaustionMapDemo.InstrumentSnapshot snapshot = SectorLPPLExhaustionMapDemo
                .analyzeInstrument(definition, load(definition), smokeProfile());

        LPPLExhaustionSide dominantSide = snapshot.crashFits() > snapshot.bubbleFits()
                ? LPPLExhaustionSide.CRASH_EXHAUSTION
                : snapshot.bubbleFits() > snapshot.crashFits() ? LPPLExhaustionSide.BUBBLE_EXHAUSTION
                        : LPPLExhaustionSide.NONE;
        double expected = dominantSide.scoreSign() * snapshot.regimeConfidence() * snapshot.directionalConsensus()
                * snapshot.averageRSquared();
        assertEquals(expected, snapshot.rawRegimeScore(), 0.0000001);
        assertEquals(snapshot.actionableFits() / (double) smokeProfile().windows().length,
                snapshot.nearTermActionableFitShare(), 0.0000001);
        assertEquals(snapshot.criticalHorizonSessionsQ1(), snapshot.criticalHorizonSessionsMedian(), 0.0000001);
        assertEquals(snapshot.criticalHorizonSessionsMedian(), snapshot.criticalHorizonSessionsQ3(), 0.0000001);
    }

    @Test
    void committedResourcesAreAdjustedCompleteAndShareThePinnedSession() throws IOException {
        Set<LocalDate> lastDates = new HashSet<>();
        for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : SectorLPPLExhaustionMapDemo.universe()) {
            assertTrue(definition.resource().startsWith("ta4jexamples/analysis/lppl/sector-exhaustion-map/"));
            assertTrue(getClass().getClassLoader().getResource(definition.resource()) != null);
            List<SectorLPPLReferenceDataUpdater.ReferenceBar> bars = SectorLPPLReferenceDataUpdater
                    .readReferenceBars(tempDirectory.resolve("missing"), definition.resource());
            assertTrue(bars.size() >= 810);
            assertEquals(LocalDate.of(2019, 1, 2), bars.getFirst().localDate());
            lastDates.add(bars.getLast().localDate());
        }
        assertEquals(Set.of(SNAPSHOT_DATE), lastDates);
    }

    @Test
    void offlineRunWritesNumericArtifactsAndNoAmbiguousLabels() throws IOException {
        SectorLPPLExhaustionMapDemo.DemoOptions options = new SectorLPPLExhaustionMapDemo.DemoOptions(tempDirectory,
                false, false, false, false);
        SectorLPPLExhaustionMapDemo.DemoRun run = SectorLPPLExhaustionMapDemo.runDemo(smokeProfile(), options);

        assertEquals(12, run.groups().size());
        assertEquals(36, run.instruments().size());
        assertTrue(Files.exists(tempDirectory.resolve("lppl-exhaustion-map.txt")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-group-spectrum.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-instrument-regimes.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-fit-details.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-null-benchmarks.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-rolling-snapshots.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-benchmark-summary.txt")));
        assertEquals(13, Files.readAllLines(tempDirectory.resolve("lppl-group-spectrum.csv")).size());
        assertEquals(37, Files.readAllLines(tempDirectory.resolve("lppl-instrument-regimes.csv")).size());
        assertFalse(run.report().contains("IMMINENT"));
        assertFalse(run.report().contains("APPROACHING"));
        assertFalse(run.report().contains("DEVELOPING"));
        assertFalse(run.report().contains("STRONG_BUBBLE"));
        assertTrue(run.report().contains("horizon_sessions_q1/median/q3="));
    }

    @Test
    void offlineRunWorksFromPackagedClasspathOutsideRepository() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path output = tempDirectory.resolve("packaged-output");
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("surefire.test.class.path"),
                PackagedClasspathRunner.class.getName(), output.toString()).directory(tempDirectory.toFile())
                        .redirectErrorStream(true)
                        .start();

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "packaged-classpath run timed out");
        String processOutput = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.exitValue(), processOutput);
        assertTrue(Files.exists(output.resolve("lppl-exhaustion-map.txt")));
    }

    @Test
    void artifactRowsKeepStableColumnCounts() throws IOException {
        SectorLPPLExhaustionMapDemo.DemoRun run = SectorLPPLExhaustionMapDemo.runDemo(smokeProfile(),
                new SectorLPPLExhaustionMapDemo.DemoOptions(tempDirectory, false, false, false, false));

        assertStableColumns(SectorLPPLExhaustionMapDemo.renderGroupCsv(run.groups()), 32);
        assertStableColumns(SectorLPPLExhaustionMapDemo.renderInstrumentCsv(run.instruments()), 39);
        assertStableColumns(SectorLPPLExhaustionMapDemo.renderFitCsv(run.instruments()), 14);
    }

    @Test
    void semiconductorSnapshotIsReproducibleWithoutHardCodedConclusion() throws IOException {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> semiconductorDefinitions = definitions("Semiconductors");
        for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : semiconductorDefinitions) {
            BarSeries series = load(definition);
            SectorLPPLExhaustionMapDemo.InstrumentSnapshot first = SectorLPPLExhaustionMapDemo
                    .analyzeInstrument(definition, series, smokeProfile());
            SectorLPPLExhaustionMapDemo.InstrumentSnapshot second = SectorLPPLExhaustionMapDemo
                    .analyzeInstrument(definition, series, smokeProfile());
            assertEquals(SNAPSHOT_DATE, first.latestDate());
            assertTrue(Double.isFinite(first.rawRegimeScore()));
            assertEquals(first.rawRegimeScore(), second.rawRegimeScore(), 0.0000000001);
            assertEquals(first.rawSide(), second.rawSide());
            assertEquals("NONE", first.benchmarkQualifiedSide());
        }
    }

    @Test
    void commandLineKeepsNetworkResourceMutationAndBenchmarkExplicit() {
        SectorLPPLExhaustionMapDemo.DemoOptions defaults = SectorLPPLExhaustionMapDemo.DemoOptions.parse(new String[0]);
        SectorLPPLExhaustionMapDemo.DemoOptions refresh = SectorLPPLExhaustionMapDemo.DemoOptions
                .parse(new String[] { "--refresh", "--output-dir", tempDirectory.toString() });
        SectorLPPLExhaustionMapDemo.DemoOptions benchmark = SectorLPPLExhaustionMapDemo.DemoOptions
                .parse(new String[] { "--benchmark" });
        SectorLPPLExhaustionMapDemo.DemoOptions update = SectorLPPLExhaustionMapDemo.DemoOptions
                .parse(new String[] { "--update-resources", "--benchmark" });

        assertFalse(defaults.refresh());
        assertFalse(defaults.benchmark());
        assertTrue(refresh.refresh());
        assertFalse(refresh.benchmark());
        assertEquals(tempDirectory.toAbsolutePath().normalize(), refresh.outputDirectory());
        assertTrue(benchmark.benchmark());
        assertTrue(update.refresh());
        assertTrue(update.updateResources());
        assertTrue(update.benchmark());
        assertThrows(IllegalArgumentException.class,
                () -> SectorLPPLExhaustionMapDemo.DemoOptions.parse(new String[] { "--update-resources" }));
        assertThrows(IllegalArgumentException.class,
                () -> SectorLPPLExhaustionMapDemo.DemoOptions.parse(new String[] { "--unknown" }));
    }

    private List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions(String group) {
        return SectorLPPLExhaustionMapDemo.universe()
                .stream()
                .filter(definition -> definition.group().equals(group))
                .toList();
    }

    private SectorLPPLExhaustionMapDemo.InstrumentDefinition definition(String ticker) {
        return SectorLPPLExhaustionMapDemo.universe()
                .stream()
                .filter(definition -> definition.ticker().equals(ticker))
                .findFirst()
                .orElseThrow();
    }

    private SectorLPPLExhaustionMapDemo.InstrumentSnapshot snapshot(
            SectorLPPLExhaustionMapDemo.InstrumentDefinition definition, double score, double criticalOffset,
            SectorLPPLBenchmark.Metrics metrics) {
        LPPLExhaustionSide side = SectorLPPLExhaustionMapDemo.rawSide(score);
        return new SectorLPPLExhaustionMapDemo.InstrumentSnapshot(definition, SNAPSHOT_DATE, 1890, 0.0, 0.0, 0.0, 0.0,
                side, score, side == LPPLExhaustionSide.NONE ? 0 : 1, 0,
                side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1 : 0, Math.abs(score), 1.0, 0.9, criticalOffset - 5.0,
                criticalOffset, criticalOffset + 5.0, 0.0, List.of(), metrics);
    }

    private static SectorLPPLBenchmark.Metrics unavailable() {
        return SectorLPPLBenchmark.Metrics.unavailable();
    }

    private BarSeries load(SectorLPPLExhaustionMapDemo.InstrumentDefinition definition) throws IOException {
        return new JsonFileBarSeriesDataSource().loadSeries(definition.resource());
    }

    private static SectorLPPLExhaustionMapDemo.AnalysisProfile smokeProfile() {
        return new SectorLPPLExhaustionMapDemo.AnalysisProfile(new int[] { 125 }, 5, 10, 30, 40, 0.75);
    }

    private static void assertStableColumns(String csv, int expectedColumns) {
        csv.lines()
                .filter(line -> !line.isBlank())
                .forEach(line -> assertEquals(expectedColumns, line.split(",", -1).length, line));
    }

    public static final class PackagedClasspathRunner {

        private PackagedClasspathRunner() {
        }

        public static void main(String[] args) throws IOException {
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile = new SectorLPPLExhaustionMapDemo.AnalysisProfile(
                    new int[] { 125 }, 5, 10, 30, 1, 0.75);
            SectorLPPLExhaustionMapDemo.DemoOptions options = new SectorLPPLExhaustionMapDemo.DemoOptions(
                    Path.of(args[0]), false, false, false, false);
            SectorLPPLExhaustionMapDemo.runDemo(profile, options);
        }
    }
}
