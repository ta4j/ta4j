/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.rules.MinuteOfHourRule;
import org.ta4j.core.strategy.named.NamedStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A trading strategy that enters and exits positions based on specific minutes
 * of the hour.
 *
 * <p>
 * This strategy uses {@link MinuteOfHourRule} to determine entry and exit
 * signals based on the minute of the hour (0-59) of each bar in the series. The
 * strategy will enter a position when the bar's minute of the hour matches the
 * specified entry minute, and exit when it matches the specified exit minute.
 *
 * <p>
 * The strategy name is automatically generated as
 * {@code "MinuteOfHourStrategy_<entryMinute>_<exitMinute>"} (e.g.,
 * "MinuteOfHourStrategy_15_45").
 *
 * <p>
 * This strategy is useful for testing intraday trading patterns at a finer
 * granularity, such as specific minute-based entry/exit points within an hour.
 *
 * @since 0.19
 */
public class MinuteOfHourStrategy extends NamedStrategy {

    static {
        registerImplementation(MinuteOfHourStrategy.class);
    }

    /**
     * Constructs a new MinuteOfHourStrategy with the specified entry and exit
     * minutes.
     *
     * @param series      the bar series to analyze
     * @param entryMinute the minute of the hour (0-59) to enter positions
     * @param exitMinute  the minute of the hour (0-59) to exit positions
     * @throws IllegalArgumentException if series is null, if entryMinute or
     *                                  exitMinute is not in range 0-59, or if
     *                                  entryMinute equals exitMinute
     */
    public MinuteOfHourStrategy(BarSeries series, int entryMinute, int exitMinute) {
        super(NamedStrategy.buildLabel(MinuteOfHourStrategy.class, String.valueOf(entryMinute),
                String.valueOf(exitMinute)), new MinuteOfHourRule(new DateTimeIndicator(series), entryMinute),
                new MinuteOfHourRule(new DateTimeIndicator(series), exitMinute));
        if (entryMinute == exitMinute) {
            throw new IllegalArgumentException(
                    "Entry minute and exit minute must be different, but both were: " + entryMinute);
        }
    }

    /**
     * Constructs a new MinuteOfHourStrategy from string parameters.
     *
     * <p>
     * The parameters should be two strings representing the entry and exit minutes
     * of the hour. Valid values are integers in the range 0-59 (inclusive).
     *
     * @param series the bar series to analyze
     * @param params array containing [entryMinute, exitMinute] as strings
     * @throws IllegalArgumentException if params is null, has fewer than 2
     *                                  elements, contains invalid minute values, or
     *                                  if entryMinute equals exitMinute
     */
    public MinuteOfHourStrategy(BarSeries series, String... params) {
        this(series, parseEntryMinute(params), parseExitMinute(params));
    }

    /**
     * Builds all possible strategy permutations for all combinations of entry and
     * exit minutes.
     *
     * <p>
     * This method generates strategies for all pairs of different minutes of the
     * hour (60 * 59 = 3540 total strategies). Strategies where the entry minute
     * equals the exit minute are excluded. If any strategy construction fails, a
     * warning is logged and that strategy is skipped.
     *
     * @param series the bar series to analyze
     * @return a list of all valid MinuteOfHourStrategy permutations
     */
    public static List<Strategy> buildAllStrategyPermutations(BarSeries series) {
        List<String[]> permutations = new ArrayList<>();

        for (int entryMinute = 0; entryMinute < 60; entryMinute++) {
            for (int exitMinute = 0; exitMinute < 60; exitMinute++) {
                if (entryMinute != exitMinute) {
                    permutations.add(new String[] { String.valueOf(entryMinute), String.valueOf(exitMinute) });
                }
            }
        }

        return NamedStrategy.buildAllStrategyPermutations(series, permutations, MinuteOfHourStrategy::new,
                (params, error) -> {
                    String entry = params.length > 0 ? params[0] : "<missing>";
                    String exit = params.length > 1 ? params[1] : "<missing>";
                    LogManager.getLogger()
                            .warn("Failed to build strategy for entry minute {} and exit minute {} - {}", entry, exit,
                                    error.getMessage());
                });
    }

    private static int parseEntryMinute(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 1) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryMinute, exitMinute), but got " + params.length);
        }
        try {
            int minute = Integer.parseInt(params[0]);
            if (minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Invalid entry minute value: '" + params[0]
                        + "'. Valid values are integers in the range 0-59 (inclusive)");
            }
            return minute;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid entry minute value: '" + params[0]
                    + "'. Valid values are integers in the range 0-59 (inclusive)", e);
        }
    }

    private static int parseExitMinute(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 2) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryMinute, exitMinute), but got " + params.length);
        }
        try {
            int minute = Integer.parseInt(params[1]);
            if (minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Invalid exit minute value: '" + params[1]
                        + "'. Valid values are integers in the range 0-59 (inclusive)");
            }
            return minute;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid exit minute value: '" + params[1]
                    + "'. Valid values are integers in the range 0-59 (inclusive)", e);
        }
    }
}
