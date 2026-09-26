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

import org.ta4j.core.num.Num;

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
 * {@link #firstBarOf(TemporalUnit, ZoneId)} always select the first bar, and
 * {@link #whenDriftExceeds(double)} selects it whenever the band is smaller
 * than the initial all-cash drift.
 * </p>
 *
 * <p>
 * Schedules combine with {@link #or(RebalancePolicy)} and
 * {@link #and(RebalancePolicy)}; for example
 * {@code firstBarOf(MONTHS, UTC).and(whenDriftExceeds(0.05))} checks monthly
 * but trades only when some weight has drifted more than five percentage
 * points. Policies are stateless and reusable across portfolios and runs.
 * </p>
 *
 * @since 0.25.1
 */
@FunctionalInterface
public interface RebalancePolicy {

    /**
     * Returns {@code true} when the portfolio should trade toward its target
     * allocation at the context's bar.
     *
     * @param context pre-trade portfolio state at the bar's close
     * @return true to rebalance at this bar
     * @since 0.25.1
     */
    boolean shouldRebalance(Context context);

    /**
     * Returns a policy that rebalances whenever this policy or {@code other} does.
     *
     * @param other other policy
     * @return union of both policies
     * @since 0.25.1
     */
    default RebalancePolicy or(RebalancePolicy other) {
        Objects.requireNonNull(other, "other");
        return context -> shouldRebalance(context) || other.shouldRebalance(context);
    }

    /**
     * Returns a policy that rebalances only when both this policy and {@code other}
     * do, for example a calendar check gated by a drift band.
     *
     * @param other other policy
     * @return intersection of both policies
     * @since 0.25.1
     */
    default RebalancePolicy and(RebalancePolicy other) {
        Objects.requireNonNull(other, "other");
        return context -> shouldRebalance(context) && other.shouldRebalance(context);
    }

    /**
     * Invests once at the first aligned bar and then holds (buy-and-hold).
     *
     * @return first-bar policy
     * @since 0.25.1
     */
    static RebalancePolicy atStart() {
        return context -> context.getIndex() == 0;
    }

    /**
     * Rebalances on every aligned bar.
     *
     * @return every-bar policy
     * @since 0.25.1
     */
    static RebalancePolicy everyBar() {
        return context -> true;
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
        return context -> context.getIndex() % n == 0;
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
        return context -> rebalanceIndexes.contains(context.getIndex());
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
        return context -> {
            int index = context.getIndex();
            if (index == 0) {
                return true;
            }
            List<Instant> endTimes = context.getSeries().getEndTimes();
            LocalDate current = endTimes.get(index).minusNanos(1).atZone(zone).toLocalDate();
            LocalDate previous = endTimes.get(index - 1).minusNanos(1).atZone(zone).toLocalDate();
            return periodKey.applyAsLong(current) != periodKey.applyAsLong(previous);
        };
    }

    /**
     * Threshold (drift-band) rebalancing: trades back to the targets only when some
     * asset weight, or the cash weight, differs from its target by more than
     * {@code band} (in weight units; {@code 0.05} means five percentage points).
     * Small drifts are left alone, which avoids paying transaction costs for
     * negligible corrections.
     *
     * <p>
     * The all-cash starting portfolio drifts from every target, so the initial
     * investment happens at the first bar unless {@code band} is at least the
     * largest target (or invested) weight. Evaluate it on every bar, or gate it
     * with a schedule via {@link #and(RebalancePolicy)}.
     * </p>
     *
     * @param band drift threshold in {@code (0, 1)}
     * @return drift-band policy
     * @since 0.25.1
     */
    static RebalancePolicy whenDriftExceeds(double band) {
        if (!(band > 0 && band < 1)) {
            throw new IllegalArgumentException("band must be in (0, 1) but was " + band);
        }
        return context -> context.getMaxDrift().isGreaterThan(context.getSeries().numFactory().numOf(band));
    }

    /**
     * Pre-trade portfolio state at one aligned bar's close, handed to
     * {@link RebalancePolicy#shouldRebalance(Context)}. Weights are fractions of
     * the pre-trade portfolio value and are computed on demand.
     *
     * @since 0.25.1
     */
    final class Context {

        private final PortfolioSeries series;
        private final int index;
        private final Num[] prices;
        private final Num[] units;
        private final Num cash;
        private final Num portfolioValue;
        private final Num[] targetWeights;
        private Num maxDrift;

        Context(PortfolioSeries series, int index, Num[] prices, Num[] units, Num cash, Num portfolioValue,
                Num[] targetWeights) {
            this.series = series;
            this.index = index;
            this.prices = prices;
            this.units = units;
            this.cash = cash;
            this.portfolioValue = portfolioValue;
            this.targetWeights = targetWeights;
        }

        /**
         * @return aligned portfolio series being run
         * @since 0.25.1
         */
        public PortfolioSeries getSeries() {
            return series;
        }

        /**
         * @return aligned portfolio index
         * @since 0.25.1
         */
        public int getIndex() {
            return index;
        }

        /**
         * @return aligned bar end time
         * @since 0.25.1
         */
        public Instant getEndTime() {
            return series.getEndTimes().get(index);
        }

        /**
         * @return pre-trade portfolio value at the bar's close
         * @since 0.25.1
         */
        public Num getPortfolioValue() {
            return portfolioValue;
        }

        /**
         * @param asset asset name
         * @return pre-trade weight of {@code asset}
         * @since 0.25.1
         */
        public Num getAssetWeight(String asset) {
            int position = position(asset);
            return weight(prices[position].multipliedBy(units[position]));
        }

        /**
         * @param asset asset name
         * @return target weight of {@code asset}
         * @since 0.25.1
         */
        public Num getTargetWeight(String asset) {
            return targetWeights[position(asset)];
        }

        /**
         * @return pre-trade cash weight
         * @since 0.25.1
         */
        public Num getCashWeight() {
            return weight(cash);
        }

        /**
         * @return largest absolute difference between an achieved and a target weight,
         *         cash included
         * @since 0.25.1
         */
        public Num getMaxDrift() {
            if (maxDrift == null) {
                Num one = series.numFactory().one();
                Num targetCash = one;
                Num drift = series.numFactory().zero();
                for (int position = 0; position < units.length; position++) {
                    Num weight = weight(prices[position].multipliedBy(units[position]));
                    drift = drift.max(weight.minus(targetWeights[position]).abs());
                    targetCash = targetCash.minus(targetWeights[position]);
                }
                maxDrift = drift.max(weight(cash).minus(targetCash).abs());
            }
            return maxDrift;
        }

        private Num weight(Num value) {
            return portfolioValue.isZero() ? series.numFactory().zero() : value.dividedBy(portfolioValue);
        }

        private int position(String asset) {
            Objects.requireNonNull(asset, "asset");
            int position = series.getAssets().indexOf(asset);
            if (position < 0) {
                throw new IllegalArgumentException("asset is not in this portfolio: " + asset);
            }
            return position;
        }
    }
}
