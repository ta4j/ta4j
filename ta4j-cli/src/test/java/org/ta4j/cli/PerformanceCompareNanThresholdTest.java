/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.cli.performance.PerformanceComparison;

/**
 * Guards the performance comparison regression threshold against {@code NaN}.
 *
 * <p>
 * The CLI accepts {@code --max-regression-pct=NaN} (the check is
 * {@code maxRegressionPct < 0d}, which {@code NaN} does not satisfy) and
 * {@link PerformanceComparison#compare} then never flags a regression because
 * every {@code medianDeltaPct > NaN} comparison is {@code false}. A hostile or
 * mistyped threshold silently disables the regression gate instead of failing
 * as a usage error.
 *
 * @since 0.23.1
 */
class PerformanceCompareNanThresholdTest {

    @TempDir
    Path tempDir;

    private void writeArtifact(Path dir, long medianNanos) throws Exception {
        Files.createDirectories(dir);
        String json = """
                {
                  "schemaVersion": 1,
                  "experimentId": "kalman-filter",
                  "gitRef": "base",
                  "repetitions": 1,
                  "warmups": 0,
                  "barCounts": [100],
                  "scenarioIds": ["sequential"],
                  "results": [
                    {
                      "scenarioId": "sequential",
                      "barCount": 100,
                      "checksum": 1,
                      "checksumStable": true,
                      "stats": {
                        "minNanos": %d,
                        "maxNanos": %d,
                        "averageNanos": %d,
                        "medianNanos": %d,
                        "p90Nanos": %d,
                        "totalOperations": 100,
                        "totalDurationNanos": %d,
                        "operationsPerSecond": 1000.0
                      },
                      "measurements": []
                    }
                  ]
                }
                """.formatted(medianNanos, medianNanos, medianNanos, medianNanos, medianNanos, medianNanos);
        Files.writeString(dir.resolve("performance.json"), json);
    }

    @Test
    void nanRegressionThresholdIsRejectedAsUsageError() throws Exception {
        Path base = tempDir.resolve("base");
        Path candidate = tempDir.resolve("candidate");
        Path output = tempDir.resolve("out");
        writeArtifact(base, 100L);
        writeArtifact(candidate, 200L);

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int code = Ta4jCli.run(
                new String[] { "performance", "compare", "--base-dir", base.toString(), "--candidate-dir",
                        candidate.toString(), "--output-dir", output.toString(), "--max-regression-pct=NaN" },
                new ByteArrayInputStream(new byte[0]), new PrintWriter(outBytes, true, StandardCharsets.UTF_8),
                new PrintWriter(errBytes, true, StandardCharsets.UTF_8));
        assertEquals(2, code, "NaN regression threshold must be a usage error, not silently disable the gate");
    }

    @Test
    void compareRejectsNanThresholdDirectly() throws Exception {
        Path base = tempDir.resolve("base2");
        Path candidate = tempDir.resolve("candidate2");
        Path output = tempDir.resolve("out2");
        writeArtifact(base, 100L);
        writeArtifact(candidate, 200L);
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(base, candidate, output, Double.NaN));
    }

    @Test
    void finiteThresholdStillEvaluatesTheGate() throws Exception {
        Path base = tempDir.resolve("base3");
        Path candidate = tempDir.resolve("candidate3");
        Path output = tempDir.resolve("out3");
        writeArtifact(base, 100L);
        writeArtifact(candidate, 200L);
        var comparison = PerformanceComparison.compare(base, candidate, output, 5d);
        assertEquals(false, comparison.get("regressionWithinThreshold").getAsBoolean());
    }
}
