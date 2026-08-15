/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ScopeType;
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
        CliCommands.StrategyCommand.class, CliCommands.IndicatorCommand.class, CliCommands.RuleCommand.class,
        CliCommands.ForecastCommand.class, CliCommands.PerformanceCommand.class, CliCommands.CatalogCommand.class,
        CliCommands.CompletionCommand.class, HelpCommand.class })
public final class Ta4jCli implements Runnable {

    enum ErrorFormat {
        TEXT, JSON
    }

    private static final int IO_ERROR_EXIT_CODE = 74;

    private final InputStream input;

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

    @Option(names = "--error-format", defaultValue = "TEXT", scope = ScopeType.INHERIT, paramLabel = "<format>", description = "Error output: text or json.")
    private ErrorFormat errorFormat;

    /**
     * Creates a CLI using standard input.
     *
     * @since 0.23.1
     */
    public Ta4jCli() {
        this(System.in);
    }

    Ta4jCli(InputStream input) {
        this.input = input;
    }

    /**
     * Executes the CLI.
     *
     * @param args command-line arguments
     * @since 0.23.1
     */
    public static void main(String[] args) {
        int exitCode = run(args, new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        return run(args, System.in, out, err);
    }

    static int run(String[] args, InputStream input, PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(new Ta4jCli(input));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setParameterExceptionHandler(Ta4jCli::handleParameterException);
        commandLine.setExecutionExceptionHandler(Ta4jCli::handleExecutionException);
        return commandLine.execute(args);
    }

    InputStream input() {
        return input;
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    private static int handleParameterException(ParameterException exception, String[] args) {
        CommandLine commandLine = exception.getCommandLine();
        PrintWriter err = commandLine.getErr();
        Ta4jCli root = rootCommand(commandLine);
        if (root.errorFormat == ErrorFormat.JSON) {
            err.println(CliSupport
                    .toJson(errorPayload(commandLine, "usage", CommandLine.ExitCode.USAGE, exception.getMessage())));
        } else {
            err.println(exception.getMessage());
            err.println();
            commandLine.usage(err);
        }
        err.flush();
        return CommandLine.ExitCode.USAGE;
    }

    private static int handleExecutionException(Exception exception, CommandLine commandLine,
            CommandLine.ParseResult parseResult) {
        int exitCode;
        String category;
        if (exception instanceof IllegalArgumentException) {
            exitCode = CommandLine.ExitCode.USAGE;
            category = "usage";
        } else if (exception instanceof IOException || exception instanceof UncheckedIOException) {
            exitCode = IO_ERROR_EXIT_CODE;
            category = "io";
        } else {
            exitCode = CommandLine.ExitCode.SOFTWARE;
            category = "software";
        }
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        PrintWriter err = commandLine.getErr();
        Ta4jCli root = rootCommand(commandLine);
        if (root.errorFormat == ErrorFormat.JSON) {
            err.println(CliSupport.toJson(errorPayload(commandLine, category, exitCode, message)));
        } else {
            err.println(message);
        }
        err.flush();
        return exitCode;
    }

    private static Ta4jCli rootCommand(CommandLine commandLine) {
        return (Ta4jCli) commandLine.getCommandSpec().root().userObject();
    }

    private static Map<String, Object> errorPayload(CommandLine commandLine, String category, int exitCode,
            String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", category);
        error.put("exitCode", exitCode);
        error.put("message", message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", CliSupport.SCHEMA_VERSION);
        payload.put("status", "error");
        payload.put("command", commandLine.getCommandSpec().qualifiedName());
        payload.put("error", error);
        return payload;
    }
}
