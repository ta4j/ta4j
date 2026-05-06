/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.lppl.LpplCalibrationProfile;
import org.ta4j.core.indicators.lppl.LpplExhaustion;
import org.ta4j.core.indicators.lppl.LpplExhaustionIndicator;
import org.ta4j.core.indicators.lppl.LpplExhaustionSide;
import org.ta4j.core.indicators.lppl.LpplExhaustionStatus;
import org.ta4j.core.indicators.lppl.LpplFit;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates LPPL exhaustion scoring across the closed State Street Select
 * Sector SPDR ETF universe.
 *
 * <p>
 * The committed seed resources are adjusted daily Yahoo Finance bars ending
 * near the 2026-04-29 reference snapshot. Analysis-demo runs can refresh those
 * references incrementally with live Yahoo deltas before running the report,
 * keeping local runs deterministic by writing refreshed copies under
 * {@code target/analysis-demos}.
 */
public final class SpdrSectorLpplRotationDemo {

    private static final LocalDate SEED_SNAPSHOT_DATE = LocalDate.of(2026, 4, 29);

    private static final List<SectorDefinition> UNIVERSE = List.of(
            new SectorDefinition("XLI", "Industrials", "YahooFinance-XLI-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLV", "Health Care", "YahooFinance-XLV-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLF", "Financials", "YahooFinance-XLF-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLRE", "Real Estate", "YahooFinance-XLRE-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLE", "Energy", "YahooFinance-XLE-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLU", "Utilities", "YahooFinance-XLU-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLK", "Technology", "YahooFinance-XLK-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLB", "Materials", "YahooFinance-XLB-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLP", "Consumer Staples", "YahooFinance-XLP-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLY", "Consumer Discretionary", "YahooFinance-XLY-PT1D-20240102_20260429.json"),
            new SectorDefinition("XLC", "Communication Services", "YahooFinance-XLC-PT1D-20240102_20260429.json"));

    private SpdrSectorLpplRotationDemo() {
    }

    /**
     * Runs the SPDR LPPL rotation demo.
     *
     * @param args ignored
     * @throws IOException if refreshed reference-data artifacts cannot be written
     */
    public static void main(String[] args) throws IOException {
        DemoRun run = runAnalysisDemo(demoProfile(), SpdrSectorReferenceDataUpdater.Settings.fromSystemProperties());
        System.out.print(run.report());
    }

    static List<SectorDefinition> closedUniverse() {
        return UNIVERSE;
    }

    static LpplCalibrationProfile demoProfile() {
        return LpplCalibrationProfile.defaults();
    }

    static DemoRun runAnalysisDemo(LpplCalibrationProfile profile, SpdrSectorReferenceDataUpdater.Settings settings)
            throws IOException {
        SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary = new SpdrSectorReferenceDataUpdater()
                .refresh(UNIVERSE, settings);
        List<SectorSnapshot> snapshots = analyze(profile, refreshSummary);
        String report = renderReport(snapshots, profile, refreshSummary);
        writeArtifacts(settings.outputDirectory(), report, snapshots, refreshSummary);
        return new DemoRun(snapshots, refreshSummary, report);
    }

    static List<SectorSnapshot> analyze(LpplCalibrationProfile profile) {
        return analyze(profile, null);
    }

    static List<SectorSnapshot> analyze(LpplCalibrationProfile profile,
            SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary) {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        List<InstrumentSnapshot> instruments = new ArrayList<>(UNIVERSE.size());
        for (SectorDefinition definition : UNIVERSE) {
            String source = refreshSummary == null ? definition.resource() : refreshSummary.sourceFor(definition);
            BarSeries series = dataSource.loadSeries(source);
            if (series == null || series.isEmpty()) {
                throw new IllegalStateException("Unable to load SPDR resource: " + source);
            }
            LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series), profile);
            instruments.add(new InstrumentSnapshot(definition.ticker(), definition.sector(), latestBarDate(series),
                    indicator.getValue(series.getEndIndex())));
        }
        return aggregate(instruments);
    }

    static List<SectorSnapshot> aggregate(List<InstrumentSnapshot> instruments) {
        List<InstrumentSnapshot> safeInstruments = instruments == null ? List.of() : List.copyOf(instruments);
        double universeAverage = safeInstruments.stream()
                .mapToDouble(SpdrSectorLpplRotationDemo::scoreValue)
                .average()
                .orElse(0.0);
        double universeStdDev = standardDeviation(safeInstruments, universeAverage);

        List<SectorSnapshot> snapshots = new ArrayList<>();
        for (SectorDefinition definition : UNIVERSE) {
            List<InstrumentSnapshot> sectorInstruments = safeInstruments.stream()
                    .filter(instrument -> instrument.sector().equals(definition.sector()))
                    .toList();
            if (sectorInstruments.isEmpty()) {
                continue;
            }
            int crashCount = (int) sectorInstruments.stream()
                    .filter(SpdrSectorLpplRotationDemo::hasCrashExhaustion)
                    .count();
            int bubbleCount = (int) sectorInstruments.stream()
                    .filter(SpdrSectorLpplRotationDemo::hasBubbleExhaustion)
                    .count();
            double lpplScore = sectorInstruments.stream()
                    .mapToDouble(SpdrSectorLpplRotationDemo::scoreValue)
                    .average()
                    .orElse(0.0);
            double total = sectorInstruments.size();
            LpplExhaustion dominant = dominantExhaustion(sectorInstruments);
            LpplFit dominantFit = dominant.dominantFit();
            int attemptedFits = sectorInstruments.stream()
                    .map(InstrumentSnapshot::exhaustion)
                    .mapToInt(LpplExhaustion::attemptedFits)
                    .sum();
            int convergedFits = sectorInstruments.stream()
                    .map(InstrumentSnapshot::exhaustion)
                    .mapToInt(SpdrSectorLpplRotationDemo::convergedFitCount)
                    .sum();
            int actionableFits = sectorInstruments.stream()
                    .map(InstrumentSnapshot::exhaustion)
                    .mapToInt(LpplExhaustion::validFits)
                    .sum();
            int crashFits = sectorInstruments.stream()
                    .map(InstrumentSnapshot::exhaustion)
                    .mapToInt(LpplExhaustion::crashFits)
                    .sum();
            int bubbleFits = sectorInstruments.stream()
                    .map(InstrumentSnapshot::exhaustion)
                    .mapToInt(LpplExhaustion::bubbleFits)
                    .sum();
            double relativeScore = lpplScore - universeAverage;
            LpplExhaustionSide side = crashCount > bubbleCount ? LpplExhaustionSide.CRASH_EXHAUSTION
                    : bubbleCount > crashCount ? LpplExhaustionSide.BUBBLE_EXHAUSTION : LpplExhaustionSide.NONE;
            snapshots.add(new SectorSnapshot(definition.sector(), definition.ticker(), latestDate(sectorInstruments),
                    sectorInstruments.size(), crashCount, bubbleCount, (crashCount - bubbleCount) / total, lpplScore,
                    relativeScore, 0, 0, universeStdDev == 0.0 ? 0.0 : relativeScore / universeStdDev, side,
                    dominant.status(), dominantFit.status(), attemptedFits, convergedFits, actionableFits, crashFits,
                    bubbleFits, validFitShare(attemptedFits, actionableFits),
                    sideConsensus(crashFits, bubbleFits, actionableFits), finite(dominant.fitQuality().doubleValue()),
                    dominantFit.window(), dominantFit.criticalOffset(), finite(dominantFit.rSquared()),
                    finite(dominantFit.rms()), bucket(side, relativeScore)));
        }
        snapshots = withRanks(snapshots);
        snapshots.sort(Comparator.comparingDouble(SectorSnapshot::relativeRotationScore)
                .reversed()
                .thenComparing(SectorSnapshot::sector));
        return List.copyOf(snapshots);
    }

    static String renderReport(List<SectorSnapshot> snapshots) {
        return renderReport(snapshots, demoProfile(), null);
    }

    static String renderReport(List<SectorSnapshot> snapshots, LpplCalibrationProfile profile) {
        return renderReport(snapshots, profile, null);
    }

    static String renderReport(List<SectorSnapshot> snapshots,
            SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary) {
        return renderReport(snapshots, demoProfile(), refreshSummary);
    }

    static String renderReport(List<SectorSnapshot> snapshots, LpplCalibrationProfile profile,
            SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary) {
        List<SectorSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        StringBuilder builder = new StringBuilder();
        builder.append(renderSnapshotCsv(safeSnapshots)).append('\n');
        builder.append(renderProfile(profile)).append('\n');
        if (refreshSummary != null) {
            builder.append(renderRefreshSummary(refreshSummary)).append('\n');
        }
        builder.append(renderInterpretation(safeSnapshots));
        return builder.toString();
    }

    static String renderProfile(LpplCalibrationProfile profile) {
        LpplCalibrationProfile safeProfile = profile == null ? demoProfile() : profile;
        return "LPPL calibration profile\n" + "windows=" + Arrays.toString(safeProfile.windows()) + "\n" + "m_range="
                + format(safeProfile.minM()) + ".." + format(safeProfile.maxM()) + ",m_steps=" + safeProfile.mSteps()
                + "\n" + "omega_range=" + format(safeProfile.minOmega()) + ".." + format(safeProfile.maxOmega())
                + ",omega_steps=" + safeProfile.omegaSteps() + "\n" + "critical_offset_range="
                + safeProfile.minCriticalOffset() + ".." + safeProfile.maxCriticalOffset() + ",critical_offset_step="
                + safeProfile.criticalOffsetStep() + "\n" + "active_critical_offset_range="
                + safeProfile.activeMinCriticalOffset() + ".." + safeProfile.activeMaxCriticalOffset() + "\n"
                + "max_evaluations=" + safeProfile.maxEvaluations() + "\n" + "min_r_squared="
                + format(safeProfile.minRSquared()) + "\n";
    }

    static String renderRefreshSummary(SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("Reference data refresh\n")
                .append("analysis_data_dir=")
                .append(refreshSummary.analysisDataDirectory())
                .append('\n')
                .append("response_cache_dir=")
                .append(refreshSummary.responseCacheDirectory())
                .append('\n')
                .append("ticker,previous_last_date,new_last_date,existing_bars,fetched_bars,merged_bars,added_bars,revised_bars,skipped,message\n");
        for (SpdrSectorReferenceDataUpdater.TickerRefresh refresh : refreshSummary.tickers()) {
            builder.append(refresh.ticker())
                    .append(',')
                    .append(refresh.previousLastDate())
                    .append(',')
                    .append(refresh.newLastDate())
                    .append(',')
                    .append(refresh.existingBars())
                    .append(',')
                    .append(refresh.fetchedBars())
                    .append(',')
                    .append(refresh.mergedBars())
                    .append(',')
                    .append(refresh.addedBars())
                    .append(',')
                    .append(refresh.revisedBars())
                    .append(',')
                    .append(refresh.skipped())
                    .append(',')
                    .append(message(refresh))
                    .append('\n');
        }
        return builder.toString();
    }

    static String renderInterpretation(List<SectorSnapshot> snapshots) {
        List<SectorSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (safeSnapshots.isEmpty()) {
            return "Interpretation\nNo sector snapshots were available for interpretation.\n";
        }

        List<SectorSnapshot> crashSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.side() == LpplExhaustionSide.CRASH_EXHAUSTION)
                .sorted(Comparator.comparingDouble(SectorSnapshot::lpplScore).reversed())
                .toList();
        List<SectorSnapshot> bubbleSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.side() == LpplExhaustionSide.BUBBLE_EXHAUSTION)
                .sorted(Comparator.comparingDouble(SectorSnapshot::lpplScore))
                .toList();
        List<SectorSnapshot> neutralSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.side() == LpplExhaustionSide.NONE)
                .sorted(Comparator.comparing(SectorSnapshot::sector))
                .toList();

        int totalInstruments = safeSnapshots.stream().mapToInt(SectorSnapshot::totalInstruments).sum();
        SectorSnapshot strongestRelative = safeSnapshots.stream()
                .min(Comparator.comparingInt(SectorSnapshot::relativeRank))
                .orElseThrow();
        SectorSnapshot weakestRelative = safeSnapshots.stream()
                .max(Comparator.comparingInt(SectorSnapshot::relativeRank))
                .orElseThrow();

        StringBuilder builder = new StringBuilder();
        builder.append("Interpretation\n");
        builder.append(
                "Positive standalone LPPL scores indicate crash exhaustion; negative scores indicate bubble exhaustion.\n");
        builder.append("This snapshot has ")
                .append(crashSignals.size())
                .append(" dominant crash-exhaustion ")
                .append(label(crashSignals.size(), "sector"))
                .append(" and ")
                .append(bubbleSignals.size())
                .append(" dominant bubble-exhaustion ")
                .append(label(bubbleSignals.size(), "sector"))
                .append(" across ")
                .append(totalInstruments)
                .append(' ')
                .append(label(totalInstruments, "instrument"))
                .append(".\n");

        appendSignalSummary(builder, "Strongest crash-exhaustion candidates", crashSignals);
        appendSignalSummary(builder, "Strongest bubble-exhaustion warnings", bubbleSignals);
        appendBucketSummary(builder, safeSnapshots);

        if (!neutralSignals.isEmpty()) {
            builder.append("Neutral or low-conviction under this profile: ")
                    .append(formatSectors(neutralSignals))
                    .append(". Their relative score only reflects position versus the universe average.\n");
        }

        builder.append("Relative rotation leader: ")
                .append(formatSector(strongestRelative))
                .append("; relative rotation laggard: ")
                .append(formatSector(weakestRelative))
                .append(".\n");
        appendDivergenceSummary(builder, safeSnapshots);

        if (safeSnapshots.stream().allMatch(snapshot -> snapshot.totalInstruments() == 1)) {
            builder.append(
                    "This closed SPDR demo uses one ETF proxy per sector, so net_exhaustion_score is binary; a production constituent universe would make it a breadth percentage.\n");
        }
        return builder.toString();
    }

    private static String renderSnapshotCsv(List<SectorSnapshot> snapshots) {
        StringBuilder builder = new StringBuilder();
        builder.append(
                "date,sector,ticker,total,crash_count,bubble_count,net_exhaustion_score,standalone_lppl_score,relative_rotation_score,relative_rank,absolute_signal_rank,universe_z_score,side,status,dominant_fit_status,attempted_fits,converged_fits,actionable_fits,crash_fits,bubble_fits,valid_fit_share,side_consensus,fit_quality,dominant_window,critical_offset,r_squared,rms,bucket\n");
        for (SectorSnapshot snapshot : snapshots) {
            appendSnapshotCsvRow(builder, snapshot);
        }
        return builder.toString();
    }

    private static void appendSnapshotCsvRow(StringBuilder builder, SectorSnapshot snapshot) {
        builder.append(snapshot.latestDate())
                .append(',')
                .append(snapshot.sector())
                .append(',')
                .append(snapshot.ticker())
                .append(',')
                .append(snapshot.totalInstruments())
                .append(',')
                .append(snapshot.crashCount())
                .append(',')
                .append(snapshot.bubbleCount())
                .append(',')
                .append(format(snapshot.netExhaustionScore()))
                .append(',')
                .append(format(snapshot.lpplScore()))
                .append(',')
                .append(format(snapshot.relativeRotationScore()))
                .append(',')
                .append(snapshot.relativeRank())
                .append(',')
                .append(snapshot.absoluteSignalRank())
                .append(',')
                .append(format(snapshot.universeZScore()))
                .append(',')
                .append(snapshot.side())
                .append(',')
                .append(snapshot.status())
                .append(',')
                .append(snapshot.dominantFitStatus())
                .append(',')
                .append(snapshot.attemptedFits())
                .append(',')
                .append(snapshot.convergedFits())
                .append(',')
                .append(snapshot.actionableFits())
                .append(',')
                .append(snapshot.crashFits())
                .append(',')
                .append(snapshot.bubbleFits())
                .append(',')
                .append(format(snapshot.validFitShare()))
                .append(',')
                .append(format(snapshot.sideConsensus()))
                .append(',')
                .append(format(snapshot.fitQuality()))
                .append(',')
                .append(snapshot.dominantWindow())
                .append(',')
                .append(snapshot.criticalOffset())
                .append(',')
                .append(format(snapshot.rSquared()))
                .append(',')
                .append(format(snapshot.rms()))
                .append(',')
                .append(snapshot.bucket())
                .append('\n');
    }

    private static void appendSignalSummary(StringBuilder builder, String label, List<SectorSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            builder.append(label).append(": none.\n");
            return;
        }
        builder.append(label).append(": ").append(formatSectors(snapshots)).append(".\n");
    }

    private static void appendBucketSummary(StringBuilder builder, List<SectorSnapshot> snapshots) {
        builder.append("Qualitative buckets: ");
        List<String> buckets = new ArrayList<>();
        for (ExhaustionBucket bucket : ExhaustionBucket.values()) {
            long count = snapshots.stream().filter(snapshot -> snapshot.bucket() == bucket).count();
            if (count > 0) {
                buckets.add(bucket + "=" + count);
            }
        }
        builder.append(String.join(", ", buckets)).append(".\n");
    }

    private static void appendDivergenceSummary(StringBuilder builder, List<SectorSnapshot> snapshots) {
        List<SectorSnapshot> divergences = snapshots.stream()
                .filter(snapshot -> snapshot.bucket() == ExhaustionBucket.CRASH_EXHAUSTED_LAGGARD
                        || snapshot.bucket() == ExhaustionBucket.BUBBLE_EXHAUSTED_RELATIVE_HOLDOUT)
                .sorted(Comparator.comparingInt(SectorSnapshot::absoluteSignalRank))
                .toList();
        if (!divergences.isEmpty()) {
            builder.append("Standalone/relative divergences: ")
                    .append(formatSectors(divergences))
                    .append(". These sectors have LPPL exhaustion in one direction but sit on the other side of the universe-relative rotation line.\n");
        }
    }

    private static void writeArtifacts(Path outputDirectory, String report, List<SectorSnapshot> snapshots,
            SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("lppl-sector-report.txt"), report, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("lppl-sector-snapshots.csv"), renderSnapshotCsv(snapshots),
                StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("lppl-reference-refresh.csv"), renderRefreshSummary(refreshSummary),
                StandardCharsets.UTF_8);
    }

    private static List<SectorSnapshot> withRanks(List<SectorSnapshot> snapshots) {
        List<SectorSnapshot> rankedByRelative = snapshots.stream()
                .sorted(Comparator.comparingDouble(SectorSnapshot::relativeRotationScore)
                        .reversed()
                        .thenComparing(SectorSnapshot::sector))
                .toList();
        List<SectorSnapshot> rankedByAbsolute = snapshots.stream()
                .sorted(Comparator.comparingDouble((SectorSnapshot snapshot) -> Math.abs(snapshot.lpplScore()))
                        .reversed()
                        .thenComparing(SectorSnapshot::sector))
                .toList();

        List<SectorSnapshot> ranked = new ArrayList<>(snapshots.size());
        for (SectorSnapshot snapshot : snapshots) {
            ranked.add(
                    snapshot.withRanks(rankedByRelative.indexOf(snapshot) + 1, rankedByAbsolute.indexOf(snapshot) + 1));
        }
        return ranked;
    }

    private static String formatSectors(List<SectorSnapshot> snapshots) {
        return snapshots.stream()
                .map(SpdrSectorLpplRotationDemo::formatSector)
                .reduce((left, right) -> left + "; " + right)
                .orElse("none");
    }

    private static String formatSector(SectorSnapshot snapshot) {
        return snapshot.sector() + " (" + snapshot.ticker() + ", standalone=" + format(snapshot.lpplScore())
                + ", relative=" + format(snapshot.relativeRotationScore()) + ", rank=" + snapshot.relativeRank()
                + ", confidence=" + format(snapshot.fitQuality()) + ")";
    }

    private static LocalDate latestBarDate(BarSeries series) {
        Bar bar = series.getLastBar();
        Duration period = bar.getTimePeriod();
        return bar.getEndTime().minus(period).atZone(ZoneId.of("America/New_York")).toLocalDate();
    }

    private static LocalDate latestDate(List<InstrumentSnapshot> instruments) {
        return instruments.stream()
                .map(InstrumentSnapshot::latestDate)
                .max(LocalDate::compareTo)
                .orElse(SEED_SNAPSHOT_DATE);
    }

    private static LpplExhaustion dominantExhaustion(List<InstrumentSnapshot> instruments) {
        return instruments.stream()
                .map(InstrumentSnapshot::exhaustion)
                .max(Comparator.comparingDouble(SpdrSectorLpplRotationDemo::diagnosticStrength))
                .orElseThrow();
    }

    private static double diagnosticStrength(LpplExhaustion exhaustion) {
        if (exhaustion.isValid()) {
            return 2.0 + Math.abs(scoreValue(exhaustion));
        }
        LpplFit fit = exhaustion.dominantFit();
        return fit.isConverged() ? 1.0 + finite(fit.rSquared()) : 0.0;
    }

    private static double standardDeviation(List<InstrumentSnapshot> instruments, double average) {
        if (instruments.isEmpty()) {
            return 0.0;
        }
        double variance = instruments.stream()
                .mapToDouble(SpdrSectorLpplRotationDemo::scoreValue)
                .map(value -> Math.pow(value - average, 2.0))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private static int convergedFitCount(LpplExhaustion exhaustion) {
        return (int) exhaustion.fits().stream().filter(LpplFit::isConverged).count();
    }

    private static double validFitShare(int attemptedFits, int actionableFits) {
        return attemptedFits == 0 ? 0.0 : (double) actionableFits / attemptedFits;
    }

    private static double sideConsensus(int crashFits, int bubbleFits, int actionableFits) {
        return actionableFits == 0 ? 0.0 : (double) Math.abs(crashFits - bubbleFits) / actionableFits;
    }

    private static ExhaustionBucket bucket(LpplExhaustionSide side, double relativeScore) {
        if (side == LpplExhaustionSide.CRASH_EXHAUSTION) {
            return relativeScore >= 0.0 ? ExhaustionBucket.CRASH_EXHAUSTED_LEADER
                    : ExhaustionBucket.CRASH_EXHAUSTED_LAGGARD;
        }
        if (side == LpplExhaustionSide.BUBBLE_EXHAUSTION) {
            return relativeScore <= 0.0 ? ExhaustionBucket.BUBBLE_EXHAUSTED_LAGGARD
                    : ExhaustionBucket.BUBBLE_EXHAUSTED_RELATIVE_HOLDOUT;
        }
        return ExhaustionBucket.NEUTRAL_OR_LOW_CONVICTION;
    }

    private static String label(long count, String singular) {
        return count == 1 ? singular : singular + "s";
    }

    private static String message(SpdrSectorReferenceDataUpdater.TickerRefresh refresh) {
        return refresh.message() == null ? "" : refresh.message().replace(',', ';');
    }

    private static boolean hasCrashExhaustion(InstrumentSnapshot instrument) {
        return instrument.exhaustion().isValid()
                && instrument.exhaustion().side() == LpplExhaustionSide.CRASH_EXHAUSTION;
    }

    private static boolean hasBubbleExhaustion(InstrumentSnapshot instrument) {
        return instrument.exhaustion().isValid()
                && instrument.exhaustion().side() == LpplExhaustionSide.BUBBLE_EXHAUSTION;
    }

    private static double scoreValue(InstrumentSnapshot instrument) {
        return scoreValue(instrument.exhaustion());
    }

    private static double scoreValue(LpplExhaustion exhaustion) {
        if (!exhaustion.isValid()) {
            return 0.0;
        }
        double value = exhaustion.score().doubleValue();
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    enum ExhaustionBucket {
        CRASH_EXHAUSTED_LEADER, CRASH_EXHAUSTED_LAGGARD, BUBBLE_EXHAUSTED_LAGGARD, BUBBLE_EXHAUSTED_RELATIVE_HOLDOUT,
        NEUTRAL_OR_LOW_CONVICTION
    }

    record DemoRun(List<SectorSnapshot> snapshots, SpdrSectorReferenceDataUpdater.RefreshSummary refreshSummary,
            String report) {
    }

    record SectorDefinition(String ticker, String sector, String resource) {
    }

    record InstrumentSnapshot(String ticker, String sector, LocalDate latestDate, LpplExhaustion exhaustion) {
    }

    record SectorSnapshot(String sector, String ticker, LocalDate latestDate, int totalInstruments, int crashCount,
            int bubbleCount, double netExhaustionScore, double lpplScore, double relativeRotationScore,
            int relativeRank, int absoluteSignalRank, double universeZScore, LpplExhaustionSide side,
            LpplExhaustionStatus status, LpplExhaustionStatus dominantFitStatus, int attemptedFits, int convergedFits,
            int actionableFits, int crashFits, int bubbleFits, double validFitShare, double sideConsensus,
            double fitQuality, int dominantWindow, int criticalOffset, double rSquared, double rms,
            ExhaustionBucket bucket) {

        SectorSnapshot withRanks(int relativeRank, int absoluteSignalRank) {
            return new SectorSnapshot(sector, ticker, latestDate, totalInstruments, crashCount, bubbleCount,
                    netExhaustionScore, lpplScore, relativeRotationScore, relativeRank, absoluteSignalRank,
                    universeZScore, side, status, dominantFitStatus, attemptedFits, convergedFits, actionableFits,
                    crashFits, bubbleFits, validFitShare, sideConsensus, fitQuality, dominantWindow, criticalOffset,
                    rSquared, rms, bucket);
        }
    }
}
