/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal argument parser for the bounded ta4j CLI contract.
 *
 * <p>
 * The parser intentionally supports only the option shapes the CLI advertises:
 * a command token followed by GNU-style `--option value` pairs, repeatable
 * multi-value options, and explicit unknown-option detection.
 * </p>
 */
final class CliArguments {

    private final String command;
    private final Map<String, List<String>> options;
    private final Set<String> consumedKeys = new LinkedHashSet<>();

    private CliArguments(String command, Map<String, List<String>> options) {
        this.command = command;
        this.options = options;
    }

    static CliArguments parse(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing command.");
        }

        String command = args[0];
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected positional argument: " + token);
            }

            String key = token.substring(2);
            String value = "true";
            if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                value = args[++index];
            }
            options.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return new CliArguments(command, options);
    }

    String command() {
        return command;
    }

    String require(String key) {
        return singleValue(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing required option --" + key + "."));
    }

    Optional<String> optional(String key) {
        return singleValue(key);
    }

    List<String> list(String key) {
        consumedKeys.add(key);
        return List.copyOf(options.getOrDefault(key, List.of()));
    }

    boolean flag(String key) {
        consumedKeys.add(key);
        return options.containsKey(key);
    }

    void assertNoUnknownOptions() {
        Set<String> unknown = new LinkedHashSet<>(options.keySet());
        unknown.removeAll(consumedKeys);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown option(s): " + String.join(", ", unknown));
        }
    }

    private Optional<String> singleValue(String key) {
        consumedKeys.add(key);
        List<String> values = options.get(key);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("Option --" + key + " may only be specified once.");
        }
        return Optional.of(values.getFirst());
    }
}
