/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.indicator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Indicator command group for exploratory indicator workflows.
 *
 * @since 0.22.7
 */
@Command(name = "indicator", description = "Explore serialized indicator workflows.", mixinStandardHelpOptions = true, subcommands = IndicatorTestCommand.class)
public final class IndicatorCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
