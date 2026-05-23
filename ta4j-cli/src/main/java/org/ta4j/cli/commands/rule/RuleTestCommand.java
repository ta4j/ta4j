/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.rule;

import org.ta4j.cli.CliCommands;

import picocli.CommandLine.Command;

/**
 * Builds a temporary strategy from one entry rule and one exit rule.
 *
 * @since 0.22.7
 */
@Command(name = "test", description = "Backtest and walk-forward entry/exit rules.", mixinStandardHelpOptions = true)
public final class RuleTestCommand extends CliCommands.RuleTestWorkflow {
}
