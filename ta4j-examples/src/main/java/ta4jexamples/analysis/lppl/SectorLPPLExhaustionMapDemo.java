/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.lppl.LPPLCalibrationProfile;
import org.ta4j.core.indicators.lppl.LPPLExhaustion;
import org.ta4j.core.indicators.lppl.LPPLExhaustionIndicator;
import org.ta4j.core.indicators.lppl.LPPLExhaustionSide;
import org.ta4j.core.indicators.lppl.LPPLFit;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Maps structural LPPL bubble and crash exhaustion evidence across three ETF
 * representations of every GICS sector plus the semiconductor sub-sector.
 *
 * <p>
 * The committed resources are split- and distribution-adjusted daily Yahoo
 * Finance bars. The default run is deterministic and offline. Structural regime
 * evidence is reported separately from the narrower near-term action horizon,
 * so a qualified developing regime is not presented as a neutral zero.
 *
 * @since 0.23.1
 */
public final class SectorLPPLExhaustionMapDemo {

    private static final Logger LOG = LogManager.getLogger(SectorLPPLExhaustionMapDemo.class);
    private static final LocalDate HISTORY_START = LocalDate.of(2019, 1, 2);
    private static final LocalDate SEED_SNAPSHOT_DATE = LocalDate.of(2026, 7, 10);
    private static final String RESOURCE_PREFIX = "ta4jexamples/analysis/lppl/sector-exhaustion-map/";
    private static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("target/analysis-demos/lppl-exhaustion-map");
    private static final String USAGE = "Usage: SectorLPPLExhaustionMapDemo [--refresh | --update-resources] "
            + "[--output-dir <path>] [--help]";

    private static final List<CoverageGroup> GROUPS = List.of(sector("Communication Services", "XLC", "RSPC", "FCOM"),
            sector("Consumer Discretionary", "XLY", "RSPD", "FDIS"), sector("Consumer Staples", "XLP", "RSPS", "FSTA"),
            sector("Energy", "XLE", "RSPG", "FENY"), sector("Financials", "XLF", "RSPF", "FNCL"),
            sector("Health Care", "XLV", "RSPH", "FHLC"), sector("Industrials", "XLI", "RSPN", "FIDU"),
            sector("Materials", "XLB", "RSPM", "FMAT"), sector("Real Estate", "XLRE", "RSPR", "FREL"),
            sector("Technology", "XLK", "RSPT", "FTEC"), sector("Utilities", "XLU", "RSPU", "FUTY"),
            semiconductorGroup());

    private SectorLPPLExhaustionMapDemo() {
    }

    /**
     * Runs the multi-lens LPPL exhaustion map.
     *
     * @param args optional refresh and output arguments
     * @throws IOException if data or report artifacts cannot be read or written
     * @since 0.23.1
     */
    public static void main(String[] args) throws IOException {
        DemoOptions options = DemoOptions.parse(args);
        if (options.help()) {
            System.out.println(USAGE);
            return;
        }
        DemoRun run = runDemo(AnalysisProfile.production(), options);
        System.out.print(run.report());
    }

    static DemoRun runDemo(AnalysisProfile profile, DemoOptions options) throws IOException {
        SectorLPPLReferenceDataUpdater.RefreshSummary refreshSummary = null;
        if (options.refresh()) {
            Path resourceDirectory = repositoryRoot(Path.of("")).resolve("ta4j-examples/src/main/resources");
            SectorLPPLReferenceDataUpdater.Settings settings = new SectorLPPLReferenceDataUpdater.Settings(
                    resourceDirectory, options.outputDirectory(), options.outputDirectory().resolve("responses"),
                    options.updateResources(), Instant.now());
            refreshSummary = new SectorLPPLReferenceDataUpdater().refresh(universe(), settings);
        }
        List<InstrumentSnapshot> instruments = analyze(profile, refreshSummary, options.outputDirectory());
        List<GroupSnapshot> groups = aggregate(instruments);
        String report = renderReport(groups, instruments, profile, refreshSummary);
        writeArtifacts(options.outputDirectory(), report, groups, instruments, refreshSummary);
        return new DemoRun(groups, instruments, refreshSummary, report);
    }

    static List<InstrumentDefinition> universe() {
        return GROUPS.stream().flatMap(group -> group.instruments().stream()).toList();
    }

    static List<CoverageGroup> coverageGroups() {
        return GROUPS;
    }

    static List<InstrumentSnapshot> analyze(AnalysisProfile profile) throws IOException {
        return analyze(profile, null, null);
    }

    static List<InstrumentSnapshot> analyze(AnalysisProfile profile,
            SectorLPPLReferenceDataUpdater.RefreshSummary refreshSummary, Path progressDirectory) throws IOException {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        List<InstrumentSnapshot> snapshots = new ArrayList<>(universe().size());
        for (InstrumentDefinition definition : universe()) {
            String source = refreshSummary == null ? definition.resource() : refreshSummary.sourceFor(definition);
            BarSeries series = dataSource.loadSeries(source);
            if (series == null || series.isEmpty()) {
                throw new IllegalStateException("Unable to load LPPL reference resource: " + source);
            }
            snapshots.add(analyzeInstrument(definition, series, profile));
            if (progressDirectory != null) {
                writeInstrumentProgress(progressDirectory, snapshots);
            }
            LOG.info("Completed LPPL calibration for {} ({}/{})", definition.ticker(), snapshots.size(),
                    universe().size());
        }
        return List.copyOf(snapshots);
    }

    static InstrumentSnapshot analyzeInstrument(InstrumentDefinition definition, BarSeries series,
            AnalysisProfile profile) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int endIndex = series.getEndIndex();
        List<FitSnapshot> fits = new ArrayList<>(profile.windows().length);
        for (int window : profile.windows()) {
            LPPLCalibrationProfile windowProfile = profile.profileFor(window);
            LPPLExhaustion exhaustion = new LPPLExhaustionIndicator(close, windowProfile).getValue(endIndex);
            LPPLFit fit = exhaustion.fits().isEmpty() ? exhaustion.dominantFit() : exhaustion.fits().getFirst();
            fits.add(new FitSnapshot(fit, fit.isQualified(windowProfile), fit.isActionable(windowProfile)));
        }

        int qualifiedFits = (int) fits.stream().filter(FitSnapshot::qualified).count();
        int actionableFits = (int) fits.stream().filter(FitSnapshot::actionable).count();
        int bubbleFits = countSide(fits, LPPLExhaustionSide.BUBBLE_EXHAUSTION);
        int crashFits = countSide(fits, LPPLExhaustionSide.CRASH_EXHAUSTION);
        LPPLExhaustionSide side = crashFits > bubbleFits ? LPPLExhaustionSide.CRASH_EXHAUSTION
                : bubbleFits > crashFits ? LPPLExhaustionSide.BUBBLE_EXHAUSTION : LPPLExhaustionSide.NONE;
        double confidence = fraction(qualifiedFits, fits.size());
        double consensus = qualifiedFits == 0 ? 0.0 : Math.abs(crashFits - bubbleFits) / (double) qualifiedFits;
        List<FitSnapshot> dominantFits = fits.stream()
                .filter(FitSnapshot::qualified)
                .filter(snapshot -> snapshot.fit().side() == side)
                .toList();
        double quality = dominantFits.stream().mapToDouble(snapshot -> snapshot.fit().rSquared()).average().orElse(0.0);
        double regimeScore = side.scoreSign() * confidence * consensus * quality;
        double medianOffset = percentile(dominantFits, 0.5);
        double firstQuartile = percentile(dominantFits, 0.25);
        double thirdQuartile = percentile(dominantFits, 0.75);

        return new InstrumentSnapshot(definition, latestBarDate(series), series.getBarCount(), returnOver(close, 20),
                returnOver(close, 60), returnOver(close, 120), returnOver(close, 252), side, regimeScore, qualifiedFits,
                actionableFits, bubbleFits, crashFits, confidence, consensus, quality, medianOffset,
                Double.isNaN(firstQuartile) ? Double.NaN : thirdQuartile - firstQuartile, proximity(medianOffset),
                fraction(actionableFits, fits.size()), fits);
    }

    static List<GroupSnapshot> aggregate(List<InstrumentSnapshot> instruments) {
        List<InstrumentSnapshot> safeInstruments = instruments == null ? List.of() : List.copyOf(instruments);
        List<GroupSnapshot> groups = new ArrayList<>(GROUPS.size());
        for (CoverageGroup definition : GROUPS) {
            List<InstrumentSnapshot> lenses = safeInstruments.stream()
                    .filter(snapshot -> snapshot.definition().group().equals(definition.name()))
                    .toList();
            if (lenses.isEmpty()) {
                continue;
            }
            if (lenses.size() != 3) {
                throw new IllegalArgumentException(definition.name() + " requires exactly three ETF lenses");
            }
            double spectrumScore = median(lenses.stream().mapToDouble(InstrumentSnapshot::regimeScore).toArray());
            int bubbleLenses = (int) lenses.stream()
                    .filter(snapshot -> snapshot.side() == LPPLExhaustionSide.BUBBLE_EXHAUSTION)
                    .count();
            int crashLenses = (int) lenses.stream()
                    .filter(snapshot -> snapshot.side() == LPPLExhaustionSide.CRASH_EXHAUSTION)
                    .count();
            LPPLExhaustionSide side = spectrumScore < 0.0 ? LPPLExhaustionSide.BUBBLE_EXHAUSTION
                    : spectrumScore > 0.0 ? LPPLExhaustionSide.CRASH_EXHAUSTION : LPPLExhaustionSide.NONE;
            List<InstrumentSnapshot> agreeing = lenses.stream().filter(snapshot -> snapshot.side() == side).toList();
            double medianOffset = median(agreeing.stream()
                    .mapToDouble(InstrumentSnapshot::medianCriticalOffset)
                    .filter(Double::isFinite)
                    .toArray());
            groups.add(new GroupSnapshot(definition.type(), definition.name(), latestDate(lenses), spectrumScore,
                    band(spectrumScore), agreement(bubbleLenses, crashLenses), side, proximity(medianOffset),
                    medianOffset, lens(lenses, Lens.PRIMARY), lens(lenses, Lens.EQUAL_WEIGHT),
                    lens(lenses, Lens.ALTERNATIVE)));
        }
        groups.sort(Comparator.comparingDouble(GroupSnapshot::spectrumScore).thenComparing(GroupSnapshot::group));
        return List.copyOf(groups);
    }

    static String renderReport(List<GroupSnapshot> groups, List<InstrumentSnapshot> instruments,
            AnalysisProfile profile, SectorLPPLReferenceDataUpdater.RefreshSummary refreshSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("LPPL sector and sub-sector exhaustion map\n")
                .append("Negative scores indicate bubble exhaustion; positive scores indicate crash exhaustion.\n\n")
                .append(renderGroupCsv(groups))
                .append('\n')
                .append(renderProfile(profile))
                .append('\n');
        if (refreshSummary != null) {
            builder.append(renderRefreshSummary(refreshSummary)).append('\n');
        }
        builder.append("Interpretation\n");
        for (GroupSnapshot group : groups) {
            builder.append(group.group())
                    .append(": ")
                    .append(group.band())
                    .append(" score=")
                    .append(format(group.spectrumScore()))
                    .append(" agreement=")
                    .append(group.agreement())
                    .append(" proximity=")
                    .append(group.proximity())
                    .append(" tc_median=")
                    .append(formatNullable(group.medianCriticalOffset()))
                    .append(" lenses=")
                    .append(formatLens(group.primary()))
                    .append("; ")
                    .append(formatLens(group.equalWeight()))
                    .append("; ")
                    .append(formatLens(group.alternative()))
                    .append('\n');
        }
        long developing = instruments.stream()
                .filter(snapshot -> snapshot.side() != LPPLExhaustionSide.NONE && snapshot.actionableFits() == 0)
                .count();
        builder.append("Qualified regimes outside the near-term action range remain visible: ")
                .append(developing)
                .append(" instrument(s).\n");
        return builder.toString();
    }

    static String renderGroupCsv(List<GroupSnapshot> groups) {
        StringBuilder builder = new StringBuilder(
                "date,type,group,spectrum_score,spectrum_band,lens_agreement,side,proximity,median_critical_offset,primary_ticker,primary_score,equal_weight_ticker,equal_weight_score,alternative_ticker,alternative_score\n");
        for (GroupSnapshot group : groups) {
            builder.append(group.latestDate())
                    .append(',')
                    .append(group.type())
                    .append(',')
                    .append(group.group())
                    .append(',')
                    .append(format(group.spectrumScore()))
                    .append(',')
                    .append(group.band())
                    .append(',')
                    .append(group.agreement())
                    .append(',')
                    .append(group.side())
                    .append(',')
                    .append(group.proximity())
                    .append(',')
                    .append(formatNullable(group.medianCriticalOffset()))
                    .append(',')
                    .append(group.primary().definition().ticker())
                    .append(',')
                    .append(format(group.primary().regimeScore()))
                    .append(',')
                    .append(group.equalWeight().definition().ticker())
                    .append(',')
                    .append(format(group.equalWeight().regimeScore()))
                    .append(',')
                    .append(group.alternative().definition().ticker())
                    .append(',')
                    .append(format(group.alternative().regimeScore()))
                    .append('\n');
        }
        return builder.toString();
    }

    static String renderInstrumentCsv(List<InstrumentSnapshot> instruments) {
        StringBuilder builder = new StringBuilder(
                "date,type,group,ticker,lens,weighting,universe,bars,return_20,return_60,return_120,return_252,side,regime_score,qualified_fits,actionable_fits,bubble_fits,crash_fits,regime_confidence,directional_consensus,average_r_squared,median_critical_offset,critical_offset_iqr,proximity,near_term_fit_share\n");
        for (InstrumentSnapshot snapshot : instruments) {
            InstrumentDefinition definition = snapshot.definition();
            builder.append(snapshot.latestDate())
                    .append(',')
                    .append(definition.type())
                    .append(',')
                    .append(definition.group())
                    .append(',')
                    .append(definition.ticker())
                    .append(',')
                    .append(definition.lens())
                    .append(',')
                    .append(definition.weighting())
                    .append(',')
                    .append(definition.universe())
                    .append(',')
                    .append(snapshot.bars())
                    .append(',')
                    .append(formatNullable(snapshot.return20()))
                    .append(',')
                    .append(formatNullable(snapshot.return60()))
                    .append(',')
                    .append(formatNullable(snapshot.return120()))
                    .append(',')
                    .append(formatNullable(snapshot.return252()))
                    .append(',')
                    .append(snapshot.side())
                    .append(',')
                    .append(format(snapshot.regimeScore()))
                    .append(',')
                    .append(snapshot.qualifiedFits())
                    .append(',')
                    .append(snapshot.actionableFits())
                    .append(',')
                    .append(snapshot.bubbleFits())
                    .append(',')
                    .append(snapshot.crashFits())
                    .append(',')
                    .append(format(snapshot.regimeConfidence()))
                    .append(',')
                    .append(format(snapshot.directionalConsensus()))
                    .append(',')
                    .append(format(snapshot.averageRSquared()))
                    .append(',')
                    .append(formatNullable(snapshot.medianCriticalOffset()))
                    .append(',')
                    .append(formatNullable(snapshot.criticalOffsetIqr()))
                    .append(',')
                    .append(snapshot.proximity())
                    .append(',')
                    .append(format(snapshot.nearTermFitShare()))
                    .append('\n');
        }
        return builder.toString();
    }

    static String renderFitCsv(List<InstrumentSnapshot> instruments) {
        StringBuilder builder = new StringBuilder(
                "date,group,ticker,lens,window,qualified,actionable,side,critical_offset,m,omega,r_squared,rms,evaluations\n");
        for (InstrumentSnapshot snapshot : instruments) {
            for (FitSnapshot fitSnapshot : snapshot.fits()) {
                LPPLFit fit = fitSnapshot.fit();
                builder.append(snapshot.latestDate())
                        .append(',')
                        .append(snapshot.definition().group())
                        .append(',')
                        .append(snapshot.definition().ticker())
                        .append(',')
                        .append(snapshot.definition().lens())
                        .append(',')
                        .append(fit.window())
                        .append(',')
                        .append(fitSnapshot.qualified())
                        .append(',')
                        .append(fitSnapshot.actionable())
                        .append(',')
                        .append(fit.side())
                        .append(',')
                        .append(fit.criticalOffset())
                        .append(',')
                        .append(formatNullable(fit.m()))
                        .append(',')
                        .append(formatNullable(fit.omega()))
                        .append(',')
                        .append(formatNullable(fit.rSquared()))
                        .append(',')
                        .append(formatNullable(fit.rms()))
                        .append(',')
                        .append(fit.evaluations())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    static String renderProfile(AnalysisProfile profile) {
        return "LPPL calibration profile\nwindows=" + Arrays.toString(profile.windows())
                + "\ncritical_time_search=1..ceil(window/3),step=" + profile.criticalOffsetStep()
                + "\nactionable_critical_offset_range=" + profile.activeMinCriticalOffset() + ".."
                + profile.activeMaxCriticalOffset() + "\nmax_evaluations=" + profile.maxEvaluations()
                + "\nmin_r_squared=" + format(profile.minRSquared()) + "\n";
    }

    static String renderRefreshSummary(SectorLPPLReferenceDataUpdater.RefreshSummary summary) {
        StringBuilder builder = new StringBuilder("Reference data refresh\n").append("analysis_data_dir=")
                .append(summary.analysisDataDirectory())
                .append('\n')
                .append("response_cache_dir=")
                .append(summary.responseCacheDirectory())
                .append('\n')
                .append("ticker,previous_last_date,new_last_date,existing_bars,fetched_bars,merged_bars,added_bars,revised_bars,skipped,message\n");
        for (SectorLPPLReferenceDataUpdater.TickerRefresh refresh : summary.tickers()) {
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
                    .append(csvSafe(refresh.message()))
                    .append('\n');
        }
        return builder.toString();
    }

    private static void writeArtifacts(Path outputDirectory, String report, List<GroupSnapshot> groups,
            List<InstrumentSnapshot> instruments, SectorLPPLReferenceDataUpdater.RefreshSummary refreshSummary)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("lppl-exhaustion-map.txt"), report, StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("lppl-group-spectrum.csv"), renderGroupCsv(groups),
                StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("lppl-instrument-regimes.csv"), renderInstrumentCsv(instruments),
                StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("lppl-fit-details.csv"), renderFitCsv(instruments),
                StandardCharsets.UTF_8);
        if (refreshSummary != null) {
            Files.writeString(outputDirectory.resolve("lppl-reference-refresh.csv"),
                    renderRefreshSummary(refreshSummary), StandardCharsets.UTF_8);
        }
    }

    private static void writeInstrumentProgress(Path outputDirectory, List<InstrumentSnapshot> snapshots)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("lppl-instrument-progress.csv"), renderInstrumentCsv(snapshots),
                StandardCharsets.UTF_8);
    }

    static Path repositoryRoot(Path start) {
        Path directory = start.toAbsolutePath().normalize();
        while (directory != null) {
            if (Files.exists(directory.resolve("pom.xml")) && Files.isDirectory(directory.resolve("ta4j-examples"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Unable to locate the ta4j repository root from " + start.toAbsolutePath());
    }

    private static CoverageGroup sector(String name, String primary, String equalWeight, String alternative) {
        return new CoverageGroup(CoverageType.SECTOR, name,
                List.of(instrument(CoverageType.SECTOR, name, primary, Lens.PRIMARY, Weighting.CAP_WEIGHTED, "SP500"),
                        instrument(CoverageType.SECTOR, name, equalWeight, Lens.EQUAL_WEIGHT, Weighting.EQUAL_WEIGHTED,
                                "SP500"),
                        instrument(CoverageType.SECTOR, name, alternative, Lens.ALTERNATIVE, Weighting.CAP_WEIGHTED,
                                "MSCI_USA_IMI")));
    }

    private static CoverageGroup semiconductorGroup() {
        String group = "Semiconductors";
        return new CoverageGroup(CoverageType.SUBSECTOR, group,
                List.of(instrument(CoverageType.SUBSECTOR, group, "SMH", Lens.PRIMARY,
                        Weighting.CONCENTRATED_CAP_WEIGHTED, "MVIS_US_LISTED_SEMICONDUCTOR_25"),
                        instrument(CoverageType.SUBSECTOR, group, "XSD", Lens.EQUAL_WEIGHT,
                                Weighting.MODIFIED_EQUAL_WEIGHTED, "SP_TOTAL_MARKET_SEMICONDUCTORS"),
                        instrument(CoverageType.SUBSECTOR, group, "SOXX", Lens.ALTERNATIVE,
                                Weighting.MODIFIED_CAP_WEIGHTED, "NYSE_SEMICONDUCTOR")));
    }

    private static InstrumentDefinition instrument(CoverageType type, String group, String ticker, Lens lens,
            Weighting weighting, String universe) {
        String resource = RESOURCE_PREFIX + "YahooFinance-" + ticker + "-PT1D-20190102_20260710.json";
        return new InstrumentDefinition(type, group, ticker, lens, weighting, universe, HISTORY_START, resource);
    }

    private static int countSide(List<FitSnapshot> fits, LPPLExhaustionSide side) {
        return (int) fits.stream()
                .filter(FitSnapshot::qualified)
                .filter(snapshot -> snapshot.fit().side() == side)
                .count();
    }

    private static InstrumentSnapshot lens(List<InstrumentSnapshot> instruments, Lens lens) {
        return instruments.stream().filter(snapshot -> snapshot.definition().lens() == lens).findFirst().orElseThrow();
    }

    private static double returnOver(ClosePriceIndicator close, int bars) {
        int end = close.getBarSeries().getEndIndex();
        int start = end - bars;
        if (start < close.getBarSeries().getBeginIndex()) {
            return Double.NaN;
        }
        return close.getValue(end).doubleValue() / close.getValue(start).doubleValue() - 1.0;
    }

    private static double percentile(List<FitSnapshot> fits, double percentile) {
        return percentile(fits.stream().mapToDouble(snapshot -> snapshot.fit().criticalOffset()).sorted().toArray(),
                percentile);
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double position = percentile * (values.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return values[lower];
        }
        return values[lower] + (values[upper] - values[lower]) * (position - lower);
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        Arrays.sort(values);
        return percentile(values, 0.5);
    }

    private static double fraction(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator / (double) denominator;
    }

    private static Proximity proximity(double offset) {
        if (!Double.isFinite(offset)) {
            return Proximity.NONE;
        }
        if (offset <= 30.0) {
            return Proximity.IMMINENT;
        }
        if (offset <= 90.0) {
            return Proximity.APPROACHING;
        }
        return Proximity.DEVELOPING;
    }

    private static SpectrumBand band(double score) {
        if (score <= -0.60) {
            return SpectrumBand.STRONG_BUBBLE;
        }
        if (score <= -0.25) {
            return SpectrumBand.BUBBLE;
        }
        if (score <= -0.10) {
            return SpectrumBand.WEAK_BUBBLE;
        }
        if (score < 0.10) {
            return SpectrumBand.NEUTRAL_OR_MIXED;
        }
        if (score < 0.25) {
            return SpectrumBand.WEAK_CRASH;
        }
        if (score < 0.60) {
            return SpectrumBand.CRASH;
        }
        return SpectrumBand.STRONG_CRASH;
    }

    private static LensAgreement agreement(int bubbleLenses, int crashLenses) {
        if (bubbleLenses > 0 && crashLenses > 0) {
            return LensAgreement.CONFLICTED;
        }
        int agreeing = Math.max(bubbleLenses, crashLenses);
        return switch (agreeing) {
        case 3 -> LensAgreement.THREE_OF_THREE;
        case 2 -> LensAgreement.TWO_OF_THREE;
        case 1 -> LensAgreement.ONE_OF_THREE;
        default -> LensAgreement.NO_SIGNAL;
        };
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

    private static String formatLens(InstrumentSnapshot snapshot) {
        return snapshot.definition().ticker() + "=" + format(snapshot.regimeScore()) + "/" + snapshot.side() + "/"
                + snapshot.proximity();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String formatNullable(double value) {
        return Double.isFinite(value) ? format(value) : "NA";
    }

    private static String csvSafe(String value) {
        return value == null ? "" : value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    enum CoverageType {
        SECTOR, SUBSECTOR
    }

    enum Lens {
        PRIMARY, EQUAL_WEIGHT, ALTERNATIVE
    }

    enum Weighting {
        CAP_WEIGHTED, EQUAL_WEIGHTED, CONCENTRATED_CAP_WEIGHTED, MODIFIED_EQUAL_WEIGHTED, MODIFIED_CAP_WEIGHTED
    }

    enum Proximity {
        IMMINENT, APPROACHING, DEVELOPING, NONE
    }

    enum SpectrumBand {
        STRONG_BUBBLE, BUBBLE, WEAK_BUBBLE, NEUTRAL_OR_MIXED, WEAK_CRASH, CRASH, STRONG_CRASH
    }

    enum LensAgreement {
        THREE_OF_THREE("3/3"), TWO_OF_THREE("2/3"), ONE_OF_THREE("1/3"), CONFLICTED("CONFLICTED"),
        NO_SIGNAL("NO_SIGNAL");

        private final String label;

        LensAgreement(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    record CoverageGroup(CoverageType type, String name, List<InstrumentDefinition> instruments) {

        CoverageGroup {
            instruments = List.copyOf(instruments);
            if (instruments.size() != 3) {
                throw new IllegalArgumentException("coverage groups require exactly three ETF lenses");
            }
        }
    }

    record InstrumentDefinition(CoverageType type, String group, String ticker, Lens lens, Weighting weighting,
            String universe, LocalDate historyStart, String resource) {
    }

    record AnalysisProfile(int[] windows, int criticalOffsetStep, int activeMinCriticalOffset,
            int activeMaxCriticalOffset, int maxEvaluations, double minRSquared) {

        AnalysisProfile {
            windows = Arrays.copyOf(windows, windows.length);
        }

        static AnalysisProfile production() {
            int[] windows = new int[26];
            for (int i = 0; i < windows.length; i++) {
                windows[i] = 125 + i * 25;
            }
            return new AnalysisProfile(windows, 5, 10, 30, 240, 0.75);
        }

        @Override
        public int[] windows() {
            return Arrays.copyOf(windows, windows.length);
        }

        LPPLCalibrationProfile profileFor(int window) {
            int maximumCriticalOffset = (int) Math.ceil(window / 3.0);
            return LPPLCalibrationProfile.defaults()
                    .withWindows(window)
                    .withCriticalTimeSearch(1, maximumCriticalOffset, criticalOffsetStep)
                    .withActionableCriticalTimeRange(activeMinCriticalOffset, activeMaxCriticalOffset)
                    .withOptimizerSettings(maxEvaluations, minRSquared);
        }
    }

    record FitSnapshot(LPPLFit fit, boolean qualified, boolean actionable) {
    }

    record InstrumentSnapshot(InstrumentDefinition definition, LocalDate latestDate, int bars, double return20,
            double return60, double return120, double return252, LPPLExhaustionSide side, double regimeScore,
            int qualifiedFits, int actionableFits, int bubbleFits, int crashFits, double regimeConfidence,
            double directionalConsensus, double averageRSquared, double medianCriticalOffset, double criticalOffsetIqr,
            Proximity proximity, double nearTermFitShare, List<FitSnapshot> fits) {

        InstrumentSnapshot {
            fits = List.copyOf(fits);
        }
    }

    record GroupSnapshot(CoverageType type, String group, LocalDate latestDate, double spectrumScore, SpectrumBand band,
            LensAgreement agreement, LPPLExhaustionSide side, Proximity proximity, double medianCriticalOffset,
            InstrumentSnapshot primary, InstrumentSnapshot equalWeight, InstrumentSnapshot alternative) {
    }

    record DemoRun(List<GroupSnapshot> groups, List<InstrumentSnapshot> instruments,
            SectorLPPLReferenceDataUpdater.RefreshSummary refreshSummary, String report) {
    }

    record DemoOptions(Path outputDirectory, boolean refresh, boolean updateResources, boolean help) {

        DemoOptions {
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
        }

        static DemoOptions parse(String[] args) {
            Path output = DEFAULT_OUTPUT_DIRECTORY;
            boolean refresh = false;
            boolean updateResources = false;
            boolean help = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                case "--refresh" -> refresh = true;
                case "--update-resources" -> {
                    refresh = true;
                    updateResources = true;
                }
                case "--output-dir" -> {
                    if (++i >= args.length) {
                        throw new IllegalArgumentException("--output-dir requires a path\n" + USAGE);
                    }
                    output = Path.of(args[i]);
                }
                case "--help" -> help = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i] + "\n" + USAGE);
                }
            }
            return new DemoOptions(output, refresh, updateResources, help);
        }
    }
}
