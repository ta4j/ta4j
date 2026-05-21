/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.indicators.lppl.LPPLCalibrationProfile;
import org.ta4j.core.indicators.lppl.LPPLExhaustion;
import org.ta4j.core.indicators.lppl.LPPLExhaustionSide;
import org.ta4j.core.indicators.lppl.LPPLExhaustionStatus;
import org.ta4j.core.indicators.lppl.LPPLFit;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class SpdrSectorLPPLRotationDemoTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 4, 29);

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @TempDir
    Path tempDirectory;

    @Test
    void closedUniverseContainsAllElevenSpdrSectorEtfs() {
        String[] tickers = SpdrSectorLPPLRotationDemo.closedUniverse()
                .stream()
                .map(SpdrSectorLPPLRotationDemo.SectorDefinition::ticker)
                .toArray(String[]::new);
        assertArrayEquals(new String[] { "XLI", "XLV", "XLF", "XLRE", "XLE", "XLU", "XLK", "XLB", "XLP", "XLY", "XLC" },
                tickers);
    }

    @Test
    void aggregateComputesRelativeRotationDiagnosticsAgainstClosedUniverseAverage() {
        List<SpdrSectorLPPLRotationDemo.InstrumentSnapshot> instruments = List.of(
                instrument("XLI", "Industrials", 0.6, LPPLExhaustionSide.CRASH_EXHAUSTION),
                instrument("XLV", "Health Care", -0.2, LPPLExhaustionSide.BUBBLE_EXHAUSTION),
                instrument("XLF", "Financials", 0.0, LPPLExhaustionSide.NONE));

        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = SpdrSectorLPPLRotationDemo.aggregate(instruments);

        assertEquals(3, snapshots.size());
        assertEquals("Industrials", snapshots.get(0).sector());
        assertEquals(1.0, snapshots.get(0).netExhaustionScore());
        assertEquals(0.466666, snapshots.get(0).relativeRotationScore(), 0.0001);
        assertEquals(1, snapshots.get(0).relativeRank());
        assertEquals(1, snapshots.get(0).absoluteSignalRank());
        assertEquals(SpdrSectorLPPLRotationDemo.ExhaustionBucket.CRASH_EXHAUSTED_LEADER, snapshots.get(0).bucket());
        assertEquals("Health Care", snapshots.get(2).sector());
        assertEquals(-1.0, snapshots.get(2).netExhaustionScore());
        assertEquals(SpdrSectorLPPLRotationDemo.ExhaustionBucket.BUBBLE_EXHAUSTED_LAGGARD, snapshots.get(2).bucket());
    }

    @Test
    void aggregateIgnoresInvalidSidesWhenCountingCrashAndBubbleFits() {
        List<SpdrSectorLPPLRotationDemo.InstrumentSnapshot> instruments = List.of(instrument("XLI", "Industrials", 0.4,
                LPPLExhaustionSide.CRASH_EXHAUSTION, LPPLExhaustionStatus.NO_VALID_FIT));

        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = SpdrSectorLPPLRotationDemo.aggregate(instruments);

        assertEquals(1, snapshots.size());
        assertEquals(0, snapshots.get(0).crashCount());
        assertEquals(0, snapshots.get(0).bubbleCount());
        assertEquals(0.0, snapshots.get(0).netExhaustionScore());
        assertEquals(0.0, snapshots.get(0).lpplScore());
        assertEquals(SpdrSectorLPPLRotationDemo.ExhaustionBucket.NEUTRAL_OR_LOW_CONVICTION, snapshots.get(0).bucket());
    }

    @Test
    void aggregateReportsConvergedNonActionableFitDiagnostics() {
        LPPLFit fit = new LPPLFit(200, LPPLExhaustionStatus.VALID, 1.0, -0.03, 0.01, 0.02, 259.0, 0.5, 8.0, 0.02, 0.01,
                0.91, 60, 25);
        LPPLExhaustion exhaustion = new LPPLExhaustion(LPPLExhaustionStatus.NO_VALID_FIT, LPPLExhaustionSide.NONE,
                numFactory.zero(), numFactory.zero(), fit, List.of(fit), 1, 0, 0, 0);

        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = SpdrSectorLPPLRotationDemo.aggregate(List.of(
                new SpdrSectorLPPLRotationDemo.InstrumentSnapshot("XLI", "Industrials", SNAPSHOT_DATE, exhaustion)));
        String report = SpdrSectorLPPLRotationDemo.renderReport(snapshots);

        SpdrSectorLPPLRotationDemo.SectorSnapshot snapshot = snapshots.get(0);
        assertEquals(LPPLExhaustionStatus.NO_VALID_FIT, snapshot.status());
        assertEquals(LPPLExhaustionStatus.VALID, snapshot.dominantFitStatus());
        assertEquals(1, snapshot.attemptedFits());
        assertEquals(1, snapshot.convergedFits());
        assertEquals(0, snapshot.actionableFits());
        assertTrue(report.contains("NONE,NO_VALID_FIT,VALID,1,1,0,0,0"));
    }

    @Test
    void renderReportProducesDeterministicCsvTableWithDiagnostics() {
        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = List
                .of(new SpdrSectorLPPLRotationDemo.SectorSnapshot("Industrials", "XLI", SNAPSHOT_DATE, 1, 1, 0, 1.0,
                        0.6, 0.4, 1, 1, 1.25, LPPLExhaustionSide.CRASH_EXHAUSTION, LPPLExhaustionStatus.VALID,
                        LPPLExhaustionStatus.VALID, 1, 1, 1, 1, 0, 1.0, 1.0, 0.7, 80, 20, 0.9, 0.1,
                        SpdrSectorLPPLRotationDemo.ExhaustionBucket.CRASH_EXHAUSTED_LEADER));

        String report = SpdrSectorLPPLRotationDemo.renderReport(snapshots);

        assertTrue(report.startsWith(
                "date,sector,ticker,total,crash_count,bubble_count,net_exhaustion_score,standalone_lppl_score,relative_rotation_score,relative_rank,absolute_signal_rank,universe_z_score,side,status,dominant_fit_status,attempted_fits,converged_fits,actionable_fits,crash_fits,bubble_fits,valid_fit_share,side_consensus,fit_quality,dominant_window,critical_offset,r_squared,rms,bucket\n"
                        + "2026-04-29,Industrials,XLI,1,1,0,1.0000,0.6000,0.4000,1,1,1.2500,CRASH_EXHAUSTION,VALID,VALID,1,1,1,1,0,1.0000,1.0000,0.7000,80,20,0.9000,0.1000,CRASH_EXHAUSTED_LEADER\n\n"
                        + "LPPL calibration profile\n"));
        assertTrue(report.contains("windows=[200, 300, 400, 500]"));
        assertTrue(report.contains("min_r_squared=0.7500"));
        assertTrue(report.contains("\nInterpretation\n"));
        assertTrue(report.contains(
                "This snapshot has 1 dominant crash-exhaustion sector and 0 dominant bubble-exhaustion sectors"));
        assertTrue(report.contains("Qualitative buckets: CRASH_EXHAUSTED_LEADER=1."));
    }

    @Test
    void renderInterpretationRanksSignalsAndExplainsSingleProxyCaveat() {
        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = List.of(
                snapshot("Financials", "XLF", -0.1884, -0.1345, 1, 1, LPPLExhaustionSide.BUBBLE_EXHAUSTION,
                        SpdrSectorLPPLRotationDemo.ExhaustionBucket.BUBBLE_EXHAUSTED_LAGGARD),
                snapshot("Utilities", "XLU", -0.1381, -0.0841, 2, 2, LPPLExhaustionSide.BUBBLE_EXHAUSTION,
                        SpdrSectorLPPLRotationDemo.ExhaustionBucket.BUBBLE_EXHAUSTED_LAGGARD),
                snapshot("Technology", "XLK", 0.0, 0.0540, 3, 3, LPPLExhaustionSide.NONE,
                        SpdrSectorLPPLRotationDemo.ExhaustionBucket.NEUTRAL_OR_LOW_CONVICTION));

        String interpretation = SpdrSectorLPPLRotationDemo.renderInterpretation(snapshots);

        assertTrue(interpretation
                .contains("0 dominant crash-exhaustion sectors and 2 dominant bubble-exhaustion sectors"));
        assertTrue(interpretation.indexOf("Financials") < interpretation.indexOf("Utilities"));
        assertTrue(interpretation.contains("Neutral or low-conviction under this profile: Technology"));
        assertTrue(interpretation.contains("one ETF proxy per sector"));
    }

    @Test
    void analyzeLoadsAllOfflineSpdrResources() {
        LPPLCalibrationProfile smokeProfile = new LPPLCalibrationProfile(new int[] { 200 }, 0.1, 0.9, 2, 6.0, 13.0, 2,
                10, 30, 10, 10, 30, 25, 0.5);

        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = SpdrSectorLPPLRotationDemo.analyze(smokeProfile);

        assertEquals(11, snapshots.size());
        assertTrue(snapshots.stream().allMatch(snapshot -> Double.isFinite(snapshot.lpplScore())));
        assertTrue(snapshots.stream().allMatch(snapshot -> Double.isFinite(snapshot.relativeRotationScore())));
        assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.latestDate() != null));
    }

    @Test
    void runAnalysisDemoWritesTargetArtifactsFromRefreshedReferenceCopies() throws IOException {
        Path referenceDataDirectory = tempDirectory.resolve("resources");
        Path outputDirectory = tempDirectory.resolve("analysis");
        copySeedResources(referenceDataDirectory);
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater((ticker, start, end) -> List.of());
        SpdrSectorReferenceDataUpdater.Settings settings = new SpdrSectorReferenceDataUpdater.Settings(
                referenceDataDirectory, outputDirectory, outputDirectory.resolve("responses"), false, 7,
                Instant.parse("2026-05-01T12:00:00Z"));
        SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary = updater
                .refresh(SpdrSectorLPPLRotationDemo.closedUniverse(), settings);

        LPPLCalibrationProfile smokeProfile = new LPPLCalibrationProfile(new int[] { 200 }, 0.1, 0.9, 2, 6.0, 13.0, 2,
                10, 30, 10, 10, 30, 25, 0.5);
        List<SpdrSectorLPPLRotationDemo.SectorSnapshot> snapshots = SpdrSectorLPPLRotationDemo.analyze(smokeProfile,
                refreshSummary);
        String report = SpdrSectorLPPLRotationDemo.renderReport(snapshots, smokeProfile, refreshSummary);

        assertEquals(11, snapshots.size());
        assertTrue(report.contains("Reference data refresh"));
        assertTrue(Files.exists(
                outputDirectory.resolve("reference-data").resolve("YahooFinance-XLI-PT1D-20240102_20260429.json")));
    }

    @Test
    void systemPropertySettingsResolveRelativePathsFromRepositoryRoot() {
        String previousOutputDirectory = System.getProperty(SpdrSectorReferenceDataUpdater.DEMO_OUTPUT_DIR_PROPERTY);
        String previousReferenceDataDirectory = System
                .getProperty(SpdrSectorReferenceDataUpdater.REFERENCE_DATA_DIR_PROPERTY);
        try {
            System.setProperty(SpdrSectorReferenceDataUpdater.DEMO_OUTPUT_DIR_PROPERTY,
                    "target/analysis-demos/lppl-sector-rotation");
            System.setProperty(SpdrSectorReferenceDataUpdater.REFERENCE_DATA_DIR_PROPERTY,
                    "ta4j-examples/src/main/resources");

            SpdrSectorReferenceDataUpdater.Settings settings = SpdrSectorReferenceDataUpdater.Settings
                    .fromSystemProperties();
            Path repositoryRoot = repositoryRoot();

            assertEquals(repositoryRoot.resolve("target/analysis-demos/lppl-sector-rotation").normalize(),
                    settings.outputDirectory());
            assertEquals(repositoryRoot.resolve("ta4j-examples/src/main/resources").normalize(),
                    settings.referenceDataDirectory());
        } finally {
            restoreProperty(SpdrSectorReferenceDataUpdater.DEMO_OUTPUT_DIR_PROPERTY, previousOutputDirectory);
            restoreProperty(SpdrSectorReferenceDataUpdater.REFERENCE_DATA_DIR_PROPERTY, previousReferenceDataDirectory);
        }
    }

    @Test
    @Tag("lppl-sector-rotation-demo")
    void lpplSectorRotationDemoFunnelRefreshesReferencesAndWritesArtifacts() throws IOException {
        SpdrSectorReferenceDataUpdater.Settings settings = SpdrSectorReferenceDataUpdater.Settings
                .fromSystemProperties();

        SpdrSectorLPPLRotationDemo.DemoRun run = SpdrSectorLPPLRotationDemo
                .runAnalysisDemo(SpdrSectorLPPLRotationDemo.demoProfile(), settings);

        assertEquals(11, run.snapshots().size());
        assertTrue(Files.exists(settings.outputDirectory().resolve("lppl-sector-report.txt")));
        assertTrue(Files.exists(settings.outputDirectory().resolve("lppl-sector-snapshots.csv")));
        assertTrue(Files.exists(settings.outputDirectory().resolve("lppl-reference-refresh.csv")));
        assertTrue(run.report()
                .contains(SpdrSectorLPPLRotationDemo.renderProfile(SpdrSectorLPPLRotationDemo.demoProfile())));
        assertFalse(run.report().isBlank());
    }

    private void copySeedResources(Path referenceDataDirectory) throws IOException {
        Files.createDirectories(referenceDataDirectory);
        for (SpdrSectorLPPLRotationDemo.SectorDefinition definition : SpdrSectorLPPLRotationDemo.closedUniverse()) {
            try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(definition.resource())) {
                assertNotNull(stream, definition.resource());
                Files.copy(stream, referenceDataDirectory.resolve(definition.resource()));
            }
        }
    }

    private Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            if (Files.exists(directory.resolve("pom.xml")) && Files.isDirectory(directory.resolve("ta4j-examples"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private SpdrSectorLPPLRotationDemo.SectorSnapshot snapshot(String sector, String ticker, double score,
            double relativeScore, int relativeRank, int absoluteRank, LPPLExhaustionSide side,
            SpdrSectorLPPLRotationDemo.ExhaustionBucket bucket) {
        return new SpdrSectorLPPLRotationDemo.SectorSnapshot(sector, ticker, SNAPSHOT_DATE, 1,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1.0
                        : side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? -1.0 : 0.0,
                score, relativeScore, relativeRank, absoluteRank, 0.0, side,
                side == LPPLExhaustionSide.NONE ? LPPLExhaustionStatus.NO_VALID_FIT : LPPLExhaustionStatus.VALID,
                side == LPPLExhaustionSide.NONE ? LPPLExhaustionStatus.NO_VALID_FIT : LPPLExhaustionStatus.VALID, 1,
                side == LPPLExhaustionSide.NONE ? 0 : 1, side == LPPLExhaustionSide.NONE ? 0 : 1,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0, side == LPPLExhaustionSide.NONE ? 0.0 : 1.0,
                side == LPPLExhaustionSide.NONE ? 0.0 : 1.0, Math.abs(score), 80, 20, 0.9, 0.1, bucket);
    }

    private SpdrSectorLPPLRotationDemo.InstrumentSnapshot instrument(String ticker, String sector, double score,
            LPPLExhaustionSide side) {
        return instrument(ticker, sector, score, side, LPPLExhaustionStatus.VALID);
    }

    private SpdrSectorLPPLRotationDemo.InstrumentSnapshot instrument(String ticker, String sector, double score,
            LPPLExhaustionSide side, LPPLExhaustionStatus status) {
        LPPLFit fit = new LPPLFit(80,
                status == LPPLExhaustionStatus.VALID ? LPPLExhaustionStatus.VALID : LPPLExhaustionStatus.NO_VALID_FIT,
                1.0, side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? -0.03 : 0.03, 0.01, 0.02, 100.0, 0.5, 8.0, 0.1, 0.1,
                0.9, 20, 5);
        LPPLExhaustion exhaustion = new LPPLExhaustion(status, side, numFactory.numOf(score),
                numFactory.numOf(Math.abs(score)), fit, List.of(fit), 1, side == LPPLExhaustionSide.NONE ? 0 : 1,
                side == LPPLExhaustionSide.CRASH_EXHAUSTION ? 1 : 0,
                side == LPPLExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0);
        return new SpdrSectorLPPLRotationDemo.InstrumentSnapshot(ticker, sector, SNAPSHOT_DATE, exhaustion);
    }
}
