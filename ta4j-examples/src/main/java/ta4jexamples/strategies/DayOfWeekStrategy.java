/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.rules.DayOfWeekRule;
import org.ta4j.core.strategy.named.NamedStrategy;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * A trading strategy that enters and exits positions based on specific days of
 * the week.
 *
 * <p>
 * This strategy uses {@link DayOfWeekRule} to determine entry and exit signals
 * based on the day of the week of each bar in the series. The strategy will
 * enter a position when the bar's day of the week matches the specified entry
 * day, and exit when it matches the specified exit day.
 *
 * <p>
 * The strategy name is automatically generated as
 * {@code "DayOfWeekStrategy_<entryDay>_<exitDay>"} (e.g.,
 * "DayOfWeekStrategy_MONDAY_FRIDAY").
 *
 * <p>
 * This strategy is useful for testing day-of-week effects in trading, such as
 * the "Monday effect" or "Friday effect" observed in some markets.
 *
 * @since 0.19
 */
public class DayOfWeekStrategy extends NamedStrategy {

    static {
        registerImplementation(DayOfWeekStrategy.class);
    }

    /**
     * Constructs a new DayOfWeekStrategy with the specified entry and exit days.
     *
     * @param series         the bar series to analyze
     * @param entryDayOfWeek the day of the week to enter positions
     * @param exitDayOfWeek  the day of the week to exit positions
     * @throws IllegalArgumentException if series is null or if entryDayOfWeek
     *                                  equals exitDayOfWeek
     */
    public DayOfWeekStrategy(BarSeries series, DayOfWeek entryDayOfWeek, DayOfWeek exitDayOfWeek) {
        super(NamedStrategy.buildLabel(DayOfWeekStrategy.class, entryDayOfWeek.name(), exitDayOfWeek.name()),
                new DayOfWeekRule(new DateTimeIndicator(series), entryDayOfWeek),
                new DayOfWeekRule(new DateTimeIndicator(series), exitDayOfWeek));
    }

    /**
     * Constructs a new DayOfWeekStrategy from string parameters.
     *
     * <p>
     * The parameters should be two strings representing the entry and exit days of
     * the week. Valid values are: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
     * SATURDAY, SUNDAY (case-sensitive).
     *
     * @param series the bar series to analyze
     * @param params array containing [entryDayOfWeek, exitDayOfWeek] as strings
     * @throws IllegalArgumentException if params is null, has fewer than 2
     *                                  elements, or contains invalid day names
     */
    public DayOfWeekStrategy(BarSeries series, String... params) {
        this(series, parseEntryDayOfWeek(params), parseExitDayOfWeek(params));
    }

    /**
     * Builds all possible strategy permutations for all combinations of entry and
     * exit days.
     *
     * <p>
     * This method generates strategies for all pairs of different days of the week
     * (7 * 6 = 42 total strategies). Strategies where the entry day equals the exit
     * day are excluded. If any strategy construction fails, a warning is logged and
     * that strategy is skipped.
     *
     * @param series the bar series to analyze
     * @return a list of all valid DayOfWeekStrategy permutations
     */
    public static List<Strategy> buildAllStrategyPermutations(BarSeries series) {
        List<String[]> permutations = new ArrayList<>();

        for (DayOfWeek entryDay : DayOfWeek.values()) {
            for (DayOfWeek exitDay : DayOfWeek.values()) {
                if (entryDay != exitDay) {
                    permutations.add(new String[] { entryDay.name(), exitDay.name() });
                }
            }
        }

        return NamedStrategy.buildAllStrategyPermutations(series, permutations, DayOfWeekStrategy::new,
                (params, error) -> {
                    String entry = params.length > 0 ? params[0] : "<missing>";
                    String exit = params.length > 1 ? params[1] : "<missing>";
                    LogManager.getLogger()
                            .warn("Failed to build strategy for entry day {} and exit day {} - {}", entry, exit,
                                    error.getMessage());
                });
    }

    private static DayOfWeek parseEntryDayOfWeek(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 1) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryDayOfWeek, exitDayOfWeek), but got " + params.length);
        }
        try {
            return DayOfWeek.valueOf(params[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid entry DayOfWeek value: '" + params[0]
                    + "'. Valid values are: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY", e);
        }
    }

    private static DayOfWeek parseExitDayOfWeek(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params cannot be null");
        }
        if (params.length < 2) {
            throw new IllegalArgumentException(
                    "At least 2 parameters required (entryDayOfWeek, exitDayOfWeek), but got " + params.length);
        }
        try {
            return DayOfWeek.valueOf(params[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid exit DayOfWeek value: '" + params[1]
                    + "'. Valid values are: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY", e);
        }
    }
}
