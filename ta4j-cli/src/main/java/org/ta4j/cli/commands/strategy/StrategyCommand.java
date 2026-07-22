/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.strategy;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Strategy command group for backtests, walk-forward checks, and strategy
 * parameter sweeps.
 *
 * @since 0.23.1
 */
@Command(name = "strategy", description = "Run and tune strategy workflows.", mixinStandardHelpOptions = true, subcommands = {
        StrategyBacktestCommand.class, StrategyWalkForwardCommand.class, StrategySweepCommand.class })
public final class StrategyCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
