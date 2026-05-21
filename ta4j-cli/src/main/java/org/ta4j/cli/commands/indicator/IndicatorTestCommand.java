/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.indicator;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Builds a temporary threshold or crossover strategy from a serialized numeric
 * indicator.
 *
 * @since 0.22.7
 */
@Command(name = "test", description = "Backtest a serialized indicator idea.", mixinStandardHelpOptions = true)
public final class IndicatorTestCommand extends CliCommands.IndicatorTestCommand {
}
