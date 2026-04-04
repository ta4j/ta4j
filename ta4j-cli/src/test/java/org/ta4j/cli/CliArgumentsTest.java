/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CliArguments}.
 *
 * <p>
 * These tests lock down the parser contract used by the bounded CLI commands so
 * validation behavior remains deterministic as the command surface evolves.
 * </p>
 */
class CliArgumentsTest {

    @Test
    void parseRejectsMissingCommand() {
        assertThatThrownBy(() -> CliArguments.parse(new String[0])).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing command.");
    }

    @Test
    void parseRejectsUnexpectedPositionalArguments() {
        assertThatThrownBy(() -> CliArguments.parse(new String[] { "backtest", "unexpected" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected positional argument: unexpected");
    }

    @Test
    void parseRejectsEmptyOptionName() {
        assertThatThrownBy(() -> CliArguments.parse(new String[] { "backtest", "--" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Empty option name: --.");
    }

    @Test
    void parseExposesCommandRequiredOptionalListAndFlagOptions() {
        CliArguments arguments = CliArguments.parse(new String[] { "backtest", "--data-file", "bars.csv", "--criteria",
                "net-profit", "--criteria", "sharpe", "--output", "result.json", "--progress" });

        assertThat(arguments.command()).isEqualTo("backtest");
        assertThat(arguments.require("data-file")).isEqualTo("bars.csv");
        assertThat(arguments.optional("output")).hasValue("result.json");
        assertThat(arguments.optional("missing")).isEmpty();
        assertThat(arguments.list("criteria")).containsExactly("net-profit", "sharpe");
        assertThat(arguments.flag("progress")).isTrue();
        assertThat(arguments.flag("chart")).isFalse();

        arguments.assertNoUnknownOptions();
    }

    @Test
    void requireRejectsMissingOptions() {
        CliArguments arguments = CliArguments.parse(new String[] { "backtest", "--output", "result.json" });

        assertThatThrownBy(() -> arguments.require("data-file")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing required option --data-file.");
    }

    @Test
    void optionalRejectsDuplicateSingleValueOptions() {
        CliArguments arguments = CliArguments
                .parse(new String[] { "backtest", "--output", "one.json", "--output", "two.json" });

        assertThatThrownBy(() -> arguments.optional("output")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option --output may only be specified once.");
    }

    @Test
    void assertNoUnknownOptionsRejectsUnconsumedValues() {
        CliArguments arguments = CliArguments.parse(new String[] { "backtest", "--data-file", "bars.csv", "--criteria",
                "net-profit", "--unexpected", "value" });

        assertThat(arguments.list("criteria")).isEqualTo(List.of("net-profit"));
        assertThat(arguments.require("data-file")).isEqualTo("bars.csv");

        assertThatThrownBy(arguments::assertNoUnknownOptions).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown option(s): unexpected");
    }
}
