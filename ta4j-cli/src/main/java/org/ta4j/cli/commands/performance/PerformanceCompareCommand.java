/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.performance;

import com.google.gson.JsonObject;
import org.ta4j.cli.performance.PerformanceComparison;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Compares two performance experiment artifact directories.
 *
 * @since 0.23.1
 */
@Command(name = "compare", description = "Compare two performance experiment runs.", mixinStandardHelpOptions = true)
public final class PerformanceCompareCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--base-dir", required = true, paramLabel = "<dir>", description = "Baseline artifact directory.")
    private Path baseDir;

    @Option(names = "--candidate-dir", required = true, paramLabel = "<dir>", description = "Candidate artifact directory.")
    private Path candidateDir;

    @Option(names = "--output-dir", required = true, paramLabel = "<dir>", description = "Comparison artifact directory.")
    private Path outputDir;

    @Option(names = "--max-regression-pct", defaultValue = "5", paramLabel = "<pct>", description = "Allowed median runtime regression percentage.")
    private double maxRegressionPct;

    @Override
    public Integer call() throws IOException {
        if (maxRegressionPct < 0d) {
            throw new IllegalArgumentException("--max-regression-pct must be non-negative");
        }
        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, maxRegressionPct);
        PerformanceComparison.ComparisonArtifacts artifacts = new PerformanceComparison.ComparisonArtifacts(outputDir,
                comparison);
        spec.commandLine().getOut().println("Performance comparison written to " + artifacts.outputDir());
        return 0;
    }
}
