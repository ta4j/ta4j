/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpdrSectorReferenceDataUpdaterTest {

    private static final Instant NOW = Instant.parse("2026-05-06T12:00:00Z");
    private static final String RESOURCE = "YahooFinance-XLI-PT1D-20240102_20260710.json";
    private static final String SECOND_RESOURCE = "YahooFinance-XLF-PT1D-20240102_20260710.json";

    @TempDir
    Path tempDirectory;

    @Test
    void appendsOnlyMissingBarsAndWritesChronologicalOutput() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10"), bar(200, "11")));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater(
                (ticker, start, end) -> List.of(bar(300, "12")));

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(universe(),
                settings(referenceDirectory, false));

        SpdrSectorReferenceDataUpdater.TickerRefresh refresh = summary.tickers().get(0);
        assertEquals(1, refresh.addedBars());
        assertEquals(0, refresh.revisedBars());
        assertEquals(3, refresh.mergedBars());
        String json = Files.readString(refresh.outputPath());
        assertTrue(json.indexOf("\"start\": \"100\"") < json.indexOf("\"start\": \"200\""));
        assertTrue(json.indexOf("\"start\": \"200\"") < json.indexOf("\"start\": \"300\""));
    }

    @Test
    void replacesOverlappingCorrectedBars() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10"), bar(200, "11")));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater(
                (ticker, start, end) -> List.of(bar(200, "11.5")));

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(universe(),
                settings(referenceDirectory, false));

        SpdrSectorReferenceDataUpdater.TickerRefresh refresh = summary.tickers().get(0);
        assertEquals(0, refresh.addedBars());
        assertEquals(1, refresh.revisedBars());
        assertTrue(Files.readString(refresh.outputPath()).contains("\"close\": \"11.5\""));
    }

    @Test
    void rejectsMalformedAndNonPositiveReferenceBars() {
        assertThrows(IOException.class, () -> SpdrSectorReferenceDataUpdater.parseCoinbaseStyleReferenceBars("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> SpdrSectorReferenceDataUpdater.parseCoinbaseStyleReferenceBars(bars(bar(100, "0"))));
    }

    @Test
    void rejectsDuplicateOrOutOfOrderReferenceCandles() throws IOException {
        String duplicate = bars(bar(200, "11"), bar(200, "12"));
        String outOfOrder = bars(bar(200, "11"), bar(100, "10"));

        IOException duplicateFailure = assertThrows(IOException.class,
                () -> SpdrSectorReferenceDataUpdater.parseCoinbaseStyleReferenceBars(duplicate));
        IOException orderFailure = assertThrows(IOException.class,
                () -> SpdrSectorReferenceDataUpdater.parseCoinbaseStyleReferenceBars(outOfOrder));

        assertTrue(duplicateFailure.getMessage().contains("strictly increasing"));
        assertTrue(orderFailure.getMessage().contains("strictly increasing"));
    }

    @Test
    void skipsIncompleteCurrentDayBars() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10")));
        Instant beforeSettlement = Instant.parse("2026-05-06T19:00:00Z");
        long currentDayStart = Instant.parse("2026-05-06T13:30:00Z").getEpochSecond();
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater(
                (ticker, start, end) -> List.of(bar(currentDayStart, "12")));

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(universe(),
                settings(referenceDirectory, false, beforeSettlement));

        assertEquals(0, summary.tickers().get(0).addedBars());
        assertFalse(
                Files.readString(summary.tickers().get(0).outputPath()).contains("\"start\": \"" + currentDayStart));
    }

    @Test
    void handlesYahooFailuresWithoutCorruptingTrackedReferenceData() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10")));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater((ticker, start, end) -> {
            throw new IOException("network unavailable");
        });

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(universe(),
                settings(referenceDirectory, true));

        SpdrSectorReferenceDataUpdater.TickerRefresh refresh = summary.tickers().get(0);
        assertTrue(refresh.skipped());
        assertEquals(0, refresh.addedBars());
        assertEquals(bars(bar(100, "10")), Files.readString(referenceDirectory.resolve(RESOURCE)));
    }

    @Test
    void promotesCommittedResourcesOnlyAfterACompleteRefresh() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10")));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater(
                (ticker, start, end) -> List.of(bar(300, "12")));

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(universe(),
                settings(referenceDirectory, true));

        Path trackedResource = referenceDirectory.resolve(RESOURCE);
        assertEquals(trackedResource, summary.tickers().get(0).outputPath());
        assertEquals(referenceDirectory.toAbsolutePath().normalize(), summary.analysisDataDirectory());
        assertTrue(Files.readString(trackedResource).contains("\"start\": \"300\""));
    }

    @Test
    void rejectsMismatchedFinalSessionsBeforeChangingCommittedResources() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10")));
        Files.writeString(referenceDirectory.resolve(SECOND_RESOURCE), bars(bar(100, "20")));
        String originalXli = Files.readString(referenceDirectory.resolve(RESOURCE));
        String originalXlf = Files.readString(referenceDirectory.resolve(SECOND_RESOURCE));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater((ticker, start,
                end) -> ticker.equals("XLI") ? List.of(bar(86_400, "12")) : List.of(bar(172_800, "22")));

        IOException failure = assertThrows(IOException.class,
                () -> updater.refresh(twoInstrumentUniverse(), settings(referenceDirectory, true)));

        assertTrue(failure.getMessage().contains("common final session"));
        assertEquals(originalXli, Files.readString(referenceDirectory.resolve(RESOURCE)));
        assertEquals(originalXlf, Files.readString(referenceDirectory.resolve(SECOND_RESOURCE)));
    }

    @Test
    void preservesPartialRefreshDetailsWithoutPromotingMismatchedResources() throws IOException {
        Path referenceDirectory = seedReference("XLI", bars(bar(100, "10")));
        Files.writeString(referenceDirectory.resolve(SECOND_RESOURCE), bars(bar(100, "20")));
        String originalXli = Files.readString(referenceDirectory.resolve(RESOURCE));
        String originalXlf = Files.readString(referenceDirectory.resolve(SECOND_RESOURCE));
        SpdrSectorReferenceDataUpdater updater = new SpdrSectorReferenceDataUpdater((ticker, start, end) -> {
            if (ticker.equals("XLF")) {
                throw new IOException("network unavailable");
            }
            return List.of(bar(86_400, "12"));
        });

        SpdrSectorReferenceDataUpdater.RefreshSummary summary = updater.refresh(twoInstrumentUniverse(),
                settings(referenceDirectory, true));

        assertFalse(summary.tickers().get(0).skipped());
        assertTrue(summary.tickers().get(1).skipped());
        assertEquals(originalXli, Files.readString(referenceDirectory.resolve(RESOURCE)));
        assertEquals(originalXlf, Files.readString(referenceDirectory.resolve(SECOND_RESOURCE)));
    }

    @Test
    void parsesYahooAdjustedCloseAndScalesOhlc() throws IOException {
        String yahooJson = """
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1000],
                      "indicators": {
                        "quote": [{
                          "open": [100.0],
                          "high": [110.0],
                          "low": [90.0],
                          "close": [100.0],
                          "volume": [1234]
                        }],
                        "adjclose": [{
                          "adjclose": [50.0]
                        }]
                      }
                    }]
                  }
                }
                """;

        List<SpdrSectorReferenceDataUpdater.ReferenceBar> bars = SpdrSectorReferenceDataUpdater.YahooFinanceAdjustedDailyBarFetcher
                .parseYahooAdjustedBars(yahooJson, "XLI");

        assertEquals(0, new BigDecimal("50").compareTo(bars.get(0).close()));
        assertEquals(0, new BigDecimal("55").compareTo(bars.get(0).high()));
        assertEquals(0, new BigDecimal("45").compareTo(bars.get(0).low()));
    }

    private Path seedReference(String ticker, String json) throws IOException {
        Path referenceDirectory = tempDirectory.resolve("resources");
        Files.createDirectories(referenceDirectory);
        Files.writeString(referenceDirectory.resolve("YahooFinance-" + ticker + "-PT1D-20240102_20260710.json"), json);
        return referenceDirectory;
    }

    private SpdrSectorReferenceDataUpdater.Settings settings(Path referenceDirectory, boolean updateReferenceData) {
        return settings(referenceDirectory, updateReferenceData, NOW);
    }

    private SpdrSectorReferenceDataUpdater.Settings settings(Path referenceDirectory, boolean updateReferenceData,
            Instant now) {
        return new SpdrSectorReferenceDataUpdater.Settings(referenceDirectory, tempDirectory.resolve("analysis"),
                tempDirectory.resolve("analysis").resolve("responses"), updateReferenceData, 7, now);
    }

    private List<SpdrSectorLPPLRotationDemo.SectorDefinition> universe() {
        return List.of(new SpdrSectorLPPLRotationDemo.SectorDefinition("XLI", "Industrials", RESOURCE));
    }

    private List<SpdrSectorLPPLRotationDemo.SectorDefinition> twoInstrumentUniverse() {
        return List.of(new SpdrSectorLPPLRotationDemo.SectorDefinition("XLI", "Industrials", RESOURCE),
                new SpdrSectorLPPLRotationDemo.SectorDefinition("XLF", "Financials", SECOND_RESOURCE));
    }

    private static String bars(SpdrSectorReferenceDataUpdater.ReferenceBar... bars) throws IOException {
        Path tempFile = Files.createTempFile("spdr-bars", ".json");
        try {
            SpdrSectorReferenceDataUpdater.writeReferenceBars(tempFile, List.of(bars));
            return Files.readString(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static SpdrSectorReferenceDataUpdater.ReferenceBar bar(long start, String close) {
        BigDecimal price = new BigDecimal(close);
        return new SpdrSectorReferenceDataUpdater.ReferenceBar(start, price, price, price, price, BigDecimal.TEN);
    }
}
