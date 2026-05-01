/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.lppl.LpplCalibrationProfile;
import org.ta4j.core.indicators.lppl.LpplExhaustion;
import org.ta4j.core.indicators.lppl.LpplExhaustionIndicator;
import org.ta4j.core.indicators.lppl.LpplExhaustionSide;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates LPPL exhaustion scoring across the closed State Street Select
 * Sector SPDR ETF universe.
 *
 * <p>
 * The committed resources are daily Yahoo Finance bars through 2026-04-29. OHLC
 * values are scaled to adjusted close so the 2025 Select Sector SPDR share
 * splits do not create artificial LPPL discontinuities.
 */
public final class SpdrSectorLpplRotationDemo {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 4, 29);

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
     * Runs the offline SPDR LPPL rotation demo.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        List<SectorSnapshot> snapshots = analyze(LpplCalibrationProfile.defaults());
        System.out.print(renderReport(snapshots));
    }

    static List<SectorDefinition> closedUniverse() {
        return UNIVERSE;
    }

    static List<SectorSnapshot> analyze(LpplCalibrationProfile profile) {
        JsonFileBarSeriesDataSource dataSource = new JsonFileBarSeriesDataSource();
        List<InstrumentSnapshot> instruments = new ArrayList<>(UNIVERSE.size());
        for (SectorDefinition definition : UNIVERSE) {
            BarSeries series = dataSource.loadSeries(definition.resource());
            if (series == null || series.isEmpty()) {
                throw new IllegalStateException("Unable to load SPDR resource: " + definition.resource());
            }
            LpplExhaustionIndicator indicator = new LpplExhaustionIndicator(new ClosePriceIndicator(series), profile);
            instruments.add(new InstrumentSnapshot(definition.ticker(), definition.sector(),
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
            snapshots.add(
                    new SectorSnapshot(definition.sector(), definition.ticker(), sectorInstruments.size(), crashCount,
                            bubbleCount, (crashCount - bubbleCount) / total, lpplScore, lpplScore - universeAverage));
        }
        snapshots.sort(Comparator.comparingDouble(SectorSnapshot::relativeRotationScore)
                .reversed()
                .thenComparing(SectorSnapshot::sector));
        return List.copyOf(snapshots);
    }

    static String renderReport(List<SectorSnapshot> snapshots) {
        List<SectorSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        StringBuilder builder = new StringBuilder();
        builder.append(
                "date,sector,ticker,total,crash_count,bubble_count,net_exhaustion_score,standalone_lppl_score,relative_rotation_score\n");
        for (SectorSnapshot snapshot : safeSnapshots) {
            builder.append(SNAPSHOT_DATE)
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
                    .append('\n');
        }
        builder.append('\n').append(renderInterpretation(safeSnapshots));
        return builder.toString();
    }

    static String renderInterpretation(List<SectorSnapshot> snapshots) {
        List<SectorSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (safeSnapshots.isEmpty()) {
            return "Interpretation\nNo sector snapshots were available for interpretation.\n";
        }

        List<SectorSnapshot> crashSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.crashCount() > snapshot.bubbleCount())
                .sorted(Comparator.comparingDouble(SectorSnapshot::lpplScore).reversed())
                .toList();
        List<SectorSnapshot> bubbleSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.bubbleCount() > snapshot.crashCount())
                .sorted(Comparator.comparingDouble(SectorSnapshot::lpplScore))
                .toList();
        List<SectorSnapshot> neutralSignals = safeSnapshots.stream()
                .filter(snapshot -> snapshot.crashCount() == snapshot.bubbleCount())
                .sorted(Comparator.comparing(SectorSnapshot::sector))
                .toList();

        int totalInstruments = safeSnapshots.stream().mapToInt(SectorSnapshot::totalInstruments).sum();
        SectorSnapshot strongestRelative = safeSnapshots.stream()
                .max(Comparator.comparingDouble(SectorSnapshot::relativeRotationScore))
                .orElseThrow();
        SectorSnapshot weakestRelative = safeSnapshots.stream()
                .min(Comparator.comparingDouble(SectorSnapshot::relativeRotationScore))
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

        if (!neutralSignals.isEmpty()) {
            builder.append("Neutral under this profile: ")
                    .append(formatSectors(neutralSignals))
                    .append(". Their positive or negative relative score only reflects position versus the universe average.\n");
        }

        builder.append("Highest relative rotation score: ")
                .append(formatSector(strongestRelative))
                .append("; lowest relative rotation score: ")
                .append(formatSector(weakestRelative))
                .append(".\n");

        if (safeSnapshots.stream().allMatch(snapshot -> snapshot.totalInstruments() == 1)) {
            builder.append(
                    "This closed SPDR demo uses one ETF proxy per sector, so net_exhaustion_score is binary; a production constituent universe would make it a breadth percentage.\n");
        }
        return builder.toString();
    }

    private static void appendSignalSummary(StringBuilder builder, String label, List<SectorSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            builder.append(label).append(": none.\n");
            return;
        }
        builder.append(label).append(": ").append(formatSectors(snapshots)).append(".\n");
    }

    private static String formatSectors(List<SectorSnapshot> snapshots) {
        return snapshots.stream()
                .map(SpdrSectorLpplRotationDemo::formatSector)
                .reduce((left, right) -> left + "; " + right)
                .orElse("none");
    }

    private static String formatSector(SectorSnapshot snapshot) {
        return snapshot.sector() + " (" + snapshot.ticker() + ", standalone=" + format(snapshot.lpplScore())
                + ", relative=" + format(snapshot.relativeRotationScore()) + ")";
    }

    private static String label(int count, String singular) {
        return count == 1 ? singular : singular + "s";
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
        if (!instrument.exhaustion().isValid()) {
            return 0.0;
        }
        double value = instrument.exhaustion().score().doubleValue();
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    record SectorDefinition(String ticker, String sector, String resource) {
    }

    record InstrumentSnapshot(String ticker, String sector, LpplExhaustion exhaustion) {
    }

    record SectorSnapshot(String sector, String ticker, int totalInstruments, int crashCount, int bubbleCount,
            double netExhaustionScore, double lpplScore, double relativeRotationScore) {
    }
}
