/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards CSV loading against malformed rows.
 *
 * <p>
 * {@code CsvFileBarSeriesDataSource.loadCsvSeries} treats a row that fails
 * numeric or date parsing, or that has too few columns, as "no data"
 * ({@code NumberFormatException}, {@code DateTimeParseException}, and
 * {@code ArrayIndexOutOfBoundsException} are caught and the loader returns
 * {@code null}), which the CLI turns into a clean usage error ("Unable to load
 * bar data from ..."). CSV-structural failures (for example an unterminated
 * quoted field, which throws {@code CsvMalformedLineException}) are treated
 * like parse failures and also return {@code null} rather than silently
 * truncating the series at the malformed row.
 * 
 * @since 0.23.1
 */
class CsvMalformedRowTest {

    @TempDir
    Path tempDir;

    private Path writeCsv(String... rows) throws IOException {
        Path file = tempDir.resolve("data.csv");
        StringBuilder csv = new StringBuilder("date,open,high,low,close,volume\n");
        for (String row : rows) {
            csv.append(row).append('\n');
        }
        Files.writeString(file, csv);
        return file;
    }

    @Test
    void malformedDateRowIsReportedAsDataLoadFailure() throws IOException {
        Path csv = writeCsv("not-a-date,100,101,99,100.5,1000");
        assertThrows(IllegalArgumentException.class, () -> CliSupport.loadSeries(csv.toString(), null,
                new ByteArrayInputStream(new byte[0]), null, null, null));
    }

    @Test
    void shortRowIsReportedAsDataLoadFailure() throws IOException {
        Path csv = writeCsv("2026-01-01,100,101,99");
        assertThrows(IllegalArgumentException.class, () -> CliSupport.loadSeries(csv.toString(), null,
                new ByteArrayInputStream(new byte[0]), null, null, null));
    }

    @Test
    void blankLineIsReportedAsDataLoadFailure() throws IOException {
        Path csv = writeCsv("2026-01-01,100,101,99,100.5,1000", "");
        assertThrows(IllegalArgumentException.class, () -> CliSupport.loadSeries(csv.toString(), null,
                new ByteArrayInputStream(new byte[0]), null, null, null));
    }

    @Test
    void csvValidationRowIsReportedAsDataLoadFailure() throws IOException {
        Path csv = writeCsv("2026-01-01,100,\"101,99,100.5,1000");
        assertThrows(IllegalArgumentException.class, () -> CliSupport.loadSeries(csv.toString(), null,
                new ByteArrayInputStream(new byte[0]), null, null, null));
    }

    @Test
    void wellFormedCsvStillLoads() throws IOException {
        Path csv = writeCsv("2026-01-01,100,101,99,100.5,1000", "2026-01-02,101,102,100,101.5,1100");
        CliSupport.loadSeries(csv.toString(), null, new ByteArrayInputStream(new byte[0]), null, null, null);
    }
}
