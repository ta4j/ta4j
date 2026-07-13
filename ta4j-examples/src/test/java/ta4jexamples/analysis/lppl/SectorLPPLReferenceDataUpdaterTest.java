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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SectorLPPLReferenceDataUpdaterTest {

    private static final Instant NOW = Instant.parse("2026-07-13T19:00:00Z");
    private static final long FIRST_START = LocalDate.of(2019, 1, 2)
            .atStartOfDay(ZoneId.of("America/New_York"))
            .toEpochSecond();

    @TempDir
    Path tempDirectory;

    @Test
    void bootstrapsAMissingResourceWithCompleteAdjustedHistory() throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = definition("AAA", "missing/AAA.json");
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> fetched = history(810, 0);
        SectorLPPLReferenceDataUpdater updater = new SectorLPPLReferenceDataUpdater((ticker, start, end) -> fetched);

        SectorLPPLReferenceDataUpdater.RefreshSummary summary = updater.refresh(List.of(definition), settings(false));

        SectorLPPLReferenceDataUpdater.TickerRefresh refresh = summary.tickers().getFirst();
        assertFalse(refresh.skipped());
        assertEquals(0, refresh.existingBars());
        assertEquals(810, refresh.fetchedBars());
        assertEquals(810, refresh.mergedBars());
        assertEquals(810, refresh.addedBars());
        assertTrue(Files.exists(refresh.outputPath()));
        assertEquals(fetched, SectorLPPLReferenceDataUpdater.readReferenceBars(refresh.outputPath(), "unused"));
    }

    @Test
    void replacesAdjustedHistoryAndCountsAddedAndRevisedBars() throws IOException {
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> existing = List.of(bar(100, "10"), bar(200, "11"));
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> fetched = List.of(bar(100, "10"), bar(200, "11.5"),
                bar(300, "12"));

        SectorLPPLReferenceDataUpdater.MergeResult result = SectorLPPLReferenceDataUpdater
                .replaceAdjustedHistory(existing, fetched);

        assertEquals(3, result.bars().size());
        assertEquals(1, result.addedBars());
        assertEquals(1, result.revisedBars());
        assertTrue(result.hasChanges());
    }

    @Test
    void rejectsIncompleteReplacementHistory() {
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> existing = List.of(bar(100, "10"), bar(200, "11"));
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> partial = List.of(bar(200, "11"));

        IOException failure = assertThrows(IOException.class,
                () -> SectorLPPLReferenceDataUpdater.replaceAdjustedHistory(existing, partial));

        assertTrue(failure.getMessage().contains("complete adjusted history"));
    }

    @Test
    void requiresACommonFinalSessionAcrossAllLenses() {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> universe = List.of(definition("AAA", "missing/AAA.json"),
                definition("BBB", "missing/BBB.json"));
        SectorLPPLReferenceDataUpdater updater = new SectorLPPLReferenceDataUpdater(
                (ticker, start, end) -> ticker.equals("AAA") ? history(810, 0) : history(810, 1));

        IOException failure = assertThrows(IOException.class, () -> updater.refresh(universe, settings(false)));

        assertTrue(failure.getMessage().contains("common final session"));
    }

    @Test
    void requiresACommonFinalSessionWhenOneLensFallsBackToExistingData() throws IOException {
        List<SectorLPPLExhaustionMapDemo.InstrumentDefinition> universe = List.of(definition("AAA", "nested/AAA.json"),
                definition("BBB", "nested/BBB.json"));
        Path resources = tempDirectory.resolve("resources");
        SectorLPPLReferenceDataUpdater.writeReferenceBars(resources.resolve("nested/AAA.json"), history(810, 0));
        SectorLPPLReferenceDataUpdater.writeReferenceBars(resources.resolve("nested/BBB.json"), history(810, 0));
        SectorLPPLReferenceDataUpdater updater = new SectorLPPLReferenceDataUpdater((ticker, start, end) -> {
            if (ticker.equals("AAA")) {
                return history(811, 0);
            }
            throw new IOException("simulated provider failure");
        });

        IOException failure = assertThrows(IOException.class, () -> updater.refresh(universe, settings(false)));

        assertTrue(failure.getMessage().contains("common final session"));
    }

    @Test
    void refusesShortHistoryAndDoesNotPromotePartialRefresh() throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = definition("AAA", "missing/AAA.json");
        SectorLPPLReferenceDataUpdater updater = new SectorLPPLReferenceDataUpdater(
                (ticker, start, end) -> history(809, 0));

        SectorLPPLReferenceDataUpdater.RefreshSummary summary = updater.refresh(List.of(definition), settings(true));

        assertTrue(summary.tickers().getFirst().skipped());
        assertTrue(summary.tickers().getFirst().message().contains("810"));
        assertFalse(Files.exists(tempDirectory.resolve("resources/missing/AAA.json")));
    }

    @Test
    void updateResourcesPromotesTheValidatedSnapshot() throws IOException {
        SectorLPPLExhaustionMapDemo.InstrumentDefinition definition = definition("AAA", "nested/AAA.json");
        SectorLPPLReferenceDataUpdater updater = new SectorLPPLReferenceDataUpdater(
                (ticker, start, end) -> history(810, 0));

        SectorLPPLReferenceDataUpdater.RefreshSummary summary = updater.refresh(List.of(definition), settings(true));

        Path target = tempDirectory.resolve("resources/nested/AAA.json");
        assertEquals(tempDirectory.resolve("resources").toAbsolutePath().normalize(), summary.analysisDataDirectory());
        assertTrue(Files.exists(target));
        assertEquals(target, summary.tickers().getFirst().outputPath());
    }

    @Test
    void compactWriterUsesStableCandleFieldOrder() throws IOException {
        Path output = tempDirectory.resolve("bars.json");

        SectorLPPLReferenceDataUpdater.writeReferenceBars(output, List.of(bar(100, "10"), bar(200, "11")));

        String json = Files.readString(output);
        assertEquals(1, json.lines().count());
        assertTrue(json.contains(
                "{\"start\":\"100\",\"low\":\"10\",\"high\":\"10\",\"open\":\"10\",\"close\":\"10\",\"volume\":\"100\"}"));
        assertTrue(json.indexOf("\"start\"") < json.indexOf("\"low\""));
        assertTrue(json.indexOf("\"low\"") < json.indexOf("\"high\""));
        assertTrue(json.indexOf("\"high\"") < json.indexOf("\"open\""));
        assertTrue(json.indexOf("\"open\"") < json.indexOf("\"close\""));
        assertTrue(json.indexOf("\"close\"") < json.indexOf("\"volume\""));
    }

    @Test
    void validatesReferenceCandleInvariants() throws IOException {
        assertThrows(IOException.class, () -> SectorLPPLReferenceDataUpdater.parseCoinbaseStyleReferenceBars("{}"));
        String duplicate = bars(bar(200, "11"), bar(200, "12"));
        String outOfOrder = bars(bar(200, "11"), bar(100, "10"));
        String nonPositive = """
                {"candles":[{"start":"100","open":"1","high":"1","low":"1","close":"0","volume":"10"}]}
                """;
        String negativeVolume = """
                {"candles":[{"start":"100","open":"1","high":"1","low":"1","close":"1","volume":"-1"}]}
                """;

        assertThrows(IOException.class,
                () -> SectorLPPLReferenceDataUpdater.parseCoinbaseStyleReferenceBars(duplicate));
        assertThrows(IOException.class,
                () -> SectorLPPLReferenceDataUpdater.parseCoinbaseStyleReferenceBars(outOfOrder));
        assertThrows(IllegalArgumentException.class,
                () -> SectorLPPLReferenceDataUpdater.parseCoinbaseStyleReferenceBars(nonPositive));
        assertThrows(IllegalArgumentException.class,
                () -> SectorLPPLReferenceDataUpdater.parseCoinbaseStyleReferenceBars(negativeVolume));
    }

    @Test
    void discardsAnUnsettledCurrentSession() throws IOException {
        Instant beforeSettlement = Instant.parse("2026-07-13T20:00:00Z");
        long currentSession = LocalDate.of(2026, 7, 13).atStartOfDay(ZoneId.of("America/New_York")).toEpochSecond();
        SectorLPPLReferenceDataUpdater.ReferenceBar bar = bar(currentSession, "10");

        assertFalse(bar.isCompleteFor(beforeSettlement));
        assertTrue(bar.isCompleteFor(Instant.parse("2026-07-13T22:01:00Z")));
    }

    private SectorLPPLReferenceDataUpdater.Settings settings(boolean updateResources) {
        Path resources = tempDirectory.resolve("resources");
        Path output = tempDirectory.resolve("output");
        return new SectorLPPLReferenceDataUpdater.Settings(resources, output, output.resolve("responses"),
                updateResources, NOW);
    }

    private static SectorLPPLExhaustionMapDemo.InstrumentDefinition definition(String ticker, String resource) {
        return new SectorLPPLExhaustionMapDemo.InstrumentDefinition(SectorLPPLExhaustionMapDemo.CoverageType.SECTOR,
                "Test", ticker, SectorLPPLExhaustionMapDemo.Lens.PRIMARY,
                SectorLPPLExhaustionMapDemo.Weighting.CAP_WEIGHTED, "TEST", LocalDate.of(2019, 1, 2), resource);
    }

    private static List<SectorLPPLReferenceDataUpdater.ReferenceBar> history(int count, int finalDayShift) {
        List<SectorLPPLReferenceDataUpdater.ReferenceBar> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bars.add(bar(FIRST_START + (long) (i + finalDayShift) * 86_400L, Integer.toString(10 + i)));
        }
        return List.copyOf(bars);
    }

    private static SectorLPPLReferenceDataUpdater.ReferenceBar bar(long start, String value) {
        BigDecimal decimal = new BigDecimal(value);
        return new SectorLPPLReferenceDataUpdater.ReferenceBar(start, decimal, decimal, decimal, decimal,
                BigDecimal.valueOf(100));
    }

    private static String bars(SectorLPPLReferenceDataUpdater.ReferenceBar... bars) {
        StringBuilder builder = new StringBuilder("{\"candles\":[");
        for (int i = 0; i < bars.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            SectorLPPLReferenceDataUpdater.ReferenceBar bar = bars[i];
            builder.append("{\"start\":\"")
                    .append(bar.startEpochSecond())
                    .append("\",\"open\":\"")
                    .append(bar.open())
                    .append("\",\"high\":\"")
                    .append(bar.high())
                    .append("\",\"low\":\"")
                    .append(bar.low())
                    .append("\",\"close\":\"")
                    .append(bar.close())
                    .append("\",\"volume\":\"")
                    .append(bar.volume())
                    .append("\"}");
        }
        return builder.append("]}").toString();
    }
}
