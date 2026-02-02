/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.rules.HourOfDayRule;
import org.ta4j.core.strategy.named.NamedStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A trading strategy that enters and exits positions based on specific hours of
 * the day.
 *
 * <p>
 * This strategy uses {@link HourOfDayRule} to determine entry and exit signals
 * based on the hour of the day (0-23) of each bar in the series. The strategy
 * will enter a position when the bar's hour of the day matches the specified
 * entry hour, and exit when it matches the specified exit hour.
 *
 * <p>
 * The strategy name is automatically generated as
 * {@code "HourOfDayStrategy_<entryHour>_<exitHour>"} (e.g.,
 * "HourOfDayStrategy_9_17").
 *
 * <p>
 * This strategy is useful for testing intraday trading patterns, such as market
 * open/close effects or specific trading hours in different time zones.
 *
 * @since 0.19
 */
public class HourOfDayStrategy extends NamedStrategy {

    static {
        registerImplementation(HourOfDayStrategy.class);
    }

    /**
     * Constructs a new HourOfDayStrategy with the specified entry and exit hours.
     *
     * @param series    the bar series to analyze
     * @param entryHour the hour of the day (0-23) to enter positions
     * @param exitHour  the hour of the day (0-23) to exit positions
     * @throws IllegalArgumentException if series is null, if entryHour or exitHour
     *                                  is not in range 0-23, or if entryHour equals
     *                                  exitHour
     */
    public HourOfDayStrategy(BarSeries series, int entryHour, int exitHour) {
        super(NamedStrategy.buildLabel(HourOfDayStrategy.class, String.valueOf(entryHour), String.valueOf(exitHour)),
                new HourOfDayRule(new DateTimeIndicator(series), entryHour),
                new HourOfDayRule(new DateTimeIndicator(series), exitHour));
        if (entryHour == exitHour) {
            throw new IllegalArgumentException(
                    "Entry hour and exit hour must be different, but both were: " + entryHour);
        }
    }

    /**
     * Constructs a new HourOfDayStrategy from string parameters.
     *
     * <p>
     * The parameters should be two strings representing the entry and exit hours of
     * the day. Valid values are integers in the range 0-23 (inclusive).
     *
     * @param series the bar series to analyze
     * @param params array containing [entryHour, exitHour] as strings
     * @throws IllegalArgumentException if params is null, has fewer than 2
     *                                  elements, contains invalid hour values, or
     *                                  if entryHour equals exitHour
     */
    public HourOfDayStrategy(BarSeries series, String... params) {
        this(series, parseEntryHour(params), parseExitHour(params));
    }

    /**
     * Builds all possible strategy permutations for all combinations of entry and
     * exit hours.
     *
     * <p>
     * This method generates strategies for all pairs of different hours of the day
     * (24 * 23 = 552 total strategies). Strategies where the entry hour equals the
     * exit hour are excluded. If any strategy construction fails, a warning is
     * logged and that strategy is skipped.
     *
     * @param series the bar series to analyze
     * @return a list of all valid HourOfDayStrategy permutations
     */
    public static List<Strategy> buildAllStrategyPermutations(BarSeries series) {
        List<String[]> permutations = new ArrayList<>();

        for (int entryHour = 0; entryHour < 24; entryHour++) {
            for (int exitHour = 0; exitHour < 24; exitHour++) {
                if (entryHour != exitHour) {
                    permutations.add(new String[] { String.valueOf(entryHour), String.valueOf(exitHour) });
                }
            }
        }

        return NamedStrategy.buildAllStrategyPermutations(series, permutations, HourOfDayStrategy::new,
                (params, error) -> {
                    String entry = params.length > 0 ? params[0] : "<missing>";
                    String exit = params.length > 1 ? params[1] : "<missing>";
                    LogManager.getLogger()
                            .warn("Failed to build strategy for entry hour {} and exit hour {} - {}", entry, exit,
                                    error.getMessage());
                });
    }

    private static int parseEntryHour(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 1) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryHour, exitHour), but got " + params.length);
        }
        try {
            int hour = Integer.parseInt(params[0]);
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Invalid entry hour value: '" + params[0]
                        + "'. Valid values are integers in the range 0-23 (inclusive)");
            }
            return hour;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid entry hour value: '" + params[0]
                    + "'. Valid values are integers in the range 0-23 (inclusive)", e);
        }
    }

    private static int parseExitHour(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 2) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryHour, exitHour), but got " + params.length);
        }
        try {
            int hour = Integer.parseInt(params[1]);
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Invalid exit hour value: '" + params[1]
                        + "'. Valid values are integers in the range 0-23 (inclusive)");
            }
            return hour;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid exit hour value: '" + params[1]
                    + "'. Valid values are integers in the range 0-23 (inclusive)", e);
        }
    }
}
