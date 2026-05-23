/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.commands.rule;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Rule command group for entry/exit rule exploration.
 *
 * @since 0.22.7
 */
@Command(name = "rule", description = "Explore entry and exit rule workflows.", mixinStandardHelpOptions = true, subcommands = RuleTestCommand.class)
public final class RuleCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
