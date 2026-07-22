/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.forecast;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Forecast state and projection command group.
 *
 * @since 0.23.1
 */
@Command(name = "forecast", description = "Inspect return state and produce deterministic forecasts.", mixinStandardHelpOptions = true, subcommands = ForecastRunCommand.class)
public final class ForecastCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
