/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.ta4j.cli.commands.indicator.IndicatorCommand;
import org.ta4j.cli.commands.forecast.ForecastCommand;
import org.ta4j.cli.commands.performance.PerformanceCommand;
import org.ta4j.cli.commands.rule.RuleCommand;
import org.ta4j.cli.commands.strategy.StrategyCommand;

import java.io.IOException;
import java.io.PrintWriter;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * First-class ta4j command-line entry point for bounded local workflows.
 *
 * <p>
 * The root command uses progressive disclosure: users first choose the primary
 * input family ({@code strategy}, {@code indicator}, {@code rule},
 * {@code forecast}, or {@code performance}), then choose the action within that
 * family.
 * </p>
 *
 * @since 0.23.1
 */
@Command(name = "ta4j-cli", description = "Run bounded ta4j workflows from local files.", mixinStandardHelpOptions = true, versionProvider = Ta4jCli.VersionProvider.class, subcommands = {
        StrategyCommand.class, IndicatorCommand.class, RuleCommand.class, ForecastCommand.class,
        PerformanceCommand.class, HelpCommand.class })
public final class Ta4jCli implements Runnable {

    static final class VersionProvider implements IVersionProvider {

        @Override
        public String[] getVersion() {
            String implementationVersion = Ta4jCli.class.getPackage().getImplementationVersion();
            return new String[] {
                    "ta4j-cli " + (implementationVersion == null ? "development" : implementationVersion) };
        }
    }

    @Spec
    private CommandSpec spec;

    /**
     * Executes the CLI.
     *
     * @param args command-line arguments
     * @since 0.23.1
     */
    public static void main(String[] args) {
        int exitCode = run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(new Ta4jCli());
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setParameterExceptionHandler(Ta4jCli::handleParameterException);
        commandLine.setExecutionExceptionHandler(Ta4jCli::handleExecutionException);
        return commandLine.execute(args);
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    private static int handleParameterException(ParameterException exception, String[] args) {
        CommandLine commandLine = exception.getCommandLine();
        PrintWriter err = commandLine.getErr();
        err.println(exception.getMessage());
        err.println();
        commandLine.usage(err);
        err.flush();
        return CommandLine.ExitCode.USAGE;
    }

    private static int handleExecutionException(Exception exception, CommandLine commandLine,
            CommandLine.ParseResult parseResult) {
        PrintWriter err = commandLine.getErr();
        err.println(exception.getMessage());
        err.flush();
        if (exception instanceof IOException) {
            return CommandLine.ExitCode.SOFTWARE;
        }
        return CommandLine.ExitCode.USAGE;
    }
}
