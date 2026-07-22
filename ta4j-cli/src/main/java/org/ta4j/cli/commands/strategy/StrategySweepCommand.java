/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.strategy;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Ranks bounded SMA crossover strategy candidates from a parameter grid.
 *
 * @since 0.23.1
 */
@Command(name = "sweep", description = "Rank bounded SMA crossover strategy candidates.", mixinStandardHelpOptions = true)
public final class StrategySweepCommand extends CliCommands.StrategySweepWorkflow {
}
