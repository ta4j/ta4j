/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;
import java.util.function.IntPredicate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;

/**
 * Factory methods that adapt ta4j signal sources to {@link EventSignal}.
 *
 * <p>
 * The {@link Indicator} adapter treats only {@link Boolean#TRUE} as an event;
 * {@code null} and {@code false} values are non-events.
 *
 * @since 0.24.1
 */
public final class EventSignals {

    private EventSignals() {
    }

    /**
     * Adapts a Boolean indicator as an event signal.
     *
     * <p>
     * The series and unstable-bar boundary are taken from the indicator itself.
     *
     * @param indicator the indicator to adapt
     * @return an event signal over the indicator's bar series
     */
    public static EventSignal fromIndicator(Indicator<Boolean> indicator) {
        Objects.requireNonNull(indicator, "indicator");
        return new IndicatorEventSignal(indicator);
    }

    /**
     * Adapts a stateless rule as an event signal.
     *
     * <p>
     * The rule is evaluated without any {@link org.ta4j.core.TradingRecord}; this
     * is only correct for stateless rules whose result does not depend on trading
     * history. Callers needing explicit evaluation context should use
     * {@link #fromPredicate(BarSeries, int, IntPredicate)} instead.
     *
     * @param series       the series the rule is evaluated over
     * @param rule         the stateless rule to adapt
     * @param unstableBars the count of leading bars whose rule results are not
     *                     trustworthy; must be {@code >= 0}
     * @return an event signal over {@code series}
     */
    public static EventSignal fromRule(BarSeries series, Rule rule, int unstableBars) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(rule, "rule");
        return fromPredicate(series, unstableBars, rule::isSatisfied);
    }

    /**
     * Adapts an explicit predicate as an event signal.
     *
     * <p>
     * This is the escape hatch for callers that need explicit evaluation context;
     * the predicate receives the bar index and must be deterministic.
     *
     * @param series       the series the predicate is evaluated over
     * @param unstableBars the count of leading bars whose predicate results are not
     *                     trustworthy; must be {@code >= 0}
     * @param predicate    the event predicate
     * @return an event signal over {@code series}
     */
    public static EventSignal fromPredicate(BarSeries series, int unstableBars, IntPredicate predicate) {
        Objects.requireNonNull(series, "series");
        if (unstableBars < 0) {
            throw new IllegalArgumentException("unstableBars must be >= 0");
        }
        Objects.requireNonNull(predicate, "predicate");
        return new PredicateEventSignal(series, unstableBars, predicate);
    }

    private static final class IndicatorEventSignal implements EventSignal {

        private final Indicator<Boolean> indicator;

        private IndicatorEventSignal(Indicator<Boolean> indicator) {
            this.indicator = indicator;
        }

        @Override
        public BarSeries getBarSeries() {
            return indicator.getBarSeries();
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }

        @Override
        public boolean isEvent(int index) {
            return Boolean.TRUE.equals(indicator.getValue(index));
        }
    }

    private static final class PredicateEventSignal implements EventSignal {

        private final BarSeries series;
        private final int unstableBars;
        private final IntPredicate predicate;

        private PredicateEventSignal(BarSeries series, int unstableBars, IntPredicate predicate) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.predicate = predicate;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public boolean isEvent(int index) {
            return predicate.test(index);
        }
    }
}
