/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.performance;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Performance command group for reusable optimization experiments.
 *
 * @since 0.22.7
 */
@Command(name = "performance", description = "Run and compare performance experiments.", mixinStandardHelpOptions = true, subcommands = {
        PerformanceRunCommand.class, PerformanceCompareCommand.class })
public final class PerformanceCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
