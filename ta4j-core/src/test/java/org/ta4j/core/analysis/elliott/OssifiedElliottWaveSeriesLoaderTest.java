/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.ta4j.core.BarSeries;

class OssifiedElliottWaveSeriesLoaderTest {

    @Test
    public void preservesDailyOffsetAndMissingDaysAfterSorting() {
        final long friday = Instant.parse("2020-01-03T06:30:00Z").getEpochSecond();
        final long day = Duration.ofDays(1).getSeconds();
        final BarSeries series = OssifiedElliottWaveSeriesLoader
                .parseCandles(candles(friday + 4 * day, friday, friday + 3 * day), "offset-daily");

        assertEquals("offset-daily", series.getName());
        assertEquals(3, series.getBarCount());
        assertEquals(Instant.ofEpochSecond(friday), series.getBar(0).getBeginTime());
        assertEquals(Instant.ofEpochSecond(friday + day), series.getBar(0).getEndTime());
        assertEquals(Instant.ofEpochSecond(friday + 3 * day), series.getBar(1).getBeginTime());
        assertEquals(Instant.ofEpochSecond(friday + 4 * day), series.getBar(2).getBeginTime());
        for (int index = 0; index < series.getBarCount(); index++) {
            assertEquals(Duration.ofDays(1), series.getBar(index).getTimePeriod());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 3_600, 86_399, 86_401, 129_600 })
    public void rejectsDuplicateIntradayAndNonintegralDaySpacing(final long spacing) {
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OssifiedElliottWaveSeriesLoader.parseCandles(candles(86_400 + spacing, 0, 86_400), "bad"));

        assertTrue(failure.getMessage().contains("daily candle cadence"), failure.getMessage());
        assertTrue(failure.getMessage().contains("86400 seconds"), failure.getMessage());
    }

    @Test
    public void rejectsFractionalSecondsRatherThanTruncatingThemIntoDailyCadence() {
        final String json = new String(candles(0, 86_400), StandardCharsets.UTF_8).replace("\"start\":86400",
                "\"start\":86400.5");
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OssifiedElliottWaveSeriesLoader.parseCandles(json.getBytes(StandardCharsets.UTF_8), "bad"));

        assertTrue(failure.getMessage().contains("integer epoch second"), failure.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "null", "\"not-a-number\"", "true", "[]", "[86400]", "{}", "86400.5",
            "9223372036854775808" })
    public void rejectsMalformedStartsWithDocumentedDiagnostic(final String start) {
        final String field = start == null ? "" : "\"start\":" + start + ",";
        for (final long[] starts : new long[][] { { 86_400 }, { 0, 86_400 } }) {
            final String json = new String(candles(starts), StandardCharsets.UTF_8).replace("\"start\":86400,", field);
            final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> OssifiedElliottWaveSeriesLoader.parseCandles(json.getBytes(StandardCharsets.UTF_8), "bad"));

            assertTrue(failure.getMessage().contains("daily candle start must be an integer epoch second"),
                    failure.getMessage());
        }
    }

    @Test
    public void checksAllCadenceBeforeConstructingDailyBars() {
        // Missing OHLC fields would fail bar construction if it preceded validation.
        final byte[] json = "{\"candles\":[{\"start\":0},{\"start\":86400},{\"start\":90000}]}"
                .getBytes(StandardCharsets.UTF_8);
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OssifiedElliottWaveSeriesLoader.parseCandles(json, "bad"));

        assertTrue(failure.getMessage().contains("daily candle cadence"), failure.getMessage());
    }

    @Test
    public void exploreReportsCadenceErrorBeforeEvaluationOrArtifactCreation(@TempDir final Path work)
            throws Exception {
        final Path source = work.resolve("intraday.json");
        Files.write(source, candles(0, 3_600));
        final Path recipe = work.resolve("recipe.json");
        Files.writeString(recipe, """
                {"datasetId":"toy","asset":"TOY",
                 "partitions":[{"name":"calibration","start":"2020-01-01","end":"2020-06-30"},
                               {"name":"validation","start":"2020-07-01","end":"2020-09-30"},
                               {"name":"holdout","start":"2020-10-01","end":"2020-12-31"}],
                 "forbiddenCalibrationStart":"2024-01-01",
                 "detector":{"name":"fractal-w3","factory":"fractal","params":[3]},
                 "activeRules":["wave2-origin","wave4-nonoverlap"],
                 "momentum":{"type":"RSI","barCount":14},
                 "competingModes":["3+3"],
                 "null":{"blockLengths":[10],"ensembleSize":4,"seed":7}}
                """);
        final Path output = work.resolve("run");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int code;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            code = ElliottResearch.execute(new String[] { "run", "explore", "--source", source.toString(), "--recipe",
                    recipe.toString(), "--out", output.toString() }, out, err);
        }
        final String diagnostic = stderr.toString(StandardCharsets.UTF_8);
        assertEquals(1, code, diagnostic);
        assertTrue(diagnostic.contains("source candles " + source), diagnostic);
        assertTrue(diagnostic.contains("daily candle cadence"), diagnostic);
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
        assertFalse(Files.exists(output), "invalid cadence must fail before a run is created");
    }

    private static byte[] candles(final long... starts) {
        final StringBuilder json = new StringBuilder("{\"candles\":[");
        for (int index = 0; index < starts.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"start\":")
                    .append(starts[index])
                    .append(",\"open\":10,\"high\":11,\"low\":9,\"close\":10,\"volume\":1}");
        }
        return json.append("]}").toString().getBytes(StandardCharsets.UTF_8);
    }
}
