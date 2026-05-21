/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.strategy;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Runs rolling walk-forward evaluation for one or more concrete strategies.
 *
 * @since 0.22.7
 */
@Command(name = "walk-forward", description = "Run strategy walk-forward validation.", mixinStandardHelpOptions = true)
public final class StrategyWalkForwardCommand extends CliCommands.StrategyWalkForwardCommand {
}
