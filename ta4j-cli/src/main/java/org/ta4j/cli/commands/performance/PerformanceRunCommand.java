/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.performance;

import org.ta4j.cli.performance.PerformanceExperimentRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Runs a named reusable performance experiment.
 *
 * @since 0.22.7
 */
@Command(name = "run", description = "Run a named performance experiment.", mixinStandardHelpOptions = true)
public final class PerformanceRunCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--experiment", defaultValue = "kalman-filter", paramLabel = "<id>", description = "Experiment id.")
    private String experimentId;

    @Option(names = "--bar-counts", split = ",", defaultValue = "1000,5000,10000,50000", paramLabel = "<count>", description = "Positive bar counts.")
    private List<Integer> barCounts;

    @Option(names = "--scenarios", split = ",", arity = "1..*", paramLabel = "<id>", description = "Scenario ids, or omit for experiment defaults.")
    private List<String> scenarioIds = List.of();

    @Option(names = "--repetitions", defaultValue = "5", paramLabel = "<count>", description = "Measured repetitions per cell.")
    private int repetitions;

    @Option(names = "--warmups", defaultValue = "1", paramLabel = "<count>", description = "Warmup repetitions per cell.")
    private int warmups;

    @Option(names = "--output-dir", paramLabel = "<dir>", description = "Artifact directory.")
    private Path outputDir;

    @Option(names = "--profile", description = "Emit profiler hints in performance.json.")
    private boolean profile;

    @Override
    public Integer call() throws IOException {
        Optional<Path> requestedOutputDir = outputDir == null ? Optional.empty() : Optional.of(outputDir);
        PerformanceExperimentRunner.RunRequest request = new PerformanceExperimentRunner.RunRequest(experimentId,
                barCounts, scenarioIds, repetitions, warmups, requestedOutputDir, profile);
        PerformanceExperimentRunner.RunArtifacts artifacts = PerformanceExperimentRunner.run(request);
        spec.commandLine().getOut().println("Performance experiment artifacts written to " + artifacts.outputDir());
        return 0;
    }
}
