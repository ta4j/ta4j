/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.forecast;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Produces forecast state or Monte Carlo return/price projections.
 *
 * @since 0.23.1
 */
@Command(name = "run", description = "Evaluate forecast state or a Monte Carlo projection.", mixinStandardHelpOptions = true)
public final class ForecastRunCommand extends CliCommands.ForecastRunWorkflow {
}
