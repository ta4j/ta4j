/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Decides on which aligned portfolio bars {@link PortfolioSeriesManager} trades
 * toward the target allocation.
 *
 * <p>
 * <b>The policy governs every trade, including the initial investment.</b> The
 * portfolio starts entirely in cash and stays in cash until the first bar the
 * policy selects. {@link #atStart()} (the manager's default) therefore means
 * buy-and-hold, {@code onIndexes(3)} deliberately delays the initial investment
 * to aligned index 3, and {@code atStart().or(onIndexes(3))} invests at the
 * first bar and rebalances again at index 3. {@link #everyNthBar(int)} and
 * {@link #firstBarOf(TemporalUnit, ZoneId)} always select the first bar.
 * </p>
 *
 * <p>
 * Policies are stateless and reusable across portfolios and runs; they receive
 * the aligned series so calendar-aware rules can inspect
 * {@link PortfolioSeries#getEndTimes()}.
 * </p>
 *
 * @since 0.25.1
 */
@FunctionalInterface
public interface RebalancePolicy {

    /**
     * Returns {@code true} when the portfolio should trade toward its target
     * allocation at {@code index}.
     *
     * @param series aligned portfolio series being run
     * @param index  aligned portfolio index
     * @return true to rebalance at {@code index}
     * @since 0.25.1
     */
    boolean shouldRebalance(PortfolioSeries series, int index);

    /**
     * Combines this policy with another one; the result rebalances whenever either
     * policy does.
     *
     * @param other other policy
     * @return union of both schedules
     * @since 0.25.1
     */
    default RebalancePolicy or(RebalancePolicy other) {
        Objects.requireNonNull(other, "other");
        return (series, index) -> shouldRebalance(series, index) || other.shouldRebalance(series, index);
    }

    /**
     * Invests once at the first aligned bar and then holds (buy-and-hold).
     *
     * @return first-bar policy
     * @since 0.25.1
     */
    static RebalancePolicy atStart() {
        return (series, index) -> index == 0;
    }

    /**
     * Rebalances on every aligned bar.
     *
     * @return every-bar policy
     * @since 0.25.1
     */
    static RebalancePolicy everyBar() {
        return (series, index) -> true;
    }

    /**
     * Rebalances on aligned indexes {@code 0, n, 2n, ...}. This counts aligned
     * bars, not calendar time; use {@link #firstBarOf(TemporalUnit, ZoneId)} for
     * calendar schedules.
     *
     * @param n positive bar interval
     * @return every-nth-bar policy
     * @since 0.25.1
     */
    static RebalancePolicy everyNthBar(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }
        return (series, index) -> index % n == 0;
    }

    /**
     * Rebalances only on the given aligned indexes. The portfolio stays in cash
     * until the first of them; combine with {@link #atStart()} to invest
     * immediately.
     *
     * @param indexes non-negative aligned indexes
     * @return explicit-index policy
     * @since 0.25.1
     */
    static RebalancePolicy onIndexes(int... indexes) {
        Objects.requireNonNull(indexes, "indexes");
        return onIndexes(Arrays.stream(indexes).boxed().toList());
    }

    /**
     * Rebalances only on the given aligned indexes. The portfolio stays in cash
     * until the first of them; combine with {@link #atStart()} to invest
     * immediately.
     *
     * @param indexes non-negative aligned indexes
     * @return explicit-index policy
     * @since 0.25.1
     */
    static RebalancePolicy onIndexes(Set<Integer> indexes) {
        Objects.requireNonNull(indexes, "indexes");
        return onIndexes(List.copyOf(indexes));
    }

    private static RebalancePolicy onIndexes(List<Integer> indexes) {
        for (Integer index : indexes) {
            if (Objects.requireNonNull(index, "indexes must not contain null") < 0) {
                throw new IllegalArgumentException("indexes must be >= 0 but was " + index);
            }
        }
        Set<Integer> rebalanceIndexes = Set.copyOf(indexes);
        return (series, index) -> rebalanceIndexes.contains(index);
    }

    /**
     * Rebalances on the first aligned bar of each calendar period, for example
     * {@code firstBarOf(ChronoUnit.MONTHS, ZoneOffset.UTC)} for monthly
     * rebalancing. The first aligned bar always rebalances.
     *
     * <p>
     * A bar belongs to the period containing the instant just before its end time,
     * so a daily bar ending exactly at midnight counts toward the day it covers.
     * Supported units are {@link ChronoUnit#DAYS}, {@link ChronoUnit#WEEKS} (ISO
     * weeks starting Monday), {@link ChronoUnit#MONTHS},
     * {@link IsoFields#QUARTER_YEARS}, and {@link ChronoUnit#YEARS}.
     * </p>
     *
     * @param unit calendar period
     * @param zone time zone used to derive calendar dates from bar end times
     * @return calendar policy
     * @since 0.25.1
     */
    static RebalancePolicy firstBarOf(TemporalUnit unit, ZoneId zone) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(zone, "zone");
        ToLongFunction<LocalDate> periodKey;
        if (unit == ChronoUnit.DAYS) {
            periodKey = LocalDate::toEpochDay;
        } else if (unit == ChronoUnit.WEEKS) {
            periodKey = date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
        } else if (unit == ChronoUnit.MONTHS) {
            periodKey = date -> date.getYear() * 12L + date.getMonthValue();
        } else if (unit == IsoFields.QUARTER_YEARS) {
            periodKey = date -> date.getYear() * 4L + date.get(IsoFields.QUARTER_OF_YEAR);
        } else if (unit == ChronoUnit.YEARS) {
            periodKey = LocalDate::getYear;
        } else {
            throw new IllegalArgumentException("unsupported calendar unit: " + unit);
        }
        return (series, index) -> {
            if (index == 0) {
                return true;
            }
            List<Instant> endTimes = series.getEndTimes();
            LocalDate current = endTimes.get(index).minusNanos(1).atZone(zone).toLocalDate();
            LocalDate previous = endTimes.get(index - 1).minusNanos(1).atZone(zone).toLocalDate();
            return periodKey.applyAsLong(current) != periodKey.applyAsLong(previous);
        };
    }
}
