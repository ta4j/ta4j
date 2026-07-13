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
    void aggregateUsesMedianAndReportsConflictingLensDirections() {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions = definitions("Energy");
        List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> instruments = List.of(
                snapshot(definitions.get(0), -0.95, LPPLExhaustionSide.BUBBLE_EXHAUSTION, 20),
                snapshot(definitions.get(1), -0.40, LPPLExhaustionSide.BUBBLE_EXHAUSTION, 50),
                snapshot(definitions.get(2), 0.20, LPPLExhaustionSide.CRASH_EXHAUSTION, 90));

        SectorLPPLExhaustionMapDemo.GroupSnapshot group = SectorLPPLExhaustionMapDemo.aggregate(instruments).getFirst();

        assertEquals(-0.40, group.spectrumScore(), 0.000001);
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.BUBBLE, group.band());
        assertEquals(SectorLPPLExhaustionMapDemo.LensAgreement.CONFLICTED, group.agreement());
        assertEquals(SectorLPPLExhaustionMapDemo.Proximity.APPROACHING, group.proximity());
        assertEquals(35.0, group.medianCriticalOffset(), 0.000001);
    }

    @Test
    void agreementDistinguishesThreeTwoOneAndNoSignal() {
        assertEquals("3/3", aggregateAgreement("Technology", -0.3, -0.2, -0.1).toString());
        assertEquals("2/3", aggregateAgreement("Technology", -0.3, -0.2, 0.0).toString());
        assertEquals("1/3", aggregateAgreement("Technology", -0.3, 0.0, 0.0).toString());
        assertEquals("NO_SIGNAL", aggregateAgreement("Technology", 0.0, 0.0, 0.0).toString());
    }

    @Test
    void spectrumBandsHonorDocumentedBoundaries() {
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.STRONG_BUBBLE, aggregateBand(-0.60));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.BUBBLE, aggregateBand(-0.25));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.WEAK_BUBBLE, aggregateBand(-0.10));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.NEUTRAL_OR_MIXED, aggregateBand(0.0));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.WEAK_CRASH, aggregateBand(0.10));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.CRASH, aggregateBand(0.25));
        assertEquals(SectorLPPLExhaustionMapDemo.SpectrumBand.STRONG_CRASH, aggregateBand(0.60));
    }

    @Test
    void instrumentScoreMatchesStructuralFormula() throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = definition("XLC");
        BarSeries series = load(definition);
        SectorLPPLExhaustionMapDemo.InstrumentSnapshot snapshot = SectorLPPLExhaustionMapDemo
                .analyzeInstrument(definition, series, smokeProfile());

        double expected = snapshot.side().scoreSign() * snapshot.regimeConfidence() * snapshot.directionalConsensus()
                * snapshot.averageRSquared();
        assertEquals(expected, snapshot.regimeScore(), 0.0000001);
        assertEquals(snapshot.actionableFits() / (double) smokeProfile().windows().length, snapshot.nearTermFitShare(),
                0.0000001);
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
    void offlineRunWritesStableAbsoluteSpectrumArtifacts() throws IOException {
        SectorLPPLExhaustionMapDemo.DemoOptions options = new SectorLPPLExhaustionMapDemo.DemoOptions(tempDirectory,
                false, false, false);
        SectorLPPLExhaustionMapDemo.DemoRun run = SectorLPPLExhaustionMapDemo.runDemo(smokeProfile(), options);

        assertEquals(12, run.groups().size());
        assertEquals(36, run.instruments().size());
        assertTrue(Files.exists(tempDirectory.resolve("lppl-exhaustion-map.txt")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-group-spectrum.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-instrument-regimes.csv")));
        assertTrue(Files.exists(tempDirectory.resolve("lppl-fit-details.csv")));
        assertEquals(13, Files.readAllLines(tempDirectory.resolve("lppl-group-spectrum.csv")).size());
        assertEquals(37, Files.readAllLines(tempDirectory.resolve("lppl-instrument-regimes.csv")).size());
        assertFalse(run.report().contains("relative_rank"));
    }

    @Test
    void artifactRowsKeepStableColumnCounts() throws IOException {
        SectorLPPLExhaustionMapDemo.DemoRun run = SectorLPPLExhaustionMapDemo.runDemo(smokeProfile(),
                new SectorLPPLExhaustionMapDemo.DemoOptions(tempDirectory, false, false, false));

        assertStableColumns(SectorLPPLExhaustionMapDemo.renderGroupCsv(run.groups()), 15);
        assertStableColumns(SectorLPPLExhaustionMapDemo.renderInstrumentCsv(run.instruments()), 25);
        assertStableColumns(SectorLPPLExhaustionMapDemo.renderFitCsv(run.instruments()), 14);
    }

    @Test
    void semiconductorSnapshotRetainsStructuralBubbleEvidenceOutsideActionBand() throws IOException {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> semiconductorDefinitions = definitions("Semiconductors");
        List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> snapshots = semiconductorDefinitions.stream()
                .map(definition -> {
                    try {
                        return SectorLPPLExhaustionMapDemo.analyzeInstrument(definition, load(definition),
                                SectorLPPLExhaustionMapDemo.AnalysisProfile.production());
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();

        assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.latestDate().equals(SNAPSHOT_DATE)));
        assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.side() == LPPLExhaustionSide.BUBBLE_EXHAUSTION));
        assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.regimeScore() < 0.0));
        assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.qualifiedFits() > snapshot.actionableFits()));
        SectorLPPLExhaustionMapDemo.GroupSnapshot group = SectorLPPLExhaustionMapDemo.aggregate(snapshots).getFirst();
        assertEquals("Semiconductors", group.group());
        assertTrue(group.spectrumScore() < 0.0);
    }

    @Test
    void commandLineKeepsNetworkAndResourceMutationExplicit() {
        SectorLPPLExhaustionMapDemo.DemoOptions defaults = SectorLPPLExhaustionMapDemo.DemoOptions.parse(new String[0]);
        SectorLPPLExhaustionMapDemo.DemoOptions refresh = SectorLPPLExhaustionMapDemo.DemoOptions
                .parse(new String[] { "--refresh", "--output-dir", tempDirectory.toString() });
        SectorLPPLExhaustionMapDemo.DemoOptions update = SectorLPPLExhaustionMapDemo.DemoOptions
                .parse(new String[] { "--update-resources" });

        assertFalse(defaults.refresh());
        assertFalse(defaults.updateResources());
        assertTrue(refresh.refresh());
        assertFalse(refresh.updateResources());
        assertEquals(tempDirectory.toAbsolutePath().normalize(), refresh.outputDirectory());
        assertTrue(update.refresh());
        assertTrue(update.updateResources());
        assertThrows(IllegalArgumentException.class,
                () -> SectorLPPLExhaustionMapDemo.DemoOptions.parse(new String[] { "--unknown" }));
    }

    private SectorLPPLExhaustionMapDemo.LensAgreement aggregateAgreement(String group, double primary,
            double equalWeight, double alternative) {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions = definitions(group);
        return SectorLPPLExhaustionMapDemo.aggregate(List.of(snapshot(definitions.get(0), primary, side(primary), 40),
                snapshot(definitions.get(1), equalWeight, side(equalWeight), 40),
                snapshot(definitions.get(2), alternative, side(alternative), 40))).getFirst().agreement();
    }

    private SectorLPPLExhaustionMapDemo.SpectrumBand aggregateBand(double score) {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> definitions = definitions("Financials");
        LPPLExhaustionSide side = side(score);
        return SectorLPPLExhaustionMapDemo
                .aggregate(List.of(snapshot(definitions.get(0), score, side, 40),
                        snapshot(definitions.get(1), score, side, 40), snapshot(definitions.get(2), score, side, 40)))
                .getFirst()
                .band();
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
            SectorLPPLExhaustionMapDemo.InstrumentDefinition definition, double score, LPPLExhaustionSide side,
            double criticalOffset) {
        return new SectorLPPLExhaustionMapDemo.InstrumentSnapshot(definition, SNAPSHOT_DATE, 1890, 0.0, 0.0, 0.0, 0.0,
                side, score, side == LPPLExhaustionSide.NONE ? 0 : 1, 0,
                side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1 : 0, Math.abs(score), 1.0, 0.9, criticalOffset, 0.0,
                criticalOffset <= 30 ? SectorLPPLExhaustionMapDemo.Proximity.IMMINENT
                        : criticalOffset <= 90 ? SectorLPPLExhaustionMapDemo.Proximity.APPROACHING
                                : SectorLPPLExhaustionMapDemo.Proximity.DEVELOPING,
                0.0, List.of());
    }

    private static LPPLExhaustionSide side(double score) {
        return score < 0.0 ? LPPLExhaustionSide.BUBBLE_EXHAUSTION
                : score > 0.0 ? LPPLExhaustionSide.CRASH_EXHAUSTION : LPPLExhaustionSide.NONE;
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
}
