/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards {@code --timeframe} against ISO-8601 durations that are not strictly
 * positive.
 *
 * <p>
 * {@link CliSupport#parseTimeframe(String)} accepts arbitrary ISO-8601
 * durations. A zero or negative duration is passed straight into
 * {@code DurationBarAggregator}, whose aggregation loop never advances for a
 * non-positive period and spins forever allocating aggregated bars until the
 * CLI hangs or exhausts memory. A hostile or mistyped {@code --timeframe PT0S}
 * (or {@code P0D}/{@code PT-1D}) therefore hangs the CLI instead of failing
 * fast as a usage error.
 *
 * @since 0.23.1
 */
class TimeframeDurationValidationTest {

    @TempDir
    Path tempDir;

    private Path copyCsv() throws IOException {
        try (var input = getClass().getResourceAsStream("/AAPL-PT1D-20130102_20131231.csv")) {
            Path file = tempDir.resolve("data.csv");
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }

    private void assertRejected(String timeframe) throws IOException {
        Path csv = copyCsv();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(IllegalArgumentException.class,
                () -> CliSupport.loadSeries(csv.toString(), null, new ByteArrayInputStream(new byte[0]), timeframe,
                        null, null)),
                "timeframe '" + timeframe + "' must be rejected as a usage error, not hang the aggregation loop");
    }

    @Test
    void zeroDurationIsoTimeframeIsRejected() throws IOException {
        assertRejected("PT0S");
    }

    @Test
    void zeroDaysIsoTimeframeIsRejected() throws IOException {
        assertRejected("P0D");
    }

    @Test
    void negativeIsoTimeframeIsRejected() throws IOException {
        assertRejected("PT-1D");
    }

    @Test
    void positiveIsoTimeframeStillAggregates() throws IOException {
        Path csv = copyCsv();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            CliSupport.loadSeries(csv.toString(), null, new ByteArrayInputStream(new byte[0]), "P2D", null, null);
        });
    }
}
