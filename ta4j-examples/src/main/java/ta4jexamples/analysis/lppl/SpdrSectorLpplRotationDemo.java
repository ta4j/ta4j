/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOG = LogManager.getLogger(SpdrSectorLpplRotationDemo.class);
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
        LOG.info("SPDR sector LPPL rotation snapshot: {}", SNAPSHOT_DATE);
        LOG.info("Positive = crash exhaustion, negative = bubble exhaustion");
        LOG.info(
                "sector,ticker,total,crash_count,bubble_count,net_exhaustion_score,lppl_score,relative_rotation_score");
        for (SectorSnapshot snapshot : snapshots) {
            LOG.info("{},{},{},{},{},{},{},{}", snapshot.sector(), snapshot.ticker(), snapshot.totalInstruments(),
                    snapshot.crashCount(), snapshot.bubbleCount(), format(snapshot.netExhaustionScore()),
                    format(snapshot.lpplScore()), format(snapshot.relativeRotationScore()));
        }
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
                .mapToDouble(instrument -> instrument.exhaustion().score().doubleValue())
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
                    .filter(instrument -> instrument.exhaustion().side() == LpplExhaustionSide.CRASH_EXHAUSTION)
                    .count();
            int bubbleCount = (int) sectorInstruments.stream()
                    .filter(instrument -> instrument.exhaustion().side() == LpplExhaustionSide.BUBBLE_EXHAUSTION)
                    .count();
            double lpplScore = sectorInstruments.stream()
                    .mapToDouble(instrument -> instrument.exhaustion().score().doubleValue())
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
