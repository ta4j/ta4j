/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.strategy;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Runs one or more concrete strategies against one local dataset.
 *
 * @since 0.22.7
 */
@Command(name = "backtest", description = "Run strategies against one dataset.", mixinStandardHelpOptions = true)
public final class StrategyBacktestCommand extends CliCommands.StrategyBacktestWorkflow {
}
